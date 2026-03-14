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
        val byte = readNextByte()
        val opcode = Opcode.fromByte(byte) ?: return unknown(byte).also { cycles += it }
        return decodeAndExecute(opcode)
    }

    fun pollNmi() {
        if (memory.ppu.nmiOccurred && memory.ppu.nmiOutput) {
            triggerNmi()
            memory.ppu.nmiOccurred = false // acknowledge
        }
    }

    private fun triggerNmi() {
        push(pc.highByte())
        push(pc.lowByte())
        push(status and 0xEF)
        setStatusFlag(FLAG_INTERRUPT_DISABLE)
        val lo = memory.read(0xFFFA)
        val hi = memory.read(0xFFFB)
        pc = word(lo, hi)
    }

    private fun decodeAndExecute(opcode: Opcode) = when (opcode) {
        // LDA
        Opcode.LDA_IMM -> ldaImmediate()
        Opcode.LDA_ABS -> ldaAbsolute()
        Opcode.LDA_ABX -> ldaAbsoluteX()
        Opcode.LDA_ABY -> ldaAbsoluteY()
        Opcode.LDA_INX -> ldaIndirectX()
        Opcode.LDA_INY -> ldaIndirectY()
        // LDX
        Opcode.LDX_IMM -> ldxImmediate()
        Opcode.LDX_ABS -> ldxAbsolute()
        Opcode.LDX_ABY -> ldxAbsoluteY()
        // LDY
        Opcode.LDY_IMM -> ldyImmediate()
        Opcode.LDY_ABS -> ldyAbsolute()
        // STA
        Opcode.STA_ZP  -> staZeroPage()
        Opcode.STA_ZPX -> staZeroPageX()
        Opcode.STA_ABS -> staAbsolute()
        Opcode.STA_ABX -> staAbsoluteX()
        Opcode.STA_ABY -> staAbsoluteY()
        Opcode.STA_INX -> staIndirectX()
        Opcode.STA_INY -> staIndirectY()
        // STX
        Opcode.STX_ZP  -> stxZeroPage()
        Opcode.STX_ZPY -> stxZeroPageY()
        // STY
        Opcode.STY_ZP  -> styZeroPage()
        Opcode.STY_ZPX -> styZeroPageX()
        // Transfer
        Opcode.TAX -> tax()
        Opcode.TXA -> txa()
        Opcode.TYA -> tya()
        Opcode.TXS -> txs()
        // Stack
        Opcode.PHA -> pha()
        Opcode.PLA -> pla()
        // AND
        Opcode.AND_IMM -> andImmediate()
        Opcode.AND_ZP  -> andZeroPage()
        Opcode.AND_ZPX -> andZeroPageX()
        Opcode.AND_ABS -> andAbsolute()
        Opcode.AND_ABX -> andAbsoluteX()
        Opcode.AND_ABY -> andAbsoluteY()
        Opcode.AND_INX -> andIndirectX()
        Opcode.AND_INY -> andIndirectY()
        // ORA
        Opcode.ORA_IMM -> oraImmediate()
        Opcode.ORA_ZP  -> oraZeroPage()
        Opcode.ORA_ZPX -> oraZeroPageX()
        Opcode.ORA_ABS -> oraAbsolute()
        Opcode.ORA_ABX -> oraAbsoluteX()
        Opcode.ORA_ABY -> oraAbsoluteY()
        Opcode.ORA_INX -> oraIndirectX()
        Opcode.ORA_INY -> oraIndirectY()
        // BIT
        Opcode.BIT_ZP  -> bitZeroPage()
        Opcode.BIT_ABS -> bitAbsolute()
        // Shift
        Opcode.LSR_ACC -> lsrAccumulator()
        // Compare
        Opcode.CMP_IMM -> cmpImmediate()
        Opcode.CPX_IMM -> cpxImmediate()
        Opcode.CPY_IMM -> cpyImmediate()
        // Branch
        Opcode.BPL -> bpl()
        Opcode.BMI -> bmi()
        Opcode.BVC -> bvc()
        Opcode.BVS -> bvs()
        Opcode.BCC -> bcc()
        Opcode.BCS -> bcs()
        Opcode.BNE -> bne()
        Opcode.BEQ -> beq()
        // INC/DEC
        Opcode.INC_ZP  -> incZeroPage()
        Opcode.INC_ZPX -> incZeroPageX()
        Opcode.INC_ABS -> incAbsolute()
        Opcode.INC_ABX -> incAbsoluteX()
        Opcode.INX -> inx()
        Opcode.INY -> iny()
        Opcode.DEX -> dex()
        Opcode.DEY -> dey()
        // Jump / call
        Opcode.JMP_ABS -> jmpAbsolute()
        Opcode.JMP_IND -> jmpIndirect()
        Opcode.JSR -> jsr()
        Opcode.RTS -> rts()
        // Flags
        Opcode.SEI -> sei()
        Opcode.CLD -> cld()
        // Misc
        Opcode.NOP -> noop()
        else -> unknown(opcode.byte)
    }.also { cycles += it }

    // ── Addressing mode helpers ───────────────────────────────────────────────

    // Effective address resolvers — return the address, no memory read.
    // Use these for write and read-modify-write instructions.
    private fun addrZeroPage() = readNextByte()
    private fun addrZeroPageI(index: Int) = (readNextByte() + index).to8bits()
    private fun addrAbsolute() = readNextWord()
    private fun addrAbsoluteI(index: Int) = (readNextWord() + index).to16bits()

    private fun addrIndirectX(): Int {
        val zpAddr = (readNextByte() + x).to8bits()
        val lo = memory.read(zpAddr)
        val hi = memory.read((zpAddr + 1).to8bits())
        return word(lo, hi)
    }

    // Returns (effectiveAddr, baseAddr). The base is needed by readIndirectY()
    // to detect page crossing; write instructions can ignore it with `_`.
    private fun addrIndirectY(): Pair<Int, Int> {
        val zpAddr = readNextByte()
        val lo = memory.read(zpAddr)
        val hi = memory.read((zpAddr + 1).to8bits())
        val base = word(lo, hi)
        return (base + y).to16bits() to base
    }

    // Page-crossing value readers — return (value, extraCycles).
    // Use these for read-only instructions on absolute-indexed and indirect-Y modes.
    private fun readAbsoluteI(index: Int): Pair<Int, Int> {
        val base = addrAbsolute()
        val addr = (base + index).to16bits()
        return memory.read(addr) to crossPageCycles(base, addr)
    }

    private fun readIndirectY(): Pair<Int, Int> {
        val (addr, base) = addrIndirectY()
        return memory.read(addr) to crossPageCycles(base, addr)
    }

    // ── Instructions ─────────────────────────────────────────────────────────

    // LDA
    private fun ldaImmediate() = 2.also {
        a = readNextByte()
        setZN(a)
    }

    private fun ldaAbsolute() = 4.also {
        a = memory.read(addrAbsolute())
        setZN(a)
    }

    private fun ldaAbsoluteX(): Int {
        val (v, extra) = readAbsoluteI(x)
        a = v
        setZN(a)
        return 4 + extra
    }

    private fun ldaAbsoluteY(): Int {
        val (v, extra) = readAbsoluteI(y)
        a = v
        setZN(a)
        return 4 + extra
    }

    private fun ldaIndirectX() = 6.also {
        a = memory.read(addrIndirectX())
        setZN(a)
    }

    private fun ldaIndirectY(): Int {
        val (v, extra) = readIndirectY()
        a = v
        setZN(a)
        return 5 + extra
    }

    // LDX
    private fun ldxImmediate() = 2.also {
        x = readNextByte()
        setZN(x)
    }

    private fun ldxAbsolute() = 4.also {
        x = memory.read(addrAbsolute())
        setZN(x)
    }

    private fun ldxAbsoluteY(): Int {
        val (v, extra) = readAbsoluteI(y)
        x = v
        setZN(x)
        return 4 + extra
    }

    // LDY
    private fun ldyImmediate() = 2.also {
        y = readNextByte()
        setZN(y)
    }

    private fun ldyAbsolute() = 4.also {
        y = memory.read(addrAbsolute())
        setZN(y)
    }

    // STA
    private fun staZeroPage()  = 3.also { memory.write(addrZeroPage(), a) }
    private fun staZeroPageX() = 4.also { memory.write(addrZeroPageI(x), a) }
    private fun staAbsolute()  = 4.also { memory.write(addrAbsolute(), a) }
    private fun staAbsoluteX() = 5.also { memory.write(addrAbsoluteI(x), a) }
    private fun staAbsoluteY() = 5.also { memory.write(addrAbsoluteI(y), a) }
    private fun staIndirectX() = 6.also { memory.write(addrIndirectX(), a) }

    private fun staIndirectY() = 6.also {
        val (addr, _) = addrIndirectY()
        memory.write(addr, a)
    }

    // STX
    private fun stxZeroPage()  = 3.also { memory.write(addrZeroPage(), x) }
    private fun stxZeroPageY() = 4.also { memory.write(addrZeroPageI(y), x) }

    // STY
    private fun styZeroPage()  = 3.also { memory.write(addrZeroPage(), y) }
    private fun styZeroPageX() = 4.also { memory.write(addrZeroPageI(x), y) }

    // Transfer
    private fun tax() = 2.also {
        x = a
        setZN(x)
    }

    private fun txa() = 2.also {
        a = x
        setZN(a)
    }

    private fun tya() = 2.also {
        a = y
        setZN(a)
    }

    private fun txs() = 2.also { sp = x }

    // Stack
    private fun pha() = 3.also { push(a) }

    private fun pla() = 4.also {
        a = pull()
        setZN(a)
    }

    // AND
    private fun andImmediate() = 2.also {
        a = a and readNextByte()
        setZN(a)
    }

    private fun andZeroPage() = 3.also {
        a = a and memory.read(addrZeroPage())
        setZN(a)
    }

    private fun andZeroPageX() = 4.also {
        a = a and memory.read(addrZeroPageI(x))
        setZN(a)
    }

    private fun andAbsolute() = 4.also {
        a = a and memory.read(addrAbsolute())
        setZN(a)
    }

    private fun andAbsoluteX(): Int {
        val (v, extra) = readAbsoluteI(x)
        a = a and v
        setZN(a)
        return 4 + extra
    }

    private fun andAbsoluteY(): Int {
        val (v, extra) = readAbsoluteI(y)
        a = a and v
        setZN(a)
        return 4 + extra
    }

    private fun andIndirectX() = 6.also {
        a = a and memory.read(addrIndirectX())
        setZN(a)
    }

    private fun andIndirectY(): Int {
        val (v, extra) = readIndirectY()
        a = a and v
        setZN(a)
        return 5 + extra
    }

    // ORA
    private fun oraImmediate() = 2.also {
        a = a or readNextByte()
        setZN(a)
    }

    private fun oraZeroPage() = 3.also {
        a = a or memory.read(addrZeroPage())
        setZN(a)
    }

    private fun oraZeroPageX() = 4.also {
        a = a or memory.read(addrZeroPageI(x))
        setZN(a)
    }

    private fun oraAbsolute() = 4.also {
        a = a or memory.read(addrAbsolute())
        setZN(a)
    }

    private fun oraAbsoluteX(): Int {
        val (v, extra) = readAbsoluteI(x)
        a = a or v
        setZN(a)
        return 4 + extra
    }

    private fun oraAbsoluteY(): Int {
        val (v, extra) = readAbsoluteI(y)
        a = a or v
        setZN(a)
        return 4 + extra
    }

    private fun oraIndirectX() = 6.also {
        a = a or memory.read(addrIndirectX())
        setZN(a)
    }

    private fun oraIndirectY(): Int {
        val (v, extra) = readIndirectY()
        a = a or v
        setZN(a)
        return 5 + extra
    }

    // BIT
    private fun bitZeroPage() = 3.also { bitTest(addrZeroPage()) }
    private fun bitAbsolute()  = 4.also { bitTest(addrAbsolute()) }

    private fun bitTest(addr: Int) {
        val m = memory.read(addr)
        setFlag((a and m) == 0, FLAG_ZERO)
        setFlag((m and 0x80) != 0, FLAG_NEGATIVE)
        setFlag((m and 0x40) != 0, FLAG_OVERFLOW)
    }

    // Shift
    private fun lsrAccumulator() = 2.also {
        val old = a
        val carry = old and 0x01
        val shifted = (old ushr 1) and 0xFF
        a = shifted
        setFlag(carry != 0, FLAG_CARRY)
        setFlag(shifted == 0, FLAG_ZERO)
        clearStatusFlag(FLAG_NEGATIVE)
    }

    // Compare
    private fun cmpImmediate() = cmImmediate(a)
    private fun cpxImmediate() = cmImmediate(x)
    private fun cpyImmediate() = cmImmediate(y)

    private fun cmImmediate(reg: Int) = 2.also {
        val v = readNextByte()
        val diff = (reg - v).to8bits()
        setFlag(reg >= v, FLAG_CARRY)
        setFlag(diff == 0, FLAG_ZERO)
        setFlag((diff and 0x80) != 0, FLAG_NEGATIVE)
    }

    // Branch
    private fun bpl() = branchIf(!nSet())
    private fun bmi() = branchIf(nSet())
    private fun bvc() = branchIf(!oSet())
    private fun bvs() = branchIf(oSet())
    private fun bcc() = branchIf(!cSet())
    private fun bcs() = branchIf(cSet())
    private fun bne() = branchIf(!zSet())
    private fun beq() = branchIf(zSet())

    // INC/DEC
    private fun incZeroPage()  = 5.also { incrementMemory(addrZeroPage()) }
    private fun incZeroPageX() = 6.also { incrementMemory(addrZeroPageI(x)) }
    private fun incAbsolute()  = 6.also { incrementMemory(addrAbsolute()) }
    private fun incAbsoluteX() = 7.also { incrementMemory(addrAbsoluteI(x)) }

    private fun incrementMemory(addr: Int) {
        val v = memory.read(addr)
        memory.write(addr, v) // 6502 RMW: write original byte back before writing modified value
        val nv = (v + 1).to8bits()
        memory.write(addr, nv)
        setZN(nv)
    }

    private fun inx() = 2.also {
        x += 1
        setZN(x)
    }

    private fun iny() = 2.also {
        y += 1
        setZN(y)
    }

    private fun dex() = 2.also {
        x -= 1
        setZN(x)
    }

    private fun dey() = 2.also {
        y -= 1
        setZN(y)
    }

    // Jump / call
    private fun jmpAbsolute() = 3.also { pc = addrAbsolute() }

    private fun jmpIndirect() = 5.also {
        val ptr = addrAbsolute()
        val lo = memory.read(ptr)
        // Hardware bug: high byte wraps within the same page when low byte is 0xFF
        val hiAddr = (ptr and 0xFF00) or ((ptr + 1) and 0x00FF)
        val hi = memory.read(hiAddr)
        pc = word(lo, hi)
    }

    private fun jsr() = 6.also {
        val target = readNextWord()
        val ret = (pc - 1).to16bits()
        push(ret.highByte())
        push(ret.lowByte())
        pc = target
    }

    private fun rts() = 6.also {
        val lo = pull()
        val hi = pull()
        pc = word(lo, hi) + 1
    }

    // Flags
    private fun sei() = 2.also { setStatusFlag(FLAG_INTERRUPT_DISABLE) }
    private fun cld() = 2.also { clearStatusFlag(FLAG_DECIMAL) }

    // Misc
    private fun noop() = 2

    private fun unknown(opcode: Int) = 1.also {
        println("Unknown opcode: ${opcode.hex()}, ${opcode.bin()} at PC=${pc.hex()}")
        abort = true
    }

    // ── Flag helpers ─────────────────────────────────────────────────────────

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

    // ── Branch helper ─────────────────────────────────────────────────────────

    private fun branchIf(condition: Boolean): Int {
        val offset = readNextByte().toByte().toInt()
        if (!condition) return 2
        val target = (pc + offset).to16bits()
        val extra = 1 + crossPageCycles(pc, target)
        pc = target
        return 2 + extra
    }

    // ── Memory helpers ────────────────────────────────────────────────────────

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
