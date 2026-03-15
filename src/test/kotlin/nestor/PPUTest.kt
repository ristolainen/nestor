package nestor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class PPUTest : FreeSpec({
    "tick" - {
        "should set VBLANK at the start of scanline 241" {
            val ppu = PPU(ByteArray(0))

            // Put the PPU right before the wrap that advances to scanline 241.
            ppu.scanline = 240
            ppu.cycle = 340

            // Advance one PPU cycle -> wraps cycle, increments scanline to 241, sets VBLANK.
            ppu.tick(1)

            ppu.scanline shouldBe 241
            (ppu.status and STATUS_VBLANK) shouldBe STATUS_VBLANK // VBlank flag set
        }

        "should clear VBLANK on the pre-render line (scanline 261) and reset writeToggle" {
            val ppu = PPU(ByteArray(0))

            // Force VBLANK on so we can verify it clears at 261.
            ppu.status = ppu.status or STATUS_VBLANK
            ppu.writeToggle = true

            // Step from end of scanline 260 into 261.
            ppu.scanline = 260
            ppu.cycle = 340
            ppu.tick(1)

            ppu.scanline shouldBe 261
            (ppu.status and STATUS_VBLANK) shouldBe 0          // VBlank cleared
            ppu.writeToggle shouldBe false                     // latch reset
        }

        "readStatus ($2002) should clear VBLANK and reset the address latch (writeToggle)" {
            val ppu = PPU(ByteArray(0))

            // Set VBLANK and toggle latch to verify side effects of reading $2002.
            ppu.status = ppu.status or STATUS_VBLANK
            ppu.writeToggle = true

            // CPU read of PPUSTATUS ($2002).
            ppu.cpuRead(0x2002)

            (ppu.status and STATUS_VBLANK) shouldBe 0          // VBlank cleared on read
            ppu.writeToggle shouldBe false                     // latch reset on read
        }
    }

    "renderFrame" - {
        fun writePpuAddr(memoryBus: MemoryBus, addr: Int) {
            memoryBus.write(0x2006, (addr shr 8) and 0xFF)
            memoryBus.write(0x2006, addr and 0xFF)
        }

        "should render the top left tile using palette 1" {
            val chrRom = ChrRomBuilder().tile(0, 0, makeCheckerboardTileData()).build()
            val ppu = PPU(chrRom)
            val memoryBus = MemoryBus(ppu, prgRom = ByteArray(0x4000)) // Empty ROM for test

            // Set tile 0 at top-left (0,0)
            writePpuAddr(memoryBus, 0x2000)
            memoryBus.write(0x2007, 0x00)

            // Set attribute byte for top-left quadrant of nametable (palette 1)
            writePpuAddr(memoryBus, 0x23C0)
            memoryBus.write(0x2007, 0b00000001)

            // Palette 1 setup — universal background color at $3F00, palette 1 at $3F04–$3F06
            writePpuAddr(memoryBus, 0x3F00)
            memoryBus.write(0x2007, 0x0F) // universal BG
            // Palette 1 → $3F04–$3F06
            writePpuAddr(memoryBus, 0x3F04)
            memoryBus.write(0x2007, 0x01) // color 1
            memoryBus.write(0x2007, 0x21) // color 2
            memoryBus.write(0x2007, 0x31) // color 3

            ppu.renderFrame()
            val frame = ppu.currentFrame()

            printAnsiTile(frame, 0, 0)

            val actualTile = (0 until 8).flatMap { y ->
                (0 until 8).map { x ->
                    frame[y * FRAME_WIDTH + x]
                }
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

        "should render the bottom right tile using palette 2" {
            val chrRom = ChrRomBuilder().tile(0, 0, makeStripeTileData()).build()
            val ppu = PPU(chrRom)
            val bus = MemoryBus(ppu, prgRom = ByteArray(0x4000)) // dummy ROM

            val tileX = 31
            val tileY = 29
            val tileIndex = tileY * TILES_PER_ROW + tileX

            // Write tile 0 at bottom-right position in nametable
            writePpuAddr(bus, 0x2000 + tileIndex)
            bus.write(0x2007, 0x00)

            // Write attribute byte for the (31, 29) tile's 32x32 region
            val attrIndex = (tileY / 4) * 8 + (tileX / 4) // 8x8 attribute grid
            val attrAddr = 0x23C0 + attrIndex
            bus.write(0x2006, (attrAddr shr 8) and 0xFF)
            bus.write(0x2006, attrAddr and 0xFF)
            bus.write(0x2007, 0b00001000) // palette 2 in bottom-right quadrant (bits 6–7)

            // Write palette RAM for palette 2: $3F00 + 1 + (2 * 3) = $3F07–$3F09
            writePpuAddr(bus, 0x3F00)
            bus.write(0x2007, 0x0F) // universal bg color
            writePpuAddr(bus, 0x3F07)
            bus.write(0x2007, 0x11) // $3F07 – Palette 2 color 1
            bus.write(0x2007, 0x21) // $3F08 – Palette 2 color 2
            bus.write(0x2007, 0x31) // $3F09 – Palette 2 color 3

            ppu.renderFrame()
            val frame = ppu.currentFrame()

            val pxOffset = tileX * TILE_WIDTH
            val pyOffset = tileY * TILE_HEIGHT

            printAnsiTile(frame, tileX, tileY)

            val actualTile = (0 until 8).flatMap { y ->
                (0 until 8).map { x ->
                    frame[(pyOffset + y) * FRAME_WIDTH + (pxOffset + x)]
                }
            }

            val expectedIndices = List(8) {
                listOf(3, 2, 3, 2, 3, 2, 3, 2)
            }.flatten()

            val expectedTile = expectedIndices.map { colorIndex ->
                when (colorIndex) {
                    2 -> nesPalette[0x21] // Palette color 2
                    3 -> nesPalette[0x31] // Palette color 3
                    else -> error("This test should only include color indices 2 and 3")
                }
            }

            actualTile shouldBe expectedTile
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
            val ppu = PPU(bankedChrRom)
            val bus = MemoryBus(ppu, ByteArray(0x4000))
            ppu.control = 0x00 // bit 4 clear → bank 0
            setupNametableAndPalette(bus)

            ppu.renderFrame()
            val frame = ppu.currentFrame()

            // Bank 0 tile 0 = checkerboard: pixel (0,0) has color index 1
            frame[0] shouldBe nesPalette[0x01]
        }

        "PPUCTRL bit 4 = 1 uses bank 1 (tiles 256–511)" {
            val ppu = PPU(bankedChrRom)
            val bus = MemoryBus(ppu, ByteArray(0x4000))
            ppu.control = 0x10 // bit 4 set → bank 1
            setupNametableAndPalette(bus)

            ppu.renderFrame()
            val frame = ppu.currentFrame()

            // Bank 1 tile 0 = striped: pixel (0,0) has color index 3
            frame[0] shouldBe nesPalette[0x31]
        }
    }

    "cpuRead" - {
        "cpuRead $2002 should return status and clear VBlank and writeToggle" {
            val ppu = PPU(ByteArray(0))
            ppu.status = 0b11100000  // VBlank + sprite flags set
            val result = ppu.cpuRead(0x2002)

            result shouldBe 0b11100000
            ppu.status and 0x80 shouldBe 0  // VBlank bit cleared
            ppu.writeToggle shouldBe false
        }

        "cpuRead $2004 should return value at OAMADDR" {
            val ppu = PPU(ByteArray(0))
            ppu.oamRam[5] = 0x42
            ppu.oamAddr = 5

            val result = ppu.cpuRead(0x2004)
            result shouldBe 0x42
        }

        "cpuRead $2007 should return buffered value on nametable access" {
            val ppu = PPU(ByteArray(0))
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
            val ppu = PPU(ByteArray(0))
            ppu.vramAddr = 0x3F00
            ppu.paletteRam[0x00] = 0x3C

            val result = ppu.cpuRead(0x2007)
            result shouldBe 0x3C
        }

        "cpuRead $2007 should increment and wrap vramAddr" {
            val ppu = PPU(ByteArray(0))
            ppu.vramAddr = 0x3FFF
            ppu.paletteRam[0x1F] = 0x21

            ppu.cpuRead(0x2007)
            ppu.vramAddr shouldBe 0x0000
        }
    }

    "cpuWrite" - {
        "cpuWrite $2000 should set control and update bits 10–11 of tempAddr" {
            val ppu = PPU(ByteArray(0))
            ppu.cpuWrite(0x2000, 0b00000011)

            ppu.control shouldBe 0b00000011
            (ppu.tempAddr shr 10) and 0b11 shouldBe 0b11
        }

        "cpuWrite $2001 should set mask register" {
            val ppu = PPU(ByteArray(0))
            ppu.cpuWrite(0x2001, 0x3F)

            ppu.mask shouldBe 0x3F
        }

        "cpuWrite $2003 should set oamAddr" {
            val ppu = PPU(ByteArray(0))
            ppu.cpuWrite(0x2003, 0x42)

            ppu.oamAddr shouldBe 0x42
        }

        "cpuWrite $2004 should write to OAM at oamAddr and increment it" {
            val ppu = PPU(ByteArray(0))
            ppu.oamAddr = 0x10
            ppu.cpuWrite(0x2004, 0xAB)

            ppu.oamRam[0x10].toUByte().toInt() shouldBe 0xAB
            ppu.oamAddr shouldBe 0x11
        }

        "cpuWrite $2005 should write fineX and coarseX on first write" {
            val ppu = PPU(ByteArray(0))
            ppu.writeToggle = false
            ppu.cpuWrite(0x2005, 0b10100101)

            ppu.fineXScroll shouldBe 0b00000101
            ppu.tempAddr and 0x001F shouldBe 0b10100
            ppu.writeToggle shouldBe true
            ppu.scrollX shouldBe 0b10100101
        }

        "cpuWrite $2005 should write coarseY and fineY on second write" {
            val ppu = PPU(ByteArray(0))
            ppu.writeToggle = true
            ppu.cpuWrite(0x2005, 0b01110110)

            (ppu.tempAddr shr 12) and 0b111 shouldBe 0b110  // fine Y
            (ppu.tempAddr shr 5) and 0b11111 shouldBe 0b01110  // coarse Y
            ppu.writeToggle shouldBe false
            ppu.scrollY shouldBe 0b01110110
        }

        "cpuWrite $2006 should write high byte of tempAddr on first write" {
            val ppu = PPU(ByteArray(0))
            ppu.writeToggle = false
            ppu.cpuWrite(0x2006, 0x3F)

            (ppu.tempAddr shr 8) and 0x3F shouldBe 0x3F
            ppu.writeToggle shouldBe true
        }

        "cpuWrite $2006 should write low byte and set vramAddr on second write" {
            val ppu = PPU(ByteArray(0))
            ppu.writeToggle = true
            ppu.tempAddr = 0x3F00
            ppu.cpuWrite(0x2006, 0x10)

            ppu.tempAddr shouldBe 0x3F10
            ppu.vramAddr shouldBe 0x3F10
            ppu.writeToggle shouldBe false
        }

        "cpuWrite $2007 should write to nametableRam and increment vramAddr" {
            val ppu = PPU(ByteArray(0))
            ppu.vramAddr = 0x2000
            ppu.cpuWrite(0x2007, 0x99)

            ppu.nametableRam[0x0000] shouldBe 0x99.toByte()
            ppu.vramAddr shouldBe 0x2001
        }

        "cpuWrite $2007 should write to paletteRam with mirrored address" {
            val ppu = PPU(ByteArray(0))
            ppu.vramAddr = 0x3F10
            ppu.cpuWrite(0x2007, 0x0F)

            ppu.paletteRam[0x00] shouldBe 0x0F.toByte()
        }
    }
})
