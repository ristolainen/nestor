package nestor

import java.nio.file.Paths

fun Int.hex() = toUInt().toString(16).uppercase()

fun loadRomFile(filename: String): ByteArray {
    val path = Paths.get("roms", filename).toFile()
    require(path.exists()) { "ROM file not found: $path" }
    return path.readBytes()
}
