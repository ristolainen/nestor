package nestor

class CPU(
    val memory: MemoryBus,
) {
    var pc: Int = 0

    fun reset() {
        val lo = memory.read(0xFFFC)
        val hi = memory.read(0xFFFD)
        pc = (hi shl 8) or lo
    }

    fun step(): Int {
        val opcode = memory.read(pc)
        return decodeAndExecute(opcode).also {
            pc = (pc + 1) and 0xFFFF
        }
    }

    private fun decodeAndExecute(opcode: Int) = when (opcode) {
        0xEA -> noop()
        else -> unknown(opcode)
    }

    private fun unknown(opcode: Int): Int {
        println("Unknown opcode: ${opcode.hex()}, ${opcode.bin()} at PC=${pc.hex()}")
        return 0
    }

    private fun noop(): Int = 2
}
