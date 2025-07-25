package nestor

const val FRAME_WIDTH = 256
const val FRAME_HEIGHT = 240
const val TILE_WIDTH = 8
const val TILE_HEIGHT = 8
const val TILES_PER_ROW = FRAME_WIDTH / TILE_WIDTH
const val TILES_PER_COL = FRAME_HEIGHT / TILE_HEIGHT

const val NAMETABLE_SIZE = 0x0400
const val NAMETABLE_RAM_SIZE = 2 * NAMETABLE_SIZE
const val PALETTE_RAM_SIZE = 32
const val ATTRTABLE_OFFSET = 0x03C0

const val NAMETABLE_START = 0x2000
const val PALETTE_START = 0x3F00

class PPU(
    val tiles: List<Array<IntArray>>,
    val nametableRam: ByteArray = ByteArray(NAMETABLE_RAM_SIZE),
    val paletteRam: ByteArray = ByteArray(PALETTE_RAM_SIZE),
) {
    private val framebuffer = IntArray(FRAME_WIDTH * FRAME_HEIGHT)

    fun readRegister(addr: Int): Int = when (addr) {
        in 0x2000..0x3EFF -> {
            val mirroredAddr = mirrorNametableAddr(addr)
            nametableRam[mirroredAddr].toUByte().toInt()
        }

        in 0x3F00..0x3FFF -> {
            val mirroredAddr = mirrorPaletteAddr(addr)
            paletteRam[mirroredAddr].toUByte().toInt()
        }

        else -> throw IllegalArgumentException("Unsupported PPU read from $addr")
    }

    fun writeRegister(addr: Int, value: Int) {
        val byteVal = value.toByte()
        when (addr) {
            in 0x2000..0x3EFF -> {
                val mirroredAddr = mirrorNametableAddr(addr)
                nametableRam[mirroredAddr] = byteVal
            }

            in 0x3F00..0x3FFF -> {
                val mirroredAddr = mirrorPaletteAddr(addr)
                paletteRam[mirroredAddr] = byteVal
            }

            else -> throw IllegalArgumentException("Unsupported PPU write to $addr")
        }
    }

    private fun mirrorNametableAddr(addr: Int): Int =
        // Mirror $2000–$2FFF into 2KB nametable RAM
        (addr - NAMETABLE_START) % NAMETABLE_RAM_SIZE

    private fun mirrorPaletteAddr(addr: Int): Int {
        // Palette space is mirrored every 32 bytes from $3F00–$3FFF
        val mirrored = (addr - PALETTE_START) % PALETTE_RAM_SIZE

        // Handle mirrored background color entries:
        // $3F10/$3F14/$3F18/$3F1C mirror $3F00/$3F04/$3F08/$3F0C
        return when (mirrored) {
            0x10 -> 0x00
            0x14 -> 0x04
            0x18 -> 0x08
            0x1C -> 0x0C
            else -> mirrored
        }
    }

    /**
     * Renders the visible 256x240 frame using nametable 0 (top-left).
     * Does not include scrolling or mirroring logic yet.
     */
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
        require(tileX in 0 until TILES_PER_ROW && tileY in 0 until TILES_PER_COL) {
            "getTile(): tile coordinates out of bounds — tileX=$tileX (max ${TILES_PER_ROW - 1}), tileY=$tileY (max ${TILES_PER_COL - 1})"
        }
        val nametableIndex = tileY * TILES_PER_ROW + tileX
        val tileIndex = nametableRam[nametableIndex].toUByte().toInt()
        return tiles[tileIndex]
    }

    private fun getPaletteForTile(tileX: Int, tileY: Int): IntArray {
        require(tileX in 0 until TILES_PER_ROW * 2 && tileY in 0 until TILES_PER_COL * 2) {
            "Tile coordinates out of bounds: ($tileX, $tileY)"
        }

        // Determine which nametable this tile is in (0–3)
        val nametableCol = tileX / TILES_PER_ROW  // 0 or 1
        val nametableRow = tileY / TILES_PER_COL  // 0 or 1
        val nametableIndex = nametableRow * 2 + nametableCol  // 0 to 3

        val localTileX = tileX % TILES_PER_ROW
        val localTileY = tileY % TILES_PER_COL
        val nametableBase = nametableIndex * NAMETABLE_SIZE

        // Compute attribute table index
        val attrX = localTileX / 4
        val attrY = localTileY / 4
        val attrIndex = attrY * (TILES_PER_ROW / 4) + attrX  // 8 x 8 grid
        val attrAddr = nametableBase + ATTRTABLE_OFFSET + attrIndex

        val attrByte = nametableRam[attrAddr].toUByte().toInt()

        val shift = when {
            localTileX % 4 < 2 && localTileY % 4 < 2 -> 0
            localTileX % 4 >= 2 && localTileY % 4 < 2 -> 2
            localTileX % 4 < 2 && localTileY % 4 >= 2 -> 4
            else -> 6
        }

        val paletteIndex = (attrByte shr shift) and 0b11
        return getPalette(paletteIndex)
    }

    private fun getPalette(paletteIndex: Int): IntArray {
        require(paletteIndex in 0..3) { "Invalid background palette index: $paletteIndex" }

        val universalBgColorIndex = paletteRam[0].toUByte().toInt()

        // Use correct offset into paletteRam (non-contiguous)
        val colorBase = 1 + paletteIndex * 4

        val color1Index = paletteRam[colorBase].toUByte().toInt() and 0x3F
        val color2Index = paletteRam[colorBase + 1].toUByte().toInt() and 0x3F
        val color3Index = paletteRam[colorBase + 2].toUByte().toInt() and 0x3F

        return intArrayOf(
            nesPalette[universalBgColorIndex],
            nesPalette[color1Index],
            nesPalette[color2Index],
            nesPalette[color3Index],
        )
    }
}
