package nestor

/**
 * Generates a 2bpp NES tile with a checkerboard pattern (color indices 0 and 1).
 * Color layout:
 *  1 0 1 0 1 0 1 0
 *  0 1 0 1 0 1 0 1
 *  ...
 */
fun makeCheckerboardTile(): ByteArray {
    val tile = ByteArray(16)
    val evenRow = 0b10101010
    val oddRow  = 0b01010101

    // plane 0 holds the low bits: alternate 1s and 0s
    for (i in 0 until 8) {
        tile[i] = if (i % 2 == 0) evenRow.toByte() else oddRow.toByte()
    }

    // plane 1 holds the high bits: all 0s for indices 0 and 1 only
    for (i in 8 until 16) {
        tile[i] = 0x00
    }

    return tile
}

fun makeStripeTile(): ByteArray {
    val tile = ByteArray(16)

    val rowLowBits = 0b10101010 // plane 0: bit 0 for 2/3
    val rowHighBits = 0b11111111 // plane 1: bit 1 for 2/3

    for (i in 0 until 8) {
        tile[i] = rowLowBits.toByte()      // plane 0
        tile[8 + i] = rowHighBits.toByte() // plane 1
    }

    return tile
}

fun printTileBitplane(tileData: ByteArray) {
    require(tileData.size == 16) { "Tile must be 16 bytes (8 bytes per bitplane)" }

    val plane0 = tileData.slice(0 until 8)
    val plane1 = tileData.slice(8 until 16)

    println("Tile pixel color indices (0-3):")
    for (y in 0 until 8) {
        val row0 = plane0[y].toInt() and 0xFF
        val row1 = plane1[y].toInt() and 0xFF

        val row = buildString {
            for (x in 7 downTo 0) {
                val bit0 = (row0 shr x) and 1
                val bit1 = (row1 shr x) and 1
                val colorIndex = (bit1 shl 1) or bit0
                append(colorIndex)
            }
        }
        println(row)
    }
}

fun rgbToAnsi(rgb: Int): String {
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF

    return when {
        r < 32 && g < 32 && b < 32 -> "\u001B[40m" // black
        r > 200 && g > 200 && b > 200 -> "\u001B[47m" // white
        r > 200 && g > 200 -> "\u001B[43m" // yellow
        r > 200 -> "\u001B[41m" // red
        g > 200 -> "\u001B[42m" // green
        b > 200 -> "\u001B[44m" // blue
        r > 150 && b > 150 -> "\u001B[45m" // magenta
        g > 150 && b > 150 -> "\u001B[46m" // cyan
        else -> "\u001B[100m" // dark gray fallback
    }
}

fun printAnsiFrame(frame: IntArray) {
    val colorMap = frame.toSet().associateWith { rgbToAnsi(it) }

    for (y in 0 until FRAME_HEIGHT) {
        val row = buildString {
            for (x in 0 until FRAME_WIDTH) {
                val color = frame[y * FRAME_WIDTH + x]
                append("${colorMap[color]}  ")
            }
            append("\u001B[0m")
        }
        println(row)
    }
}

fun printAnsiTile(frame: IntArray, tileX: Int, tileY: Int) {
    val startX = tileX * TILE_WIDTH
    val startY = tileY * TILE_HEIGHT

    if (startX + TILE_WIDTH > FRAME_WIDTH || startY + TILE_HEIGHT > FRAME_HEIGHT) {
        println("Tile out of bounds!")
        return
    }

    val tilePixels = mutableListOf<Int>()
    for (y in 0 until TILE_HEIGHT) {
        for (x in 0 until TILE_WIDTH) {
            val globalX = startX + x
            val globalY = startY + y
            val index = globalY * FRAME_WIDTH + globalX
            tilePixels.add(frame[index])
        }
    }

    val colorMap = tilePixels.toSet().associateWith { rgbToAnsi(it) }

    for (y in 0 until TILE_HEIGHT) {
        val row = buildString {
            for (x in 0 until TILE_WIDTH) {
                val color = tilePixels[y * TILE_HEIGHT + x]
                append("${colorMap[color]}  ")
            }
            append("\u001B[0m")
        }
        println(row)
    }
}
