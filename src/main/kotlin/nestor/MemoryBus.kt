package nestor

class MemoryBus(
    private val ppu: PPU,
    private val prgRom: ByteArray,
    private val cpuRam: ByteArray = ByteArray(0x0800),
) {
    fun read(address: Int): Int = when (address) {
        in 0x0000..0x1FFF -> {
            // 2KB internal RAM, mirrored every 2KB
            val mirroredAddr = address % 0x0800
            cpuRam[mirroredAddr].toUByte().toInt()
        }

        in 0x2000..0x3FFF -> {
            // PPU registers, mirrored every 8 bytes
            val mirroredAddr = 0x2000 + (address % 8)
            ppu.cpuRead(mirroredAddr)
        }

        in 0x8000..0xFFFF -> {
            // PRG-ROM (assume 16KB mirrored or 32KB flat, depending on mapper)
            val mappedAddr = if (prgRom.size == 0x4000) address % 0x4000 else address - 0x8000
            prgRom[mappedAddr].toUByte().toInt()
        }

        else -> {
            // For now, treat unimplemented areas as zero
            0
        }
    }

    fun write(address: Int, value: Int) {
        when (address) {
            in 0x0000..0x1FFF -> {
                val mirroredAddr = address % 0x0800
                cpuRam[mirroredAddr] = value.toByte()
            }

            in 0x2000..0x3FFF -> {
                val mirroredAddr = 0x2000 + (address % 8)
                ppu.cpuWrite(mirroredAddr, value)
            }

            in 0x8000..0xFFFF -> {
                // Typically PRG-ROM is read-only; ignore writes
            }

            else -> {
                // Ignore writes to unimplemented areas for now
            }
        }
    }
}
