package nestor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class PPUTest : FreeSpec({
    "should render the top left tile using palette 1" {
        // Create a checkerboard pattern tile
        // Color layout:
        // 1 0 1 0 1 0 1 0
        // 0 1 0 1 0 1 0 1
        // ...
        val tileData = makeCheckerboardTile()
            .also { printTileBitplane(it) }
        val rom = mockk<INESRom>()
        every { rom.chrData } returns tileData + ByteArray(0x2000 - 16) // pad to full CHR bank

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

        val ppu = PPU(rom, nametableRam, paletteRam)
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
        val tileData = makeStripeTile()
            .also { printTileBitplane(it) }
        val rom = mockk<INESRom>()
        every { rom.chrData } returns tileData + ByteArray(0x2000 - 16)

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

        val ppu = PPU(rom, nametableRam, paletteRam)
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
})
