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
const val OAM_RAM_SIZE = 256

const val NAMETABLE_START = 0x2000
const val PALETTE_START = 0x3F00

class PPU(
    val tiles: List<Array<IntArray>>,
) {
    internal val nametableRam: ByteArray = ByteArray(NAMETABLE_RAM_SIZE)
    internal val paletteRam: ByteArray = ByteArray(PALETTE_RAM_SIZE)
    internal val oamRam: ByteArray = ByteArray(OAM_RAM_SIZE)

    private val framebuffer = IntArray(FRAME_WIDTH * FRAME_HEIGHT)

    internal var control: Int = 0
    internal var mask: Int = 0
    internal var scrollX: Int = 0
    internal var scrollY: Int = 0
    internal var tempAddr: Int = 0
    internal var fineXScroll: Int = 0
    internal var oamAddr: Int = 0
    internal var status: Int = 0
    internal var ppuDataBuffer: Byte = 0
    internal var vramAddr: Int = 0
    internal var writeToggle = false

    fun cpuRead(addr: Int): Int = when (addr and 0x2007) {
        0x2002 -> readStatus()
        0x2004 -> readOamData()
        0x2007 -> readPpuData()
        else -> throw IllegalArgumentException("PPU: Unsupported read from ${addr.hex()}")
    }

    fun cpuWrite(addr: Int, value: Int) {
        when (addr and 0x2007) {
            0x2000 -> writeControl(value)
            0x2001 -> writeMask(value)
            0x2003 -> writeOamAddr(value)
            0x2004 -> writeOamData(value)
            0x2005 -> writeScroll(value)
            0x2006 -> writePpuAddr(value)
            0x2007 -> writePpuData(value)
            else -> println("PPU: Ignored write to unsupported register ${addr.hex()}")
        }
    }

    private fun readStatus(): Int {
        val result = status or (status and 0xE0) // NMI + sprite flags
        status = status and 0x7F // Clear VBlank bit (bit 7)
        writeToggle = false      // Reset address latch
        return result
    }

    private fun readOamData(): Int {
        return oamRam[oamAddr].toUByte().toInt()
    }

    private fun readPpuData(): Int {
        val result = when (vramAddr) {
            in PALETTE_START..0x3FFF -> {
                val mirroredAddr = mirrorPaletteAddr(vramAddr)
                paletteRam[mirroredAddr].toUByte().toInt()
            }

            else -> {
                val mirroredAddr = mirrorNametableAddr (vramAddr)
                val buffered = ppuDataBuffer
                ppuDataBuffer = nametableRam[mirroredAddr]
                buffered.toUByte().toInt()
            }
        }

        vramAddr = (vramAddr + 1) and 0x3FFF
        return result
    }

    private fun writeControl(value: Int) {
        control = value
        // Bits 0–1 of control specify the base nametable address.
        // These are copied into bits 10–11 of tempAddr as part of the scroll setup.
        // This allows fine X/Y scrolling and rendering to use the correct nametable region.
        tempAddr = (tempAddr and 0xF3FF) or ((value and 0x03) shl 10)
    }

    private fun writeMask(value: Int) {
        mask = value
    }

    private fun writeOamAddr(value: Int) {
        oamAddr = value and 0xFF
    }

    private fun writeOamData(value: Int) {
        oamRam[oamAddr] = value.toByte()
        oamAddr = (oamAddr + 1) and 0xFF
    }

    private fun writeScroll(value: Int) {
        if (!writeToggle) {
            // First write: horizontal scroll
            fineXScroll = value and 0x07
            tempAddr = (tempAddr and 0xFFE0) or (value shr 3)
            scrollX = value
        } else {
            // Second write: vertical scroll
            tempAddr = (tempAddr and 0x8FFF) or ((value and 0x07) shl 12)     // fine Y
            tempAddr = (tempAddr and 0xFC1F) or ((value and 0xF8) shl 2)      // coarse Y
            scrollY = value
        }
        writeToggle = !writeToggle
    }

    private fun writePpuAddr(value: Int) {
        if (!writeToggle) {
            tempAddr = (tempAddr and 0x00FF) or ((value and 0x3F) shl 8)
        } else {
            tempAddr = (tempAddr and 0xFF00) or (value and 0xFF)
            vramAddr = tempAddr
        }
        writeToggle = !writeToggle
    }

    private fun writePpuData(value: Int) {
        when (vramAddr) {
            in NAMETABLE_START until PALETTE_START -> {
                val mirrored = (vramAddr - NAMETABLE_START) % NAMETABLE_RAM_SIZE
                nametableRam[mirrored] = value.toByte()
            }

            in PALETTE_START..0x3FFF -> {
                val mirrored = mirrorPaletteAddr(vramAddr)
                paletteRam[mirrored] = value.toByte()
            }

            else -> {
                val mirrored = (vramAddr - NAMETABLE_START) % NAMETABLE_RAM_SIZE
                nametableRam[mirrored] = value.toByte()
            }
        }

        vramAddr = (vramAddr + 1) and 0x3FFF
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

        // Use correct offset into paletteRam
        val colorBase = 1 + paletteIndex * 3

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
