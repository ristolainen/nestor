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

    "nametable RAM writes and reads" - {
        val ppu = PPU(listOf())

        "should write to $2000 and read back" {
            ppu.writeRegister(0x2000, 0x11)
            ppu.readRegister(0x2000) shouldBe 0x11
            ppu.nametableRam[0x0000] shouldBe 0x11.toByte()
        }

        "should write to $27FF and mirror correctly at $37FF" {
            ppu.writeRegister(0x27FF, 0x22)
            ppu.readRegister(0x37FF) shouldBe 0x22
            ppu.nametableRam[0x07FF] shouldBe 0x22.toByte()
        }

        "should write to $2ABC and mirror to $3ABC" {
            ppu.writeRegister(0x2ABC, 0x33)
            ppu.readRegister(0x3ABC) shouldBe 0x33
        }
    }

    "palette RAM writes and reads" - {
        val ppu = PPU(listOf())

        "should write to $3F00 and read back" {
            ppu.writeRegister(0x3F00, 0x40)
            ppu.readRegister(0x3F00) shouldBe 0x40
            ppu.paletteRam[0x00] shouldBe 0x40.toByte()
        }

        "should write to $3F1F and mirror to $3FFF" {
            ppu.writeRegister(0x3F1F, 0x50)
            ppu.readRegister(0x3FFF) shouldBe 0x50
            ppu.paletteRam[0x1F] shouldBe 0x50.toByte()
        }

        "should mirror $3F10 to $3F00" {
            ppu.writeRegister(0x3F10, 0x66)
            ppu.readRegister(0x3F00) shouldBe 0x66
            ppu.paletteRam[0x00] shouldBe 0x66.toByte()
        }

        "should mirror $3F14 to $3F04" {
            ppu.writeRegister(0x3F14, 0x77)
            ppu.readRegister(0x3F04) shouldBe 0x77
            ppu.paletteRam[0x04] shouldBe 0x77.toByte()
        }
    }
})
