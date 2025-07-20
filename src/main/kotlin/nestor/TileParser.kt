package nestor

object TileParser {
    fun parseTiles(chrRom: ByteArray): List<Array<IntArray>> = buildList {
        require(chrRom.size % 16 == 0) {
            "chr-rom data length is not even 16 byte tiles"
        }
        val numTiles = chrRom.size / 16

        for (tileIndex in 0 until numTiles) {
            val tile = Array(8) { IntArray(8) }
            val offset = tileIndex * 16

            for (row in 0 until 8) {
                val plane0 = chrRom[offset + row].toInt() and 0xFF
                val plane1 = chrRom[offset + row + 8].toInt() and 0xFF

                for (col in 0 until 8) {
                    val bit0 = (plane0 shr (7 - col)) and 1
                    val bit1 = (plane1 shr (7 - col)) and 1
                    tile[row][col] = (bit1 shl 1) or bit0
                }
            }

            add(tile)
        }
    }
}
