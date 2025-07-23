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

        printTileBitplane(tileData)

        val rom = mockk<INESRom>()
        every { rom.chrData } returns tileData + ByteArray(0x2000 - 16) // pad to full CHR bank

        val vram = ByteArray(VRAM_SIZE)

        // --- Set nametable to use tile 0 at top-left (0,0) ---
        vram[NAMETABLE_BASE] = 0x00

        // --- Set attribute table so tile (0,0) uses palette 1 ---
        vram[ATTRTABLE_BASE] = 0b00000001 // top-left quadrant = palette 1

        // --- Set background palette 1 in $3F05–$3F07 ---
        vram[PALETTE_BASE + 0] = 0x0F // universal bg color
        vram[PALETTE_BASE + 5] = 0x01
        vram[PALETTE_BASE + 6] = 0x21
        vram[PALETTE_BASE + 7] = 0x31

        val ppu = PPU(rom, vram)
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

        printTileBitplane(tileData)

        val rom = mockk<INESRom>()
        every { rom.chrData } returns tileData + ByteArray(0x2000 - 16)

        val vram = ByteArray(VRAM_SIZE)

        val tileX = 31
        val tileY = 29
        val tileIndex = NAMETABLE_BASE + tileY * TILES_PER_ROW + tileX
        vram[tileIndex] = 0x00 // use tile 0 at bottom-right

        // Set attribute table byte that includes (31,29)
        // Top-right quadrant → bits 2-3 → palette index 2
        // TODO: Is this really correct? Suspect that this should be bottom-right.
        val attrIndex = (tileY / 4) * 8 + (tileX / 4)
        vram[ATTRTABLE_BASE + attrIndex] = 0b00001000

        // Set palette 2 entries in $3F09–$3F0B
        vram[PALETTE_BASE + 0] = 0x0F // universal bg color (not used)
        vram[PALETTE_BASE + 9] = 0x11 // palette[1]
        vram[PALETTE_BASE + 10] = 0x21 // palette[2]
        vram[PALETTE_BASE + 11] = 0x31 // palette[3]

        println("0x3F0A: " + vram[0x3F0A])
        println("0x3F0B: " + vram[0x3F0B])

        val ppu = PPU(rom, vram)
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
