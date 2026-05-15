package nestor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class PPUTest : FreeSpec({
    "tick" - {
        "should set VBLANK at dot 1 of scanline 241" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)

            ppu.scanline = 240
            ppu.cycle = 340

            // First tick: cycle wraps to 0 (dot 0), scanline advances to 241 — flag not set yet.
            ppu.tick(1)
            ppu.scanline shouldBe 241
            (ppu.status and STATUS_VBLANK) shouldBe 0          // not yet at dot 1

            // Second tick: dot 1 — VBlank flag set.
            ppu.tick(1)
            (ppu.status and STATUS_VBLANK) shouldBe STATUS_VBLANK
        }

        "should clear VBLANK at dot 1 of the pre-render line (scanline 261) and reset writeToggle" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)

            ppu.status = ppu.status or STATUS_VBLANK
            ppu.writeToggle = true

            ppu.scanline = 260
            ppu.cycle = 340

            // First tick: cycle wraps to 0 (dot 0), scanline 261 — not cleared yet.
            ppu.tick(1)
            ppu.scanline shouldBe 261
            (ppu.status and STATUS_VBLANK) shouldBe STATUS_VBLANK  // still set at dot 0

            // Second tick: dot 1 — VBlank cleared, latch reset.
            ppu.tick(1)
            (ppu.status and STATUS_VBLANK) shouldBe 0
            ppu.writeToggle shouldBe false
        }
    }

    "nametable mirroring" - {
        "vertical mirroring maps \$2000 and \$2800 to the same bank" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.vramAddr = 0x2000
            ppu.cpuWrite(0x2007, 0xAB)
            ppu.vramAddr = 0x2800
            ppu.cpuWrite(0x2007, 0xCD)

            // Both writes hit the same physical 0x000 bank — second write wins
            ppu.nametableRam[0x000] shouldBe 0xCD.toByte()
        }

        "vertical mirroring maps \$2400 and \$2C00 to the same bank" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.vramAddr = 0x2400
            ppu.cpuWrite(0x2007, 0x11)
            ppu.vramAddr = 0x2C00
            ppu.cpuWrite(0x2007, 0x22)

            // Both hit physical 0x400 bank — second write wins
            ppu.nametableRam[0x400] shouldBe 0x22.toByte()
        }

        "horizontal mirroring maps \$2000 and \$2400 to the same bank" {
            val ppu = PPU(ByteArray(0), MirroringMode.HORIZONTAL)
            ppu.vramAddr = 0x2000
            ppu.cpuWrite(0x2007, 0xAB)
            ppu.vramAddr = 0x2400
            ppu.cpuWrite(0x2007, 0xCD)

            // Both hit physical 0x000 bank — second write wins
            ppu.nametableRam[0x000] shouldBe 0xCD.toByte()
        }

        "horizontal mirroring maps \$2800 and \$2C00 to the same bank" {
            val ppu = PPU(ByteArray(0), MirroringMode.HORIZONTAL)
            ppu.vramAddr = 0x2800
            ppu.cpuWrite(0x2007, 0x33)
            ppu.vramAddr = 0x2C00
            ppu.cpuWrite(0x2007, 0x44)

            // Both hit physical 0x400 bank — second write wins
            ppu.nametableRam[0x400] shouldBe 0x44.toByte()
        }

        "horizontal mirroring keeps \$2000 and \$2800 in separate banks" {
            val ppu = PPU(ByteArray(0), MirroringMode.HORIZONTAL)
            ppu.vramAddr = 0x2000
            ppu.cpuWrite(0x2007, 0xAA)
            ppu.vramAddr = 0x2800
            ppu.cpuWrite(0x2007, 0xBB)

            ppu.nametableRam[0x000] shouldBe 0xAA.toByte()
            ppu.nametableRam[0x400] shouldBe 0xBB.toByte()
        }
    }

    "background pattern table selection via PPUCTRL bit 4" - {
        val bankedChrRom = ChrRomBuilder(banks = 2)
            .tile(0, 0, makeCheckerboardTileData())
            .tile(1, 0, makeStripeTileData())
            .build()

        fun writePpuAddr(bus: MemoryBus, addr: Int) {
            bus.write(0x2006, (addr shr 8) and 0xFF)
            bus.write(0x2006, addr and 0xFF)
        }

        fun setupNametableAndPalette(bus: MemoryBus) {
            writePpuAddr(bus, 0x2000)
            bus.write(0x2007, 0x00) // tile index 0 at position (0,0)
            writePpuAddr(bus, 0x3F00)
            bus.write(0x2007, 0x0F) // universal bg
            bus.write(0x2007, 0x01) // palette 0 color 1
            bus.write(0x2007, 0x21) // palette 0 color 2
            bus.write(0x2007, 0x31) // palette 0 color 3
        }

        "PPUCTRL bit 4 = 0 uses bank 0 (tiles 0–255)" {
            val ppu = PPU(bankedChrRom, MirroringMode.VERTICAL)
            val bus = MemoryBus(ppu, ByteArray(0x4000))
            ppu.control = 0x00 // bit 4 clear → bank 0
            ppu.mask = MASK_BG_ENABLE
            setupNametableAndPalette(bus)

            ppu.tick(240 * 341)
            val frame = ppu.currentFrame()

            // Bank 0 tile 0 = checkerboard: pixel (0,0) has color index 1
            frame[0] shouldBe nesPalette[0x01]
        }

        "PPUCTRL bit 4 = 1 uses bank 1 (tiles 256–511)" {
            val ppu = PPU(bankedChrRom, MirroringMode.VERTICAL)
            val bus = MemoryBus(ppu, ByteArray(0x4000))
            ppu.control = 0x10 // bit 4 set → bank 1
            ppu.mask = MASK_BG_ENABLE
            setupNametableAndPalette(bus)

            ppu.tick(240 * 341)
            val frame = ppu.currentFrame()

            // Bank 1 tile 0 = striped: pixel (0,0) has color index 3
            frame[0] shouldBe nesPalette[0x31]
        }
    }

    "cpuRead" - {
        "cpuRead of write-only registers should return 0" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            listOf(0x2000, 0x2001, 0x2003, 0x2005, 0x2006).forEach { addr ->
                ppu.cpuRead(addr) shouldBe 0
            }
        }

        "cpuRead $2002 should return status and clear VBlank and writeToggle" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.status = 0b11100000  // VBlank + sprite flags set
            val result = ppu.cpuRead(0x2002)

            result shouldBe 0b11100000
            ppu.status and 0x80 shouldBe 0  // VBlank bit cleared
            ppu.writeToggle shouldBe false
        }

        "cpuRead $2002 should mask off lower 5 bits (open bus)" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.status = 0xFF  // all bits set, including lower 5 open-bus bits
            val result = ppu.cpuRead(0x2002)

            result shouldBe 0xE0  // only upper 3 bits returned
        }

        "cpuRead $2004 should return value at OAMADDR" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.oamRam[5] = 0x42
            ppu.oamAddr = 5

            val result = ppu.cpuRead(0x2004)
            result shouldBe 0x42
        }

        "cpuRead $2007 should return buffered CHR-ROM byte on pattern table access" {
            val chrRom = ByteArray(0x2000)
            chrRom[0x0000] = 0xAB.toByte()
            chrRom[0x0001] = 0xCD.toByte()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.vramAddr = 0x0000

            // First read: returns old buffer, loads chrRom[0x0000] into buffer
            ppu.ppuDataBuffer = 0x11
            val first = ppu.cpuRead(0x2007)
            val second = ppu.cpuRead(0x2007)

            first shouldBe 0x11             // old buffer
            second shouldBe 0xAB            // was just buffered from chrRom[0x0000]
            ppu.ppuDataBuffer.toUByte().toInt() shouldBe 0xCD
        }

        "cpuRead $2007 should return buffered value on nametable access" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.nametableRam[0x0000] = 0x12
            ppu.nametableRam[0x0001] = 0x34
            ppu.vramAddr = 0x2000

            // First read returns old buffer, updates buffer
            ppu.ppuDataBuffer = 0xAB.toByte()
            val first = ppu.cpuRead(0x2007)
            val second = ppu.cpuRead(0x2007)

            first shouldBe 0xAB            // old buffer
            second shouldBe 0x12           // was just buffered
            ppu.ppuDataBuffer.toUByte().toInt() shouldBe 0x34
        }

        "cpuRead $2007 should return actual palette value immediately" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.vramAddr = 0x3F00
            ppu.paletteRam[0x00] = 0x3C

            val result = ppu.cpuRead(0x2007)
            result shouldBe 0x3C
        }

        "cpuRead $2007 from palette should fill ppuDataBuffer with nametable data at mirrored address" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            // $3F00 mirrors the nametable at $2F00; $2F00 maps to nametableRam[0x700]
            ppu.nametableRam[0x700] = 0x55
            ppu.vramAddr = 0x3F00
            ppu.paletteRam[0x00] = 0x3C

            ppu.cpuRead(0x2007)

            ppu.ppuDataBuffer.toUByte().toInt() shouldBe 0x55
        }

        "cpuRead $2007 should increment and wrap vramAddr" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.vramAddr = 0x3FFF
            ppu.paletteRam[0x1F] = 0x21

            ppu.cpuRead(0x2007)
            ppu.vramAddr shouldBe 0x0000
        }
    }

    "cpuWrite" - {
        "cpuWrite $2000 should set control and update bits 10–11 of tempAddr" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.cpuWrite(0x2000, 0b00000011)

            ppu.control shouldBe 0b00000011
            (ppu.tempAddr shr 10) and 0b11 shouldBe 0b11
        }

        "cpuWrite $2001 should set mask register" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.cpuWrite(0x2001, 0x3F)

            ppu.mask shouldBe 0x3F
        }

        "cpuWrite $2003 should set oamAddr" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.cpuWrite(0x2003, 0x42)

            ppu.oamAddr shouldBe 0x42
        }

        "cpuWrite $2004 should write to OAM at oamAddr and increment it" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.oamAddr = 0x10
            ppu.cpuWrite(0x2004, 0xAB)

            ppu.oamRam[0x10].toUByte().toInt() shouldBe 0xAB
            ppu.oamAddr shouldBe 0x11
        }

        "cpuWrite $2005 should write fineX and coarseX on first write" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.writeToggle = false
            ppu.cpuWrite(0x2005, 0b10100101)

            ppu.fineXScroll shouldBe 0b00000101
            ppu.tempAddr and 0x001F shouldBe 0b10100
            ppu.writeToggle shouldBe true
            ppu.scrollX shouldBe 0b10100101
        }

        "cpuWrite $2005 should write coarseY and fineY on second write" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.writeToggle = true
            ppu.cpuWrite(0x2005, 0b01110110)

            (ppu.tempAddr shr 12) and 0b111 shouldBe 0b110  // fine Y
            (ppu.tempAddr shr 5) and 0b11111 shouldBe 0b01110  // coarse Y
            ppu.writeToggle shouldBe false
            ppu.scrollY shouldBe 0b01110110
        }

        "cpuWrite $2006 should write high byte of tempAddr on first write" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.writeToggle = false
            ppu.cpuWrite(0x2006, 0x3F)

            (ppu.tempAddr shr 8) and 0x3F shouldBe 0x3F
            ppu.writeToggle shouldBe true
        }

        "cpuWrite $2006 should write low byte and set vramAddr on second write" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.writeToggle = true
            ppu.tempAddr = 0x3F00
            ppu.cpuWrite(0x2006, 0x10)

            ppu.tempAddr shouldBe 0x3F10
            ppu.vramAddr shouldBe 0x3F10
            ppu.writeToggle shouldBe false
        }

        "cpuWrite $2007 should write to nametableRam and increment vramAddr" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.vramAddr = 0x2000
            ppu.cpuWrite(0x2007, 0x99)

            ppu.nametableRam[0x0000] shouldBe 0x99.toByte()
            ppu.vramAddr shouldBe 0x2001
        }

        "cpuWrite $2007 should write to paletteRam with mirrored address" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.vramAddr = 0x3F10
            ppu.cpuWrite(0x2007, 0x0F)

            ppu.paletteRam[0x00] shouldBe 0x0F.toByte()
        }

        "cpuWrite $2007 should increment vramAddr by 32 when PPUCTRL bit 2 is set" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.control = 0x04  // bit 2 set → vertical increment (+32)
            ppu.vramAddr = 0x2000
            ppu.cpuWrite(0x2007, 0x99)

            ppu.vramAddr shouldBe 0x2020
        }

        "cpuRead $2007 should increment vramAddr by 32 when PPUCTRL bit 2 is set" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.control = 0x04  // bit 2 set → vertical increment (+32)
            ppu.vramAddr = 0x2000

            ppu.cpuRead(0x2007)

            ppu.vramAddr shouldBe 0x2020
        }
    }

    "calculatePixel" - {
        "returns background color when sprites are disabled" {
            val chrRom = ChrRomBuilder().tile(0, 0, makeCheckerboardTileData()).build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE // sprites disabled

            // Nametable: tile 0 at position (0,0)
            ppu.nametableRam[0] = 0x00

            // Background palette 0: universal bg = 0x0F, color 1 = 0x01
            ppu.paletteRam[0] = 0x0F
            ppu.paletteRam[1] = 0x01

            // Checkerboard tile: pixel (0,0) has color index 1
            ppu.calculatePixel(0, 0) shouldBe nesPalette[0x01]
            // Pixel (1,0) has color index 0 (transparent = universal bg)
            ppu.calculatePixel(1, 0) shouldBe nesPalette[0x0F]
        }

        "sprite in front of background shows sprite color, transparent center shows background" {
            val chrRom = ChrRomBuilder()
                .tile(0, 0, makeCheckerboardTileData()) // BG tile
                .tile(0, 1, makeFrameSpriteTileData())  // sprite tile: border opaque, center transparent
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE or MASK_SPRITE_ENABLE

            // BG: tile 0 at position (0,0), palette 0
            ppu.nametableRam[0] = 0x00
            ppu.paletteRam[0] = 0x0F // universal bg
            ppu.paletteRam[1] = 0x01 // bg palette 0 color 1

            // Sprite palette 0: paletteRam[0x11–0x13]
            ppu.paletteRam[0x11] = 0x16

            // Sprite 0 at (0, 0): Y=0 visible on scanline 1, tile 1, in front
            ppu.oamRam[0] = (0x00).toByte() // Y
            ppu.oamRam[1] = (0x01).toByte() // tile id
            ppu.oamRam[2] = (0x00).toByte() // attributes: palette 0, in front, no flip
            ppu.oamRam[3] = (0x00).toByte() // X

            // (0, 1) is on the opaque border → sprite color wins
            ppu.calculatePixel(0, 1) shouldBe nesPalette[0x16]

            // (3, 3) is in the transparent center → background shows through
            // Checkerboard at (3, 3): (3+3) % 2 == 0 → color index 1
            ppu.calculatePixel(3, 3) shouldBe nesPalette[0x01]
        }

        "sprite behind background shows background when BG is opaque, sprite when BG is transparent" {
            val chrRom = ChrRomBuilder()
                .tile(0, 0, makeCheckerboardTileData()) // BG tile: alternates index 1 and 0
                .tile(0, 1, makeFrameSpriteTileData())  // sprite tile
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE or MASK_SPRITE_ENABLE

            ppu.nametableRam[0] = 0x00
            ppu.paletteRam[0] = 0x0F // universal bg
            ppu.paletteRam[1] = 0x01 // bg palette 0 color 1

            ppu.paletteRam[0x11] = 0x16 // sprite palette 0 color 1

            // Sprite 0: behind background (attribute bit 5 set)
            ppu.oamRam[0] = (0x00).toByte() // Y
            ppu.oamRam[1] = (0x01).toByte() // tile id
            ppu.oamRam[2] = (0x20).toByte() // attributes: behind BG
            ppu.oamRam[3] = (0x00).toByte() // X

            // (1, 1) border pixel: BG checkerboard at row 1 col 1 → index 1 (opaque)
            // → BG wins because sprite is behind and BG is opaque
            ppu.calculatePixel(1, 1) shouldBe nesPalette[0x01]

            // (0, 1) border pixel: BG checkerboard at row 1 col 0 → index 0 (transparent)
            // → sprite shows through because BG is transparent
            ppu.calculatePixel(0, 1) shouldBe nesPalette[0x16]
        }

        "horizontal flip mirrors sprite pixels" {
            val chrRom = ChrRomBuilder()
                .tile(0, 0, makeBlankTileData())      // blank BG
                .tile(0, 1, makeCornerDotTileData())   // dot at top-left only
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE or MASK_SPRITE_ENABLE
            ppu.paletteRam[0] = 0x0F
            ppu.paletteRam[0x11] = 0x16

            // Sprite 0 at (0,0), horizontal flip
            ppu.oamRam[0] = (0x00).toByte()
            ppu.oamRam[1] = (0x01).toByte()
            ppu.oamRam[2] = (0x40).toByte() // H-flip
            ppu.oamRam[3] = (0x00).toByte()

            // Without flip the dot is at (0, 1). With H-flip it moves to (7, 1).
            ppu.calculatePixel(0, 1) shouldBe nesPalette[0x0F] // was opaque, now transparent
            ppu.calculatePixel(7, 1) shouldBe nesPalette[0x16] // was transparent, now opaque
        }

        "vertical flip mirrors sprite pixels" {
            val chrRom = ChrRomBuilder()
                .tile(0, 0, makeBlankTileData())
                .tile(0, 1, makeCornerDotTileData())
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE or MASK_SPRITE_ENABLE
            ppu.paletteRam[0] = 0x0F
            ppu.paletteRam[0x11] = 0x16

            // Sprite 0 at (0,0), vertical flip
            ppu.oamRam[0] = (0x00).toByte()
            ppu.oamRam[1] = (0x01).toByte()
            ppu.oamRam[2] = (0x80).toByte() // V-flip
            ppu.oamRam[3] = (0x00).toByte()

            // Without flip the dot is at (0, 1) (row 0 of sprite = scanline 1).
            // With V-flip it moves to (0, 8) (row 7 of sprite = scanline 8).
            ppu.calculatePixel(0, 1) shouldBe nesPalette[0x0F] // was opaque, now transparent
            ppu.calculatePixel(0, 8) shouldBe nesPalette[0x16] // was transparent, now opaque
        }

        "lower OAM index sprite wins when two sprites overlap" {
            val chrRom = ChrRomBuilder()
                .tile(0, 0, makeBlankTileData())
                .tile(0, 1, makeFrameSpriteTileData()) // sprite tile for both sprites
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE or MASK_SPRITE_ENABLE
            ppu.paletteRam[0] = 0x0F

            // Sprite palette 0 and 1 with distinct colors
            ppu.paletteRam[0x11] = 0x16 // palette 0 color 1
            ppu.paletteRam[0x15] = 0x26 // palette 1 color 1

            // Sprite 0: palette 0, at (0,0)
            ppu.oamRam[0] = (0x00).toByte()
            ppu.oamRam[1] = (0x01).toByte()
            ppu.oamRam[2] = (0x00).toByte() // palette 0
            ppu.oamRam[3] = (0x00).toByte()

            // Sprite 1: palette 1, also at (0,0)
            ppu.oamRam[4] = (0x00).toByte()
            ppu.oamRam[5] = (0x01).toByte()
            ppu.oamRam[6] = (0x01).toByte() // palette 1
            ppu.oamRam[7] = (0x00).toByte()

            // Both sprites cover (0,1) — sprite 0 (palette 0) wins
            ppu.calculatePixel(0, 1) shouldBe nesPalette[0x16]
        }

        "PPUCTRL bit 3 selects sprite pattern table" {
            val chrRom = ChrRomBuilder(banks = 2)
                .tile(0, 0, makeBlankTileData())        // blank BG in bank 0
                .tile(0, 1, makeCornerDotTileData())    // bank 0 sprite tile: dot at top-left
                .tile(1, 1, makeFrameSpriteTileData())  // bank 1 sprite tile: frame border
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE or MASK_SPRITE_ENABLE
            ppu.paletteRam[0] = 0x0F
            ppu.paletteRam[0x11] = 0x16

            // Sprite 0: tile 1 at (0,0)
            ppu.oamRam[0] = (0x00).toByte()
            ppu.oamRam[1] = (0x01).toByte()
            ppu.oamRam[2] = (0x00).toByte()
            ppu.oamRam[3] = (0x00).toByte()

            // PPUCTRL bit 3 = 0 → bank 0: corner dot tile, only (0,1) is opaque
            ppu.control = 0x00
            ppu.calculatePixel(0, 1) shouldBe nesPalette[0x16] // opaque dot
            ppu.calculatePixel(1, 1) shouldBe nesPalette[0x0F] // transparent

            // PPUCTRL bit 3 = 1 → bank 1: frame tile, (0,1) and (1,1) both opaque border
            ppu.control = CTRL_SPRITE_PATTERN_TABLE
            ppu.calculatePixel(0, 1) shouldBe nesPalette[0x16] // opaque border
            ppu.calculatePixel(1, 1) shouldBe nesPalette[0x16] // opaque border
        }
    }

    "sprite 0 hit" - {
        "is set when opaque sprite 0 pixel overlaps opaque BG pixel" {
            val chrRom = ChrRomBuilder()
                .tile(0, 0, makeCheckerboardTileData()) // BG tile: row 1 has opaque pixels at cols 1,3,5,7
                .tile(0, 1, makeFrameSpriteTileData())  // sprite tile: all border pixels opaque
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE or MASK_SPRITE_ENABLE

            // BG: tile 0 at position (0,0)
            ppu.nametableRam[0] = 0x00
            ppu.paletteRam[0] = 0x0F // universal bg
            ppu.paletteRam[1] = 0x01 // bg palette 0 color 1

            // Sprite palette 0
            ppu.paletteRam[0x11] = 0x16

            // Sprite 0 at (0, 0): OAM Y=0 means visible on scanlines 1–8
            ppu.oamRam[0] = 0x00.toByte() // Y
            ppu.oamRam[1] = 0x01.toByte() // tile id
            ppu.oamRam[2] = 0x00.toByte() // attributes: palette 0, in front, no flip
            ppu.oamRam[3] = 0x00.toByte() // X

            // Before rendering, flag should be clear
            (ppu.status and STATUS_SPRITE0_HIT) shouldBe 0

            // Tick through scanlines 0–1 (2 × 341 dots).
            // On scanline 1 (sprite row 0), pixel x=1 has:
            //   BG checkerboard row 1 col 1 → color index 1 (opaque)
            //   Frame sprite row 0 col 1 → color index 1 (opaque)
            // → sprite 0 hit fires
            ppu.tick(2 * 341)

            (ppu.status and STATUS_SPRITE0_HIT) shouldBe STATUS_SPRITE0_HIT
        }

        "is not set when sprite 0 pixel is transparent" {
            val chrRom = ChrRomBuilder()
                .tile(0, 0, makeCheckerboardTileData()) // BG tile
                .tile(0, 1, makeCornerDotTileData())    // sprite tile: only (0,0) opaque
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE or MASK_SPRITE_ENABLE

            ppu.nametableRam[0] = 0x00
            ppu.paletteRam[0] = 0x0F
            ppu.paletteRam[1] = 0x01
            ppu.paletteRam[0x11] = 0x16

            // Sprite 0 at X=1: the single opaque pixel lands at screen (1,1).
            // BG checkerboard row 1 col 1 → color index 1 (opaque).
            // But flip the sprite horizontally so the dot moves to col 7 → screen (8,1).
            // BG checkerboard row 1 col 8 → that's tile (1,0) nametable index 1 which is 0 → blank tile.
            // Instead, just place sprite at X=1 with no flip, so opaque pixel is at (1,1).
            // That WILL hit. So to test no-hit, place sprite where its only opaque pixel
            // aligns with a transparent BG pixel.
            // Checkerboard row 1: cols 0,2,4,6 are transparent (index 0).
            // Corner dot at X=0: opaque pixel at (0,1). BG row 1 col 0 → index 0 (transparent).
            // → no overlap, flag stays clear.
            ppu.oamRam[0] = 0x00.toByte() // Y=0 → visible scanlines 1–8
            ppu.oamRam[1] = 0x01.toByte() // tile id
            ppu.oamRam[2] = 0x00.toByte()
            ppu.oamRam[3] = 0x00.toByte() // X=0

            ppu.tick(9 * 341) // tick through all scanlines the sprite covers

            (ppu.status and STATUS_SPRITE0_HIT) shouldBe 0
        }

        "is not set when BG pixel is transparent" {
            val chrRom = ChrRomBuilder()
                .tile(0, 0, makeBlankTileData())       // BG tile: all transparent
                .tile(0, 1, makeFrameSpriteTileData())  // sprite tile: border opaque
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE or MASK_SPRITE_ENABLE

            ppu.nametableRam[0] = 0x00
            ppu.paletteRam[0] = 0x0F
            ppu.paletteRam[0x11] = 0x16

            ppu.oamRam[0] = 0x00.toByte()
            ppu.oamRam[1] = 0x01.toByte()
            ppu.oamRam[2] = 0x00.toByte()
            ppu.oamRam[3] = 0x00.toByte()

            ppu.tick(9 * 341)

            (ppu.status and STATUS_SPRITE0_HIT) shouldBe 0
        }

        "is not set when sprite rendering is disabled" {
            val chrRom = ChrRomBuilder()
                .tile(0, 0, makeCheckerboardTileData())
                .tile(0, 1, makeFrameSpriteTileData())
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE // sprites disabled

            ppu.nametableRam[0] = 0x00
            ppu.paletteRam[0] = 0x0F
            ppu.paletteRam[1] = 0x01
            ppu.paletteRam[0x11] = 0x16

            ppu.oamRam[0] = 0x00.toByte()
            ppu.oamRam[1] = 0x01.toByte()
            ppu.oamRam[2] = 0x00.toByte()
            ppu.oamRam[3] = 0x00.toByte()

            ppu.tick(9 * 341)

            (ppu.status and STATUS_SPRITE0_HIT) shouldBe 0
        }

        "is cleared on the pre-render scanline" {
            val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
            ppu.status = ppu.status or STATUS_SPRITE0_HIT

            ppu.scanline = 260
            ppu.cycle = 340

            // Tick to dot 0 of scanline 261 — not cleared yet
            ppu.tick(1)
            (ppu.status and STATUS_SPRITE0_HIT) shouldBe STATUS_SPRITE0_HIT

            // Tick to dot 1 of scanline 261 — cleared
            ppu.tick(1)
            (ppu.status and STATUS_SPRITE0_HIT) shouldBe 0
        }

        "only sprite 0 triggers the flag" {
            val chrRom = ChrRomBuilder()
                .tile(0, 0, makeCheckerboardTileData())
                .tile(0, 1, makeFrameSpriteTileData())
                .build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            ppu.mask = MASK_BG_ENABLE or MASK_SPRITE_ENABLE

            ppu.nametableRam[0] = 0x00
            ppu.paletteRam[0] = 0x0F
            ppu.paletteRam[1] = 0x01
            ppu.paletteRam[0x11] = 0x16

            // Sprite 0: offscreen (Y=0xEF puts it below visible area)
            ppu.oamRam[0] = 0xEF.toByte()
            ppu.oamRam[1] = 0x01.toByte()
            ppu.oamRam[2] = 0x00.toByte()
            ppu.oamRam[3] = 0x00.toByte()

            // Sprite 1: overlaps opaque BG at (0,0)
            ppu.oamRam[4] = 0x00.toByte()
            ppu.oamRam[5] = 0x01.toByte()
            ppu.oamRam[6] = 0x00.toByte()
            ppu.oamRam[7] = 0x00.toByte()

            ppu.tick(9 * 341)

            (ppu.status and STATUS_SPRITE0_HIT) shouldBe 0
        }
    }

    "tick-based rendering" - {
        fun writePpuAddr(bus: MemoryBus, addr: Int) {
            bus.write(0x2006, (addr shr 8) and 0xFF)
            bus.write(0x2006, addr and 0xFF)
        }

        // Ticking through all 240 visible scanlines (each 341 dots) fills the framebuffer.
        val VISIBLE_FRAME_CYCLES = 240 * 341

        "should produce correct pixels after ticking through 240 visible scanlines" {
            val chrRom = ChrRomBuilder().tile(0, 0, makeCheckerboardTileData()).build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            val bus = MemoryBus(ppu, ByteArray(0x4000))
            ppu.mask = MASK_BG_ENABLE

            writePpuAddr(bus, 0x2000)
            bus.write(0x2007, 0x00)             // tile 0 at top-left
            writePpuAddr(bus, 0x23C0)
            bus.write(0x2007, 0b00000001)       // palette 1 for top-left quadrant
            writePpuAddr(bus, 0x3F00)
            bus.write(0x2007, 0x0F)             // universal bg color
            writePpuAddr(bus, 0x3F05)
            bus.write(0x2007, 0x01)             // palette 1 color 1
            bus.write(0x2007, 0x21)             // palette 1 color 2
            bus.write(0x2007, 0x31)             // palette 1 color 3

            ppu.tick(VISIBLE_FRAME_CYCLES)

            val frame = ppu.currentFrame()
            val actualTile = (0 until 8).flatMap { y ->
                (0 until 8).map { x -> frame[y * FRAME_WIDTH + x] }
            }
            val expectedTile = listOf(
                0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F,
                0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01,
                0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F,
                0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01,
                0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F,
                0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01,
                0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F,
                0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01, 0x0F, 0x01
            ).map { nesPalette[it] }

            actualTile shouldBe expectedTile
        }

        "should render incrementally — only ticked scanlines appear in the framebuffer" {
            val chrRom = ChrRomBuilder().tile(0, 0, makeCheckerboardTileData()).build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            val bus = MemoryBus(ppu, ByteArray(0x4000))
            ppu.mask = MASK_BG_ENABLE

            // Fill all 32×30 nametable positions with tile 0
            writePpuAddr(bus, 0x2000)
            repeat(TILES_PER_ROW * TILES_PER_COL) { bus.write(0x2007, 0x00) }
            writePpuAddr(bus, 0x3F00)
            bus.write(0x2007, 0x0F)             // universal bg color
            bus.write(0x2007, 0x01)             // palette 0 color 1

            // Tick through only the first 5 scanlines
            ppu.tick(5 * 341)

            val frame = ppu.currentFrame()

            // Rows 0–4 should have been drawn (non-zero pixels from the tile)
            val renderedRows = (0 until 5).flatMap { y ->
                (0 until FRAME_WIDTH).map { x -> frame[y * FRAME_WIDTH + x] }
            }
            renderedRows.any { it != 0 } shouldBe true

            // Row 5 and below should still be untouched (zero-initialised)
            val unrenderedRows = (5 until FRAME_HEIGHT).flatMap { y ->
                (0 until FRAME_WIDTH).map { x -> frame[y * FRAME_WIDTH + x] }
            }
            unrenderedRows.all { it == 0 } shouldBe true
        }

        "should not render when PPUMASK background enable bit is clear" {
            val chrRom = ChrRomBuilder().tile(0, 0, makeCheckerboardTileData()).build()
            val ppu = PPU(chrRom, MirroringMode.VERTICAL)
            val bus = MemoryBus(ppu, ByteArray(0x4000))
            // mask left at 0 — background rendering disabled

            writePpuAddr(bus, 0x2000)
            bus.write(0x2007, 0x00)             // tile 0 at top-left
            writePpuAddr(bus, 0x3F00)
            bus.write(0x2007, 0x0F)             // universal bg color

            ppu.tick(VISIBLE_FRAME_CYCLES)

            val frame = ppu.currentFrame()
            frame.all { it == 0 } shouldBe true
        }
    }
})
