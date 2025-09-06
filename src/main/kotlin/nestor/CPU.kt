package nestor

const val FLAG_CARRY = 0b00000001
const val FLAG_ZERO = 0b00000010
const val FLAG_INTERRUPT_DISABLE = 0b00000100
const val FLAG_DECIMAL = 0b00001000
const val FLAG_OVERFLOW = 0b01000000
const val FLAG_NEGATIVE = 0b10000000

class CPU(
    val memory: MemoryBus,
) {
    var cycles: Long = 0

    var status: Int = 0
        set(value) {
            field = value and 0xFF
        }

    var a: Int = 0
        set(value) {
            field = value and 0xFF
        }

    var x: Int = 0
        set(value) {
            field = value and 0xFF
        }

    var y: Int = 0
        set(value) {
            field = value and 0xFF
        }

    var sp: Int = 0
        set(value) {
            field = value and 0xFF
        }

    var pc: Int = 0
        set(value) {
            field = value and 0xFFFF
        }

    var abort = false

    fun reset() {
        val lo = memory.read(0xFFFC)
        val hi = memory.read(0xFFFD)
        pc = word(lo, hi)

        status = 0x24
    }

    fun step(): Int {
        val opcode = readNextByte()
        return decodeAndExecute(opcode)
    }

    private fun decodeAndExecute(opcode: Int) = when (opcode) {
        0x01 -> oraIndirectX()
        0x05 -> oraZeroPage()
        0x09 -> oraImmediate()
        0x0D -> oraAbsolute()
        0x10 -> bpl()
        0x11 -> oraIndirectY()
        0x15 -> oraZeroPageX()
        0x19 -> oraAbsoluteY()
        0x1D -> oraAbsoluteX()
        0x20 -> jsr()
        0x21 -> andIndirectX()
        0x24 -> bitZeroPage()
        0x25 -> andZeroPage()
        0x29 -> andImmediate()
        0x2C -> bitAbsolute()
        0x2D -> andAbsolute()
        0x30 -> bmi()
        0x31 -> andIndirectY()
        0x35 -> andZeroPageX()
        0x39 -> andAbsoluteY()
        0x3D -> andAbsoluteX()
        0x50 -> bvc()
        0x60 -> rts()
        0x70 -> bvs()
        0x78 -> sei()
        0x81 -> staIndirectX()
        0x84 -> styZeroPage()
        0x85 -> staZeroPage()
        0x86 -> stxZeroPage()
        0x88 -> dey()
        0x8D -> sdaAbsolute()
        0x90 -> bcc()
        0x91 -> staIndirectY()
        0x94 -> styZeroPageX()
        0x95 -> staZeroPageX()
        0x96 -> stxZeroPageY()
        0x99 -> staAbsoluteY()
        0x9A -> txs()
        0x9D -> staAbsoluteX()
        0xA0 -> ldyImmediate()
        0xA2 -> ldxImmediate()
        0xA9 -> ldaImmediate()
        0xAC -> ldyAbsolute()
        0xAD -> ldaAbsolute()
        0xAE -> ldxAbsolute()
        0xB0 -> bcs()
        0xB9 -> ldaAbsoluteY()
        0xBD -> ldaAbsoluteX()
        0xC0 -> cpyImmediate()
        0xC8 -> iny()
        0xC9 -> cmpImmediate()
        0xCA -> dex()
        0xD0 -> bne()
        0xD8 -> cld()
        0xE0 -> cpxImmediate()
        0xE8 -> inx()
        0xEA -> noop()
        0xF0 -> beq()
        else -> unknown(opcode)
    }.also { cycles += it }

    // Bitwise OR immediate
    private fun oraImmediate() = 2.also {
        val v = readNextByte()
        a = a or v
        setZN(a)
    }

    // Bitwise OR zero page
    private fun oraZeroPage() = 3.also {
        val addr = readNextByte()
        val v = memory.read(addr)
        a = a or v
        setZN(a)
    }

    // Bitwise OR zero page X
    private fun oraZeroPageX() = 4.also {
        val base = readNextByte()
        val addr = (base + x).to8bits()
        val v = memory.read(addr)
        a = a or v
        setZN(a)
    }

    // Bitwise OR absolute
    private fun oraAbsolute() = 4.also {
        val addr = readNextWord()
        val v = memory.read(addr)
        a = a or v
        setZN(a)
    }

    // Bitwise OR absolute X
    private fun oraAbsoluteX() = oraAbsoluteI(x)

    // Bitwise OR absolute Y
    private fun oraAbsoluteY() = oraAbsoluteI(y)

    // Bitwise OR absolute indexes
    private fun oraAbsoluteI(i: Int): Int {
        val base = readNextWord()
        val addr = (base + i).to16bits()
        val v = memory.read(addr)
        a = a or v
        setZN(a)
        return 4 + crossPageCycles(base, addr)
    }

    // Bitwise OR indirect X
    private fun oraIndirectX() = 6.also {
        val base = readNextByte()
        val zpAddr = (base + x).to8bits()
        val lo = memory.read(zpAddr)
        val hi = memory.read((zpAddr + 1).to8bits())
        val addr = word(lo, hi)
        val v = memory.read(addr)
        a = a or v
        setZN(a)
    }

    // Bitwise OR indirect Y
    private fun oraIndirectY(): Int {
        val zpAddr = readNextByte()
        val lo = memory.read(zpAddr)
        val hi = memory.read((zpAddr + 1).to8bits())
        val base = word(lo, hi)
        val addr = (base + y).to16bits()
        val v = memory.read(addr)
        a = a or v
        setZN(a)
        return 5 + crossPageCycles(base, addr)
    }

    // Bitwise AND immediate
    private fun andImmediate() = 2.also {
        val v = readNextByte()
        a = a and v
        setZN(a)
    }

    // Bitwise AND zero page
    private fun andZeroPage() = 3.also {
        val addr = readNextByte()
        val v = memory.read(addr)
        a = a and v
        setZN(a)
    }

    // Bitwise AND zero page X
    private fun andZeroPageX() = 4.also {
        val base = readNextByte()
        val addr = (base + x).to8bits()
        val v = memory.read(addr)
        a = a and v
        setZN(a)
    }

    // Bitwise AND absolute
    private fun andAbsolute() = 4.also {
        val addr = readNextWord()
        val v = memory.read(addr)
        a = a and v
        setZN(a)
    }

    // Bitwise AND absolute X
    private fun andAbsoluteX() = andAbsoluteI(x)

    // Bitwise AND absolute Y
    private fun andAbsoluteY() = andAbsoluteI(y)

    // Bitwise OR absolute indexes
    private fun andAbsoluteI(i: Int): Int {
        val base = readNextWord()
        val addr = (base + i).to16bits()
        val v = memory.read(addr)
        a = a and v
        setZN(a)
        return 4 + crossPageCycles(base, addr)
    }

    // Bitwise AND indirect X
    private fun andIndirectX() = 6.also {
        val base = readNextByte()
        val zpAddr = (base + x).to8bits()
        val lo = memory.read(zpAddr)
        val hi = memory.read((zpAddr + 1).to8bits())
        val addr = word(lo, hi)
        val v = memory.read(addr)
        a = a and v
        setZN(a)
    }

    // Bitwise AND indirect Y
    private fun andIndirectY(): Int {
        val zpAddr = readNextByte()
        val lo = memory.read(zpAddr)
        val hi = memory.read((zpAddr + 1).to8bits())
        val base = word(lo, hi)
        val addr = (base + y).to16bits()
        val v = memory.read(addr)
        a = a and v
        setZN(a)
        return 5 + crossPageCycles(base, addr)
    }

    // Branch if plus
    private fun bpl() = branchIf(!nSet())

    // Jump to Subroutine
    private fun jsr() = 6.also {
        val target = readNextWord()
        val ret = (pc - 1).to16bits()
        push(ret.highByte())
        push(ret.lowByte())
        pc = target
    }

    // Bit test zero page
    private fun bitZeroPage() = 3.also {
        bitTest(readNextByte())
    }

    // Bit test absolute
    private fun bitAbsolute() = 4.also {
        bitTest(readNextWord())
    }

    private fun bitTest(addr: Int) {
        val m = memory.read(addr)
        setFlag((a and m) == 0, FLAG_ZERO)
        setFlag((m and 0x80) != 0, FLAG_NEGATIVE)
        setFlag((m and 0x40) != 0, FLAG_OVERFLOW)
    }

    // Branch if minus
    private fun bmi() = branchIf(nSet())

    // Branch if overflow clear
    private fun bvc() = branchIf(!oSet())

    // Return from subroutine
    private fun rts() = 6.also {
        val lo = pull()
        val hi = pull()
        val ret = word(lo, hi)
        pc = ret + 1
    }

    // Branch if overflow set
    private fun bvs() = branchIf(oSet())

    // Set interrupt
    private fun sei() = 2.also {
        setStatusFlag(FLAG_INTERRUPT_DISABLE)
    }

    // Store accumulator zero page addressing
    private fun staZeroPage() = 3.also { stZeroPage(a, 0) }

    // Store accumulator with X zero page addressing
    private fun staZeroPageX() = 4.also { stZeroPage(a, x) }

    // Store X zero page addressing
    private fun stxZeroPage() = 3.also { stZeroPage(x, 0) }

    // Store X with Y zero page addressing
    private fun stxZeroPageY() = 4.also { stZeroPage(x, y) }

    // Store A absolute X
    private fun staAbsoluteX() = staAbsoluteI(x)

    // Store A absolute Y
    private fun staAbsoluteY() = staAbsoluteI(y)

    private fun staAbsoluteI(i: Int) = 5.also {
        val addr = (readNextWord() + i).to16bits()
        memory.write(addr, a)
    }

    // Store Y zero page addressing
    private fun styZeroPage() = 3.also { stZeroPage(y, 0) }

    // Store Y with X zero page addressing
    private fun styZeroPageX() = 4.also { stZeroPage(y, x) }

    private fun stZeroPage(v: Int, o: Int) {
        val addr = (readNextByte() + o).to8bits()
        memory.write(addr, v)
    }

    // Store accumulator absolute addressing
    private fun sdaAbsolute() = 4.also {
        val addr = readNextWord()
        memory.write(addr, a)
    }

    // Branch if carry clear
    private fun bcc() = branchIf(!cSet())

    // Store A indirect X
    private fun staIndirectX() = 6.also {
        val zp = (readNextByte() + x).to8bits()
        val lo = memory.read(zp)
        val hi = memory.read((zp + 1).to8bits())
        val addr = word(lo, hi)
        memory.write(addr, a)
    }

    // Store A indirect Y
    private fun staIndirectY() = 6.also {
        val zp = readNextByte()
        val lo = memory.read(zp)
        val hi = memory.read((zp + 1).to8bits())
        val base = word(lo, hi)
        val addr = (base + y).to16bits()
        memory.write(addr, a)
    }

    // Transfer X to stack pointer
    private fun txs() = 2.also {
        sp = x
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

    // Load A absolute X
    private fun ldaAbsoluteX() = ldaAbsoluteI(x)

    // Load A absolute Y
    private fun ldaAbsoluteY() = ldaAbsoluteI(y)

    private fun ldaAbsoluteI(i: Int): Int {
        val base = readNextWord()
        val address = (base + i).to16bits()
        a = memory.read(address)
        setZN(a)
        return 4 + crossPageCycles(base, address)
    }

    // Compare A immediate
    private fun cmpImmediate() = cmImmediate(a)

    // Compare X immediate
    private fun cpxImmediate() = cmImmediate(x)

    // Compare Y immediate
    private fun cpyImmediate() = cmImmediate(y)

    private fun cmImmediate(reg: Int) = 2.also {
        val v = readNextByte()
        val diff = (reg - v).to8bits()
        setFlag(reg >= v, FLAG_CARRY)
        setFlag(diff == 0, FLAG_ZERO)
        setFlag((diff and 0x80) != 0, FLAG_NEGATIVE)
    }

    // Decrement X
    private fun dex() = 2.also {
        x -= 1
        setZN(x)
    }

    // Decrement Y
    private fun dey() = 2.also {
        y -= 1
        setZN(y)
    }

    // Increment X
    private fun inx() = 2.also {
        x += 1
        setZN(x)
    }

    // Increment Y
    private fun iny() = 2.also {
        y += 1
        setZN(y)
    }

    // Branch if not equal
    private fun bne() = branchIf(!zSet())

    // Load Y absolute
    private fun ldyAbsolute() = 4.also {
        val addr = readNextWord()
        y = memory.read(addr)
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
        abort = true
    }

    // Update Zero/Negative for any 8-bit value
    private fun setZN(value: Int) {
        setZ(value)
        setN(value)
    }

    private fun setZ(value: Int) {
        setFlag((value and 0xFF) == 0, FLAG_ZERO)
    }

    private fun setN(value: Int) {
        setFlag((value and 0x80) != 0, FLAG_NEGATIVE)
    }

    private fun setO(value: Int) {
        setFlag((value and 0x40) != 0, FLAG_OVERFLOW)
    }

    private fun setFlag(test: Boolean, flag: Int) {
        if (test) setStatusFlag(flag) else clearStatusFlag(flag)
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
            val target = (pc + offset).to16bits()
            cycles += 1 + crossPageCycles(pc, target)
            pc = target
        }
        return cycles
    }

    private fun readNextByte(): Int {
        val value = memory.read(pc)
        pc = (pc + 1).to16bits()
        return value
    }

    private fun readNextWord(): Int {
        val lo = readNextByte()
        val hi = readNextByte()
        return word(lo, hi)
    }

    private fun push(v: Int) {
        memory.write(0x0100 + sp, v.to8bits())
        sp -= 1
    }

    private fun pull(): Int {
        sp += 1
        return memory.read(0x0100 + sp)
    }

    private fun crossedPage(a: Int, b: Int) = a.highByte() != b.highByte()

    private fun crossPageCycles(a: Int, b: Int) =
        if (crossedPage(a, b)) 1 else 0

    private fun nSet() = (status and FLAG_NEGATIVE) != 0
    private fun zSet() = (status and FLAG_ZERO) != 0
    private fun cSet() = (status and FLAG_CARRY) != 0
    private fun oSet() = (status and FLAG_OVERFLOW) != 0
}
