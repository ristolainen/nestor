package nestor

const val FRAME_WIDTH = 256
const val FRAME_HEIGHT = 240
const val TILE_WIDTH = 8
const val TILE_HEIGHT = 8
const val TILES_PER_ROW = FRAME_WIDTH / TILE_WIDTH
const val TILES_PER_COL = FRAME_HEIGHT / TILE_HEIGHT

const val VRAM_SIZE = 0x4000
const val NAMETABLE_BASE = 0x2000
const val ATTRTABLE_BASE = 0x23C0
const val PALETTE_BASE = 0x3F00

class PPU(
    rom: INESRom,
    val vram: ByteArray = ByteArray(VRAM_SIZE)
) {
    private val tiles = TileParser.parseTiles(rom.chrData)
    private val framebuffer = IntArray(FRAME_WIDTH * FRAME_HEIGHT)

    fun renderFrame() {
        for (tileY in 0 until TILES_PER_COL) {
            for (tileX in 0 until TILES_PER_ROW) {
                renderTile(tileX, tileY)
            }
        }
    }

    fun currentFrame() = framebuffer

    private fun renderTile(tileX: Int, tileY: Int) {
        val tile = getTile(tileX, tileY)
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

    private fun getTile(tileX: Int, tileY: Int): Array<IntArray> {
        val nametableIndex = NAMETABLE_BASE + tileY * TILES_PER_ROW + tileX
        val tileIndex = vram[nametableIndex].toInt() and 0xFF
        return tiles[tileIndex]
    }

    private fun getPaletteForTile(tileX: Int, tileY: Int): IntArray {
        val attrX = tileX / 4
        val attrY = tileY / 4
        val attrIndex = attrY * 8 + attrX
        val attrByte = vram[ATTRTABLE_BASE + attrIndex].toInt() and 0xFF

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

        val base = PALETTE_BASE
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
}
