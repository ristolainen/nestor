package nestor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class PPUTest : FreeSpec({
    "renderFrame" - {
        "should render the top left tile using palette 1" {
            // Create a checkerboard pattern tile
            // Color layout:
            // 1 0 1 0 1 0 1 0
            // 0 1 0 1 0 1 0 1
            // ...
            val tile = makeCheckerboardTile()
                .also { printTile(it) }
            val nametableRam = ByteArray(NAMETABLE_RAM_SIZE)
            val paletteRam = ByteArray(PALETTE_RAM_SIZE)

            // Set tile 0 at top-left (0,0)
            nametableRam[0x0000] = 0x00

            // Set attribute table byte for top-left quadrant (palette 1)
            nametableRam[ATTRTABLE_OFFSET] = 0b00000001 // top-left quadrant = palette 1

            // Palette 1: indexes at $3F05–$3F07
            paletteRam[0x00] = 0x0F // universal bg color
            paletteRam[0x05] = 0x01
            paletteRam[0x06] = 0x21
            paletteRam[0x07] = 0x31

            val ppu = PPU(listOf(tile), nametableRam, paletteRam)
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
            val tile = makeStripedTile()
                .also { printTile(it) }
            val nametableRam = ByteArray(NAMETABLE_RAM_SIZE)
            val paletteRam = ByteArray(PALETTE_RAM_SIZE)

            val tileX = 31
            val tileY = 29
            val tileIndex = tileY * TILES_PER_ROW + tileX
            nametableRam[tileIndex] = 0x00  // tile 0 at bottom-right

            // Set attribute for (31,29) tile → quadrant = bottom-right → shift = 6 → 0b11
            val attrIndex = (tileY / 4) * 8 + (tileX / 4)
            nametableRam[ATTRTABLE_OFFSET + attrIndex] = 0b00001000  // palette 2 in bottom-right

            // Palette 2: $3F09–$3F0B
            paletteRam[0x00] = 0x0F
            paletteRam[0x09] = 0x11
            paletteRam[0x0A] = 0x21
            paletteRam[0x0B] = 0x31

            val ppu = PPU(listOf(tile), nametableRam, paletteRam)
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
                // colorIndex 2 → palette[2] = vram[0x3F0A] = 0x21
                // colorIndex 3 → palette[3] = vram[0x3F0B] = 0x31
                when (colorIndex) {
                    2 -> nesPalette[0x21]
                    3 -> nesPalette[0x31]
                    else -> error("This test should only include color indices 2 and 3")
                }
            }

            actualTile shouldBe expectedTile
        }
    }

    "cpuRead" - {
        "cpuRead $2002 should return status and clear VBlank and writeToggle" {
            val ppu = PPU(emptyList())
            ppu.status = 0b11100000  // VBlank + sprite flags set
            val result = ppu.cpuRead(0x2002)

            result shouldBe 0b11100000
            ppu.status and 0x80 shouldBe 0  // VBlank bit cleared
            ppu.writeToggle shouldBe false
        }

        "cpuRead $2004 should return value at OAMADDR" {
            val ppu = PPU(emptyList())
            ppu.oamRam[5] = 0x42
            ppu.oamAddr = 5

            val result = ppu.cpuRead(0x2004)
            result shouldBe 0x42
        }

        "cpuRead $2007 should return buffered value on nametable access" {
            val ppu = PPU(emptyList())
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
            val ppu = PPU(emptyList())
            ppu.vramAddr = 0x3F00
            ppu.paletteRam[0x00] = 0x3C

            val result = ppu.cpuRead(0x2007)
            result shouldBe 0x3C
        }

        "cpuRead $2007 should increment and wrap vramAddr" {
            val ppu = PPU(emptyList())
            ppu.vramAddr = 0x3FFF
            ppu.paletteRam[0x1F] = 0x21

            ppu.cpuRead(0x2007)
            ppu.vramAddr shouldBe 0x0000
        }
    }

    "cpuWrite" - {
        "cpuWrite $2000 should set control and update bits 10–11 of tempAddr" {
            val ppu = PPU(emptyList())
            ppu.cpuWrite(0x2000, 0b00000011)

            ppu.control shouldBe 0b00000011
            (ppu.tempAddr shr 10) and 0b11 shouldBe 0b11
        }

        "cpuWrite $2001 should set mask register" {
            val ppu = PPU(emptyList())
            ppu.cpuWrite(0x2001, 0x3F)

            ppu.mask shouldBe 0x3F
        }

        "cpuWrite $2003 should set oamAddr" {
            val ppu = PPU(emptyList())
            ppu.cpuWrite(0x2003, 0x42)

            ppu.oamAddr shouldBe 0x42
        }

        "cpuWrite $2004 should write to OAM at oamAddr and increment it" {
            val ppu = PPU(emptyList())
            ppu.oamAddr = 0x10
            ppu.cpuWrite(0x2004, 0xAB)

            ppu.oamRam[0x10].toUByte().toInt() shouldBe 0xAB
            ppu.oamAddr shouldBe 0x11
        }

        "cpuWrite $2005 should write fineX and coarseX on first write" {
            val ppu = PPU(emptyList())
            ppu.writeToggle = false
            ppu.cpuWrite(0x2005, 0b10100101)

            ppu.fineXScroll shouldBe 0b00000101
            ppu.tempAddr and 0x001F shouldBe 0b10100
            ppu.writeToggle shouldBe true
            ppu.scrollX shouldBe 0b10100101
        }

        "cpuWrite $2005 should write coarseY and fineY on second write" {
            val ppu = PPU(emptyList())
            ppu.writeToggle = true
            ppu.cpuWrite(0x2005, 0b01110110)

            (ppu.tempAddr shr 12) and 0b111 shouldBe 0b110  // fine Y
            (ppu.tempAddr shr 5) and 0b11111 shouldBe 0b01110  // coarse Y
            ppu.writeToggle shouldBe false
            ppu.scrollY shouldBe 0b01110110
        }

        "cpuWrite $2006 should write high byte of tempAddr on first write" {
            val ppu = PPU(emptyList())
            ppu.writeToggle = false
            ppu.cpuWrite(0x2006, 0x3F)

            (ppu.tempAddr shr 8) and 0x3F shouldBe 0x3F
            ppu.writeToggle shouldBe true
        }

        "cpuWrite $2006 should write low byte and set vramAddr on second write" {
            val ppu = PPU(emptyList())
            ppu.writeToggle = true
            ppu.tempAddr = 0x3F00
            ppu.cpuWrite(0x2006, 0x10)

            ppu.tempAddr shouldBe 0x3F10
            ppu.vramAddr shouldBe 0x3F10
            ppu.writeToggle shouldBe false
        }

        "cpuWrite $2007 should write to nametableRam and increment vramAddr" {
            val ppu = PPU(emptyList())
            ppu.vramAddr = 0x2000
            ppu.cpuWrite(0x2007, 0x99)

            ppu.nametableRam[0x0000] shouldBe 0x99.toByte()
            ppu.vramAddr shouldBe 0x2001
        }

        "cpuWrite $2007 should write to paletteRam with mirrored address" {
            val ppu = PPU(emptyList())
            ppu.vramAddr = 0x3F10
            ppu.cpuWrite(0x2007, 0x0F)

            ppu.paletteRam[0x00] shouldBe 0x0F.toByte()
        }
    }
})
