package nestor

import java.nio.file.Paths

fun loadRomFile(filename: String): ByteArray {
    val path = Paths.get("roms", filename).toFile()
    require(path.exists()) { "ROM file not found: $path" }
    return path.readBytes()
}
