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
            writePpuAddr(bus, 0x3F04)
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
