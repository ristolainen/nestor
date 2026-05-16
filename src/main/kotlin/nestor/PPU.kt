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

const val STATUS_VBLANK = 0b10000000
const val CTRL_BG_PATTERN_TABLE = 0x10
const val MASK_BG_ENABLE = 0x08
const val MASK_SPRITE_ENABLE = 0x10
const val CTRL_SPRITE_PATTERN_TABLE = 0x08
const val STATUS_SPRITE0_HIT = 0b01000000

class PPU(private val chrRom: ByteArray, private val mirroring: MirroringMode) {
    private val tiles = TileParser.parseTiles(chrRom)
    internal val nametableRam: ByteArray = ByteArray(NAMETABLE_RAM_SIZE)
    internal val paletteRam: ByteArray = ByteArray(PALETTE_RAM_SIZE)
    internal val oamRam: ByteArray = ByteArray(OAM_RAM_SIZE)

    private val framebuffer = IntArray(FRAME_WIDTH * FRAME_HEIGHT)

    internal var control: Int = 0
    internal var mask: Int = 0
    internal var tempAddr: Int = 0
    internal var fineXScroll: Int = 0
    internal var oamAddr: Int = 0
    internal var status: Int = 0
    internal var ppuDataBuffer: Byte = 0
    internal var vramAddr: Int = 0
    internal var writeToggle = false
    internal var scanline = 0
    internal var cycle = 0
    internal var frame: Long = 0
    internal var nmiOutput = false
    internal var nmiOccurred = false
    var frameReady = false

    fun tick(cycles: Int) {
        repeat(cycles) {
            cycle++
            if (cycle > 340) {
                cycle = 0
                scanline++
                if (scanline >= 262) {
                    scanline = 0
                    frame++
                }
            }

            if (cycle == 1) {
                when (scanline) {
                    241 -> { // start of VBlank at dot 1
                        setStatusFlag(STATUS_VBLANK)
                        nmiOccurred = true
                        frameReady = true
                    }
                    261 -> { // pre-render line at dot 1
                        clearStatusFlag(STATUS_VBLANK)
                        clearStatusFlag(STATUS_SPRITE0_HIT)
                        writeToggle = false
                        nmiOccurred = false
                    }
                }
            }

            // Dots 1–256 of visible scanlines (0–239) produce one pixel each.
            // cycle - 1 maps dot 1 → x=0, dot 256 → x=255.
            if (scanline < FRAME_HEIGHT
                && cycle in 1..FRAME_WIDTH
                && (mask and MASK_BG_ENABLE) != 0
            ) {
                renderPixel(cycle - 1, scanline)
            }
        }
    }

    private fun renderPixel(x: Int, y: Int) {
        framebuffer[y * FRAME_WIDTH + x] = calculatePixel(x, y)

        if ((status and STATUS_SPRITE0_HIT) == 0
            && (mask and MASK_SPRITE_ENABLE) != 0
            && x < 255
        ) {
            checkSprite0Hit(x, y)
        }
    }

    private fun checkSprite0Hit(x: Int, y: Int) {
        val sprite = getSprite(0)
        val row = y - (sprite.y + 1)
        val col = x - sprite.x
        if (row !in 0 until 8 || col !in 0 until 8) return

        if (spriteColorIndex(sprite, row, col) == 0) return

        if (fetchBackground(x, y).colorIndex != 0) {
            setStatusFlag(STATUS_SPRITE0_HIT)
        }
    }

    internal fun calculatePixel(x: Int, y: Int): Int {
        val bg = fetchBackground(x, y)

        val sprite = if ((mask and MASK_SPRITE_ENABLE) != 0) spriteAt(x, y) else null

        return when {
            sprite == null -> bg.palette[bg.colorIndex]
            sprite.behindBackground && bg.colorIndex != 0 -> bg.palette[bg.colorIndex]
            else -> {
                val row = y - (sprite.y + 1)
                val col = x - sprite.x
                getSpritePalette(sprite.palette)[spriteColorIndex(sprite, row, col)]
            }
        }
    }

    private data class BackgroundPixel(val colorIndex: Int, val palette: IntArray)

    private fun fetchBackground(x: Int, y: Int): BackgroundPixel {
        // Scroll source: bits of t and the fine X latch (see NESdev PPU_scrolling).
        val scrollCoarseX = tempAddr and 0x1F
        val scrollCoarseY = (tempAddr shr 5) and 0x1F
        val scrollNtX     = (tempAddr shr 10) and 0x01
        val scrollNtY     = (tempAddr shr 11) and 0x01
        val scrollFineY   = (tempAddr shr 12) and 0x07

        // Translate the on-screen pixel into a position in the 512x480 virtual nametable grid.
        val worldX = scrollCoarseX * 8 + fineXScroll + x
        val worldY = scrollCoarseY * 8 + scrollFineY + y

        val ntX     = scrollNtX xor (worldX / 256)
        val ntY     = scrollNtY xor (worldY / 240)
        val coarseX = (worldX / 8) and 0x1F
        val coarseY = ((worldY / 8) % 30) and 0x1F
        val fineX   = worldX and 0x07
        val fineY   = worldY and 0x07

        val ntAddr      = 0x2000 or (ntY shl 11) or (ntX shl 10) or (coarseY shl 5) or coarseX
        val tileId      = nametableRam[mirrorNametableAddr(ntAddr)].toUByte().toInt()
        val patternBase = if ((control and CTRL_BG_PATTERN_TABLE) != 0) 256 else 0
        val colorIndex  = tiles[patternBase + tileId][fineY][fineX]

        val attrAddr   = 0x23C0 or (ntY shl 11) or (ntX shl 10) or ((coarseY shr 2) shl 3) or (coarseX shr 2)
        val attrByte   = nametableRam[mirrorNametableAddr(attrAddr)].toUByte().toInt()
        val attrShift  = ((coarseY and 0b10) shl 1) or (coarseX and 0b10)
        val paletteIdx = (attrByte shr attrShift) and 0b11

        return BackgroundPixel(colorIndex, getPalette(paletteIdx))
    }

    fun cpuRead(addr: Int): Int = when (addr and 0x2007) {
        0x2002 -> readStatus()
        0x2004 -> readOamData()
        0x2007 -> readPpuData()
        else -> 0 // write-only registers return open bus (0)
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
        val result = status and 0xE0 // upper 3 bits only; lower 5 are open bus
        clearStatusFlag(STATUS_VBLANK)
        writeToggle = false      // Reset address latch
        nmiOccurred = false
        return result
    }

    private fun readOamData(): Int {
        return oamRam[oamAddr].toUByte().toInt()
    }

    private fun readPpuData(): Int {
        val result = when (vramAddr) {
            in 0x0000 until NAMETABLE_START -> {
                val buffered = ppuDataBuffer
                ppuDataBuffer = chrRom[vramAddr]
                buffered.toUByte().toInt()
            }

            in PALETTE_START..0x3FFF -> {
                val mirroredAddr = mirrorPaletteAddr(vramAddr)
                ppuDataBuffer = nametableRam[mirrorNametableAddr(vramAddr and 0x2FFF)]
                paletteRam[mirroredAddr].toUByte().toInt()
            }

            else -> {
                val mirroredAddr = mirrorNametableAddr(vramAddr)
                val buffered = ppuDataBuffer
                ppuDataBuffer = nametableRam[mirroredAddr]
                buffered.toUByte().toInt()
            }
        }

        vramAddr = (vramAddr + vramIncrement()) and 0x3FFF
        return result
    }

    private fun writeControl(value: Int) {
        control = value
        nmiOutput = (value and 0x80) != 0
        // Bits 0–1 of control specify the base nametable address.
        // These are copied into bits 10–11 of tempAddr as part of the scroll setup.
        // This allows fine X/Y scrolling and rendering to use the correct nametable region.
        tempAddr = (tempAddr and 0xF3FF) or ((value and 0x03) shl 10)
    }

    private fun writeMask(value: Int) {
        mask = value
    }

    private fun writeOamAddr(value: Int) {
        oamAddr = value.to8bits()
    }

    private fun writeOamData(value: Int) {
        oamRam[oamAddr] = value.toByte()
        oamAddr = (oamAddr + 1).to8bits()
    }

    private fun writeScroll(value: Int) {
        if (!writeToggle) {
            // First write: horizontal scroll
            fineXScroll = value and 0x07
            tempAddr = (tempAddr and 0xFFE0) or (value shr 3)
        } else {
            // Second write: vertical scroll
            tempAddr = (tempAddr and 0x8FFF) or ((value and 0x07) shl 12)     // fine Y
            tempAddr = (tempAddr and 0xFC1F) or ((value and 0xF8) shl 2)      // coarse Y
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
                nametableRam[mirrorNametableAddr(vramAddr)] = value.toByte()
            }

            in PALETTE_START..0x3FFF -> {
                val mirrored = mirrorPaletteAddr(vramAddr)
                paletteRam[mirrored] = value.toByte()
            }

            else -> {
                // TODO: Pattern table ($0000–$1FFF) — CHR-ROM is read-only for NROM; ignore writes.
            }
        }

        vramAddr = (vramAddr + vramIncrement()) and 0x3FFF
    }

    private fun vramIncrement() = if ((control and 0x04) != 0) 32 else 1

    private fun mirrorNametableAddr(addr: Int): Int {
        val a = (addr - NAMETABLE_START) and 0x0FFF
        return when (mirroring) {
            // Horizontal: $2000/$2400 → bank A, $2800/$2C00 → bank B
            MirroringMode.HORIZONTAL -> (a / 0x800) * 0x400 + a % 0x400
            // Vertical: $2000/$2800 → bank A, $2400/$2C00 → bank B
            MirroringMode.VERTICAL -> a % 0x800
        }
    }

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

    fun oamDma(data: ByteArray) {
        for (i in 0 until 256) {
            oamRam[(oamAddr + i) and 0xFF] = data[i]
        }
    }

    fun currentFrame() = framebuffer

    private fun getPalette(paletteIndex: Int): IntArray {
        require(paletteIndex in 0..3) { "Invalid background palette index: $paletteIndex" }

        val universalBgColorIndex = paletteRam[0].toUByte().toInt()

        // Use correct offset into paletteRam
        val colorBase = paletteIndex * 4 + 1

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

    internal data class Sprite(
        val x: Int,
        val y: Int,
        val tileId: Int,
        val palette: Int,
        val flipH: Boolean,
        val flipV: Boolean,
        val behindBackground: Boolean,
    )

    internal fun getSprite(index: Int): Sprite {
        val base = index * 4
        return Sprite(
            x = oamRam[base + 3].toUByte().toInt(),
            y = oamRam[base].toUByte().toInt(),
            tileId = oamRam[base + 1].toUByte().toInt(),
            palette = oamRam[base + 2].toUByte().toInt() and 0x03,
            flipH = (oamRam[base + 2].toInt() and 0x40) != 0,
            flipV = (oamRam[base + 2].toInt() and 0x80) != 0,
            behindBackground = (oamRam[base + 2].toInt() and 0x20) != 0,
        )
    }

    internal fun spriteAt(x: Int, y: Int): Sprite? {
        for (i in 0 until 64) {
            val sprite = getSprite(i)
            val row = y - (sprite.y + 1)
            val col = x - sprite.x
            if (row in 0 until 8 && col in 0 until 8) {
                val colorIndex = spriteColorIndex(sprite, row, col)
                if (colorIndex != 0) return sprite
            }
        }
        return null
    }

    internal fun spriteColorIndex(sprite: Sprite, row: Int, col: Int): Int {
        val tileRow = if (sprite.flipV) 7 - row else row
        val tileCol = if (sprite.flipH) 7 - col else col
        val spritePatternOffset = if ((control and CTRL_SPRITE_PATTERN_TABLE) != 0) 256 else 0
        return tiles[spritePatternOffset + sprite.tileId][tileRow][tileCol]
    }

    private fun getSpritePalette(paletteIndex: Int): IntArray {
        val base = 0x10 + paletteIndex * 4
        return intArrayOf(
            0,
            nesPalette[paletteRam[base + 1].toUByte().toInt() and 0x3F],
            nesPalette[paletteRam[base + 2].toUByte().toInt() and 0x3F],
            nesPalette[paletteRam[base + 3].toUByte().toInt() and 0x3F],
        )
    }

    private fun setStatusFlag(flag: Int) {
        status = status or flag
    }

    private fun clearStatusFlag(flag: Int) {
        status = status and flag.inv()
    }
}
