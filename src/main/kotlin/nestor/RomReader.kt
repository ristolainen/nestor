package nestor

object RomReader {
    fun read(rom: ByteArray): INESRom {
        if (rom.size < 16) {
            error("ROM too small to contain a valid iNES header")
        }

        // Verify iNES magic number
        if (rom[0] != 0x4E.toByte() || // 'N'
            rom[1] != 0x45.toByte() || // 'E'
            rom[2] != 0x53.toByte() || // 'S'
            rom[3] != 0x1A.toByte()    // EOF marker
        ) {
            error("Invalid iNES header")
        }

        val prgSize = rom[4].toUByte().toInt() * 16 * 1024 // in bytes
        val chrSize = rom[5].toUByte().toInt() * 8 * 1024  // in bytes

        val flags6 = rom[6].toInt()
        val hasTrainer = (flags6 and 0b00000100) != 0

        var offset = 16
        if (hasTrainer) {
            offset += 512
        }

        if (rom.size < offset + prgSize + chrSize) {
            throw IllegalArgumentException("ROM file is smaller than expected based on header sizes")
        }

        val prgData = rom.copyOfRange(offset, offset + prgSize)
        val chrData = rom.copyOfRange(offset + prgSize, offset + prgSize + chrSize)

        return INESRom(
            header = INESRom.Header(
                prgSize = prgSize,
                chrSize = chrSize,
            ),
            prgData = prgData,
            chrData = chrData,
        )
    }
}

fun main() {
    // val rom = loadRomFile("test-roms/instr_test-v3/all_instrs.nes")
    val rom = loadRomFile("goodnes/Europe/Super Mario Bros. (E) (V1.1) [!].nes")
    val inesRom = RomReader.read(rom)
    println(inesRom.header)
}
