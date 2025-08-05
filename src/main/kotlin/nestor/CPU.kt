package nestor

const val FLAG_CARRY                = 0b00000001
const val FLAG_ZERO                 = 0b00000010
const val FLAG_INTERRUPT_DISABLE    = 0b00000100
const val FLAG_DECIMAL              = 0b00001000
const val FLAG_NEGATIVE             = 0b01000000

class CPU(
    val memory: MemoryBus,
) {
    var pc: Int = 0
    var status: Int = 0
    var accumulator: Int = 0

    fun reset() {
        val lo = memory.read(0xFFFC)
        val hi = memory.read(0xFFFD)
        pc = (hi shl 8) or lo

        status = 0x24
    }

    fun step(): Int {
        val opcode = readNextByte()
        return decodeAndExecute(opcode)
    }

    private fun decodeAndExecute(opcode: Int) = when (opcode) {
        0x78 -> sei()
        0xA9 -> ldaImmediate()
        0xD8 -> cld()
        0xEA -> noop()
        else -> unknown(opcode)
    }

    // Load accumulator immediate
    private fun ldaImmediate() = 2.also {
        accumulator = readNextByte()
        if (accumulator == 0) {
            setStatusFlag(FLAG_ZERO)
        } else {
            clearStatusFlag(FLAG_ZERO)
        }
        if ((accumulator and 0x80) != 0) {
            setStatusFlag(FLAG_NEGATIVE)
        } else {
            clearStatusFlag(FLAG_NEGATIVE)
        }
    }

    // Set interrupt
    private fun sei() = 2.also {
        setStatusFlag(FLAG_INTERRUPT_DISABLE)
    }

    // Clear decimal
    private fun cld() = 2.also {
        clearStatusFlag(FLAG_DECIMAL)
    }

    // No-op
    private fun noop() = 2

    private fun unknown(opcode: Int) = 1.also {
        println("Unknown opcode: ${opcode.hex()}, ${opcode.bin()} at PC=${pc.hex()}")
    }

    private fun setStatusFlag(flag: Int) {
        status = status or flag
    }

    private fun clearStatusFlag(flag: Int) {
        status = status and flag.inv()
    }

    private fun readNextByte(): Int {
        val value = memory.read(pc)
        pc = (pc + 1) and 0xFFFF
        return value
    }

    private fun readNextWord(): Int {
        val lo = readNextByte()
        val hi = readNextByte()
        return (hi shl 8) or lo
    }
}
