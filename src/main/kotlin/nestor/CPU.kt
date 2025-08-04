package nestor

const val FLAG_INTERRUPT_DISABLE = 0b00000100

class CPU(
    val memory: MemoryBus,
) {
    var pc: Int = 0
    var status: Int = 0

    fun reset() {
        val lo = memory.read(0xFFFC)
        val hi = memory.read(0xFFFD)
        pc = (hi shl 8) or lo

        status = 0x24
    }

    fun step(): Int {
        val opcode = memory.read(pc)
        return decodeAndExecute(opcode).also {
            pc = (pc + 1) and 0xFFFF
        }
    }

    private fun decodeAndExecute(opcode: Int) = when (opcode) {
        0x78 -> sei()
        0xEA -> noop()
        else -> unknown(opcode)
    }

    // Set interrupt
    private fun sei() = 2.also {
        status = status or FLAG_INTERRUPT_DISABLE
    }

    // No-op
    private fun noop() = 2

    private fun unknown(opcode: Int) = 1.also {
        println("Unknown opcode: ${opcode.hex()}, ${opcode.bin()} at PC=${pc.hex()}")
    }
}
