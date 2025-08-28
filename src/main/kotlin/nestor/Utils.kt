package nestor

import java.nio.file.Paths

fun Int.hex() = toUInt().toString(16).uppercase()

fun Int.bin() = toUInt().toString(2).padStart(8, '0')

fun Int.to8bits() = this and 0xFF

fun Int.to16bits() = this and 0xFFFF

fun Int.lowByte() = this and 0xFF

fun Int.highByte() = (this ushr 8) and 0xFF

fun word(lo: Int, hi: Int) = (hi shl 8) or lo

fun loadRomFile(filename: String): ByteArray {
    val path = Paths.get("roms", filename).toFile()
    require(path.exists()) { "ROM file not found: $path" }
    return path.readBytes()
}
