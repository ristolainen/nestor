package nestor

import java.io.File

const val FRAME_WIDTH = 256
const val FRAME_HEIGHT = 240
const val TILE_WIDTH = 8
const val TILE_HEIGHT = 8
const val TILES_PER_ROW = FRAME_WIDTH / TILE_WIDTH

class PPU(
    val rom: INESRom,
) {
    private val vram = ByteArray(0x4000)
    private val tiles = TileParser.parseTiles(rom.chrData)
    private val framebuffer = IntArray(FRAME_WIDTH * FRAME_HEIGHT) { 0xFF000000.toInt() } // Clear to black

    init {
        initTestPalette()
        fakeNameTables()
    }

    fun renderFrame() {
        val tileCount = 512
        for (tileIndex in 0 until tileCount) {
            renderTile(tileIndex)
        }
    }

    fun currentFrame() = framebuffer

    private fun renderTile(tileIndex: Int) {
        val tile = tiles[tileIndex]
        val tileX = tileIndex % TILES_PER_ROW
        val tileY = tileIndex / TILES_PER_ROW

        val palette = getPaletteForTile(tileX, tileY)

        for (y in 0 until TILE_HEIGHT) {
            for (x in 0 until TILE_WIDTH) {
                val colorIndex = tile[y][x]
                val color = palette[colorIndex]
                val px = (tileX * TILE_WIDTH) + x
                val py = (tileY * TILE_HEIGHT) + y
                framebuffer[py * FRAME_WIDTH + px] = color
            }
        }
    }

    private fun getPaletteForTile(tileX: Int, tileY: Int): IntArray {
        val attrTableBase = 0x23C0
        val attrX = tileX / 4
        val attrY = tileY / 4
        val attrIndex = attrY * 8 + attrX
        val attrByte = vram[attrTableBase + attrIndex].toInt() and 0xFF

        val shift = when {
            tileX % 4 < 2 && tileY % 4 < 2 -> 0
            tileX % 4 >= 2 && tileY % 4 < 2 -> 2
            tileX % 4 < 2 && tileY % 4 >= 2 -> 4
            else -> 6
        }

        val paletteIndex = (attrByte shr shift) and 0b11
        return getPalette(paletteIndex)
    }

    private fun getPalette(paletteIndex: Int): IntArray {
        require(paletteIndex in 0..3) { "Invalid background palette index: $paletteIndex" }

        val base = 0x3F00
        val universalBgColorIndex = vram[base].toInt() and 0x3F

        val color1Index = vram[base + 1 + paletteIndex * 4].toInt() and 0x3F
        val color2Index = vram[base + 2 + paletteIndex * 4].toInt() and 0x3F
        val color3Index = vram[base + 3 + paletteIndex * 4].toInt() and 0x3F

        return intArrayOf(
            nesPalette[universalBgColorIndex],
            nesPalette[color1Index],
            nesPalette[color2Index],
            nesPalette[color3Index],
        )
    }

    private fun mapColor(index: Int): Int = when (index) {
        0 -> 0xFF000000.toInt() // black
        1 -> 0xFF555555.toInt() // dark gray
        2 -> 0xFFAAAAAA.toInt() // light gray
        3 -> 0xFFFFFFFF.toInt() // white
        else -> 0xFF000000.toInt()
    }

    private fun initTestPalette() {
        // Each 4-byte block is a palette (background or sprite)
        val testPalette = byteArrayOf(
            0x0F, 0x21, 0x31, 0x30,  // Background palette 0: Sky + bricks
            0x0F, 0x1A, 0x2A, 0x27,  // Background palette 1: Bushes + clouds
            0x0F, 0x16, 0x27, 0x18,  // Background palette 2: Ground + shadows
            0x0F, 0x00, 0x10, 0x20   // Background palette 3: Misc/unused
        )

        for (i in testPalette.indices) {
            vram[0x3F00 + i] = testPalette[i]
        }
    }

    private fun fakeNameTables() {
        val save = File("/Users/misto/tmp/State").readBytes()

        // Copy mock nametable into 0x2000
        val available = save.size - 0x1000
        val safeLength = minOf(960, available)
        save.copyInto(vram, 0x2000, 0x1000, 0x1000 + safeLength)

        // Optionally fill attribute table with a default (all palette 0)
        //vram.fill(0x00, 0x23C0, 0x2400)

        // Cycle through palettes
        for (i in 0x23C0 until 0x2400) {
            val quadrant = (i - 0x23C0) % 4
            val value = when (quadrant) {
                0 -> 0b00000000  // All palette 0
                1 -> 0b01010101  // All palette 1
                2 -> 0b10101010  // All palette 2
                else -> 0b11111111  // All palette 3
            }.toByte()
            vram[i] = value
        }
    }
}
