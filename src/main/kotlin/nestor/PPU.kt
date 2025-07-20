package nestor

class PPU(
    val rom: INESRom,
) {
    private val vram = ByteArray(0x4000)
    private val tiles = TileParser.parseTiles(rom.chrData)

    fun renderFrame(): IntArray {
        val framebuffer = IntArray(SCREEN_WIDTH * SCREEN_HEIGHT) { 0xFF000000.toInt() } // Clear to black

        val tileWidth = 8
        val tileHeight = 8
        val tilesPerRow = SCREEN_WIDTH / tileWidth
        val tileCount = 512

        for (i in 0 until tileCount) {
            val tile = tiles[i]
            val tileX = (i % tilesPerRow) * tileWidth
            val tileY = (i / tilesPerRow) * tileHeight

            for (y in 0 until tileHeight) {
                for (x in 0 until tileWidth) {
                    val colorIndex = tile[y][x]
                    val color = mapColor(colorIndex)
                    val px = tileX + x
                    val py = tileY + y
                    if (px < SCREEN_WIDTH && py < SCREEN_HEIGHT) {
                        framebuffer[py * SCREEN_WIDTH + px] = color
                    }
                }
            }
        }

        return framebuffer
    }

    private fun mapColor(index: Int): Int = when (index) {
        0 -> 0xFF000000.toInt() // black
        1 -> 0xFF555555.toInt() // dark gray
        2 -> 0xFFAAAAAA.toInt() // light gray
        3 -> 0xFFFFFFFF.toInt() // white
        else -> 0xFF000000.toInt()
    }
}
