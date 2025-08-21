package nestor

const val FLAG_CARRY                = 0b00000001
const val FLAG_ZERO                 = 0b00000010
const val FLAG_INTERRUPT_DISABLE    = 0b00000100
const val FLAG_DECIMAL              = 0b00001000
const val FLAG_OVERFLOW             = 0b01000000
const val FLAG_NEGATIVE             = 0b10000000

class CPU(
    val memory: MemoryBus,
) {
    var cycles: Long = 0
    var pc: Int = 0
    var status: Int = 0
    var a: Int = 0
    var x: Int = 0
    var y: Int = 0
    var sp: Int = 0

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
        0x10 -> bpl()
        0x30 -> bmi()
        0x50 -> bvc()
        0x70 -> bvs()
        0x78 -> sei()
        0x8D -> sdaAbsolute()
        0x90 -> bcc()
        0x9A -> txs()
        0xA0 -> ldyImmediate()
        0xA2 -> ldxImmediate()
        0xA9 -> ldaImmediate()
        0xAC -> ldyAbsolute()
        0xAD -> ldaAbsolute()
        0xAE -> ldxAbsolute()
        0xB0 -> bcs()
        0xD0 -> bne()
        0xD8 -> cld()
        0xEA -> noop()
        0xF0 -> beq()
        else -> unknown(opcode)
    }.also { cycles += it }

    // Branch if plus
    private fun bpl() = branchIf(!nSet())

    // Branch if minus
    private fun bmi() = branchIf(nSet())

    // Branch if overflow clear
    private fun bvc() = branchIf(!oSet())

    // Branch if overflow set
    private fun bvs() = branchIf(oSet())

    // Set interrupt
    private fun sei() = 2.also {
        setStatusFlag(FLAG_INTERRUPT_DISABLE)
    }

    // Store accumulator absolute addressing
    private fun sdaAbsolute() = 4.also {
        val address = readNextWord()
        memory.write(address, a)
    }

    // Branch if carry clear
    private fun bcc() = branchIf(!cSet())

    // Transfer X to stack pointer
    private fun txs() = 2.also {
        sp = x and 0xFF
    }

    // Load X immediate
    private fun ldxImmediate() = 2.also {
        x = readNextByte()
        setZN(x)
    }

    // Load Y immediate
    private fun ldyImmediate() = 2.also {
        y = readNextByte()
        setZN(y)
    }

    // Load accumulator immediate
    private fun ldaImmediate() = 2.also {
        a = readNextByte()
        setZN(a)
    }

    // Load accumulator absolute
    private fun ldaAbsolute() = 4.also {
        val address = readNextWord()
        a = memory.read(address)
        setZN(a)
    }

    // Load X absolute
    private fun ldxAbsolute() = 4.also {
        val address = readNextWord()
        x = memory.read(address)
        setZN(x)
    }

    // Branch if carry set
    private fun bcs() = branchIf(cSet())

    // Branch if not equal
    private fun bne() = branchIf(!zSet())

    // Load Y absolute
    private fun ldyAbsolute() = 4.also {
        val address = readNextWord()
        y = memory.read(address)
        setZN(y)
    }

    // Clear decimal
    private fun cld() = 2.also {
        clearStatusFlag(FLAG_DECIMAL)
    }

    // No-op
    private fun noop() = 2

    // Branch if equal
    private fun beq() = branchIf(zSet())

    private fun unknown(opcode: Int) = 1.also {
        println("Unknown opcode: ${opcode.hex()}, ${opcode.bin()} at PC=${pc.hex()}")
    }

    // Update Zero/Negative for any 8-bit value
    private fun setZN(value: Int) {
        if ((value and 0xFF) == 0) setStatusFlag(FLAG_ZERO) else clearStatusFlag(FLAG_ZERO)
        if ((value and 0x80) != 0) setStatusFlag(FLAG_NEGATIVE) else clearStatusFlag(FLAG_NEGATIVE)
    }

    private fun setStatusFlag(flag: Int) {
        status = status or flag
    }

    private fun clearStatusFlag(flag: Int) {
        status = status and flag.inv()
    }

    private fun branchIf(condition: Boolean): Int {
        val offset = readNextByte().toByte().toInt()
        var cycles = 2

        if (condition) {
            val target = (pc + offset) and 0xFFFF
            cycles += 1
            if ((pc and 0xFF00) != (target and 0xFF00)) cycles += 1
            pc = target
        }
        return cycles
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

    private fun nSet() = (status and FLAG_NEGATIVE) != 0
    private fun zSet() = (status and FLAG_ZERO) != 0
    private fun cSet() = (status and FLAG_CARRY) != 0
    private fun oSet() = (status and FLAG_OVERFLOW) != 0
}
