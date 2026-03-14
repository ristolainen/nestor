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
        Opcode.STX_ABS -> stxAbsolute()
        // STY
        Opcode.STY_ZP  -> styZeroPage()
        Opcode.STY_ZPX -> styZeroPageX()
        Opcode.STY_ABS -> styAbsolute()
        // Transfer
        Opcode.TAX -> tax()
        Opcode.TAY -> tay()
        Opcode.TXA -> txa()
        Opcode.TYA -> tya()
        Opcode.TXS -> txs()
        Opcode.TSX -> tsx()
        // Stack
        Opcode.PHA -> pha()
        Opcode.PLA -> pla()
        // EOR
        Opcode.EOR_IMM -> eorImmediate()
        Opcode.EOR_ZP  -> eorZeroPage()
        Opcode.EOR_ZPX -> eorZeroPageX()
        Opcode.EOR_ABS -> eorAbsolute()
        Opcode.EOR_ABX -> eorAbsoluteX()
        Opcode.EOR_ABY -> eorAbsoluteY()
        Opcode.EOR_INX -> eorIndirectX()
        Opcode.EOR_INY -> eorIndirectY()
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
        Opcode.ASL_ACC -> aslAccumulator()
        Opcode.ASL_ZP  -> aslZeroPage()
        Opcode.ASL_ZPX -> aslZeroPageX()
        Opcode.ASL_ABS -> aslAbsolute()
        Opcode.ASL_ABX -> aslAbsoluteX()
        Opcode.LSR_ACC -> lsrAccumulator()
        Opcode.LSR_ZP  -> lsrZeroPage()
        Opcode.LSR_ZPX -> lsrZeroPageX()
        Opcode.LSR_ABS -> lsrAbsolute()
        Opcode.LSR_ABX -> lsrAbsoluteX()
        // ROR
        Opcode.ROR_ACC -> rorAccumulator()
        Opcode.ROR_ZP  -> rorZeroPage()
        Opcode.ROR_ZPX -> rorZeroPageX()
        Opcode.ROR_ABS -> rorAbsolute()
        Opcode.ROR_ABX -> rorAbsoluteX()
        // ROL
        Opcode.ROL_ACC -> rolAccumulator()
        Opcode.ROL_ZP  -> rolZeroPage()
        Opcode.ROL_ZPX -> rolZeroPageX()
        Opcode.ROL_ABS -> rolAbsolute()
        Opcode.ROL_ABX -> rolAbsoluteX()
        // ADC
        Opcode.ADC_IMM -> adcImmediate()
        Opcode.ADC_ZP  -> adcZeroPage()
        Opcode.ADC_ZPX -> adcZeroPageX()
        Opcode.ADC_ABS -> adcAbsolute()
        Opcode.ADC_ABX -> adcAbsoluteX()
        Opcode.ADC_ABY -> adcAbsoluteY()
        Opcode.ADC_INX -> adcIndirectX()
        Opcode.ADC_INY -> adcIndirectY()
        // SBC
        Opcode.SBC_IMM -> sbcImmediate()
        Opcode.SBC_ZP  -> sbcZeroPage()
        Opcode.SBC_ZPX -> sbcZeroPageX()
        Opcode.SBC_ABS -> sbcAbsolute()
        Opcode.SBC_ABX -> sbcAbsoluteX()
        Opcode.SBC_ABY -> sbcAbsoluteY()
        Opcode.SBC_INX -> sbcIndirectX()
        Opcode.SBC_INY -> sbcIndirectY()
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
        Opcode.DEC_ZP  -> decZeroPage()
        Opcode.DEC_ZPX -> decZeroPageX()
        Opcode.DEC_ABS -> decAbsolute()
        Opcode.DEC_ABX -> decAbsoluteX()
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
        Opcode.CLC -> clc()
        Opcode.CLI -> cli()
        Opcode.CLV -> clv()
        Opcode.SEC -> sec()
        Opcode.SED -> sed()
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
    private fun stxAbsolute()  = 4.also { memory.write(addrAbsolute(), x) }

    // STY
    private fun styZeroPage()  = 3.also { memory.write(addrZeroPage(), y) }
    private fun styZeroPageX() = 4.also { memory.write(addrZeroPageI(x), y) }
    private fun styAbsolute()  = 4.also { memory.write(addrAbsolute(), y) }

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

    private fun tay() = 2.also {
        y = a
        setZN(y)
    }

    private fun txs() = 2.also { sp = x }

    private fun tsx() = 2.also {
        x = sp
        setZN(x)
    }

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

    // EOR
    private fun eorImmediate() = 2.also {
        a = a xor readNextByte()
        setZN(a)
    }

    private fun eorZeroPage() = 3.also {
        a = a xor memory.read(addrZeroPage())
        setZN(a)
    }

    private fun eorZeroPageX() = 4.also {
        a = a xor memory.read(addrZeroPageI(x))
        setZN(a)
    }

    private fun eorAbsolute() = 4.also {
        a = a xor memory.read(addrAbsolute())
        setZN(a)
    }

    private fun eorAbsoluteX(): Int {
        val (v, extra) = readAbsoluteI(x)
        a = a xor v
        setZN(a)
        return 4 + extra
    }

    private fun eorAbsoluteY(): Int {
        val (v, extra) = readAbsoluteI(y)
        a = a xor v
        setZN(a)
        return 4 + extra
    }

    private fun eorIndirectX() = 6.also {
        a = a xor memory.read(addrIndirectX())
        setZN(a)
    }

    private fun eorIndirectY(): Int {
        val (v, extra) = readIndirectY()
        a = a xor v
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
    // ASL
    private fun aslAccumulator() = 2.also { a = shiftLeft(a) }
    private fun aslZeroPage()  = 5.also { shiftLeftMemory(addrZeroPage()) }
    private fun aslZeroPageX() = 6.also { shiftLeftMemory(addrZeroPageI(x)) }
    private fun aslAbsolute()  = 6.also { shiftLeftMemory(addrAbsolute()) }
    private fun aslAbsoluteX() = 7.also { shiftLeftMemory(addrAbsoluteI(x)) }

    private fun shiftLeft(v: Int): Int {
        val result = (v shl 1) and 0xFF
        setFlag((v and 0x80) != 0, FLAG_CARRY)
        setZN(result)
        return result
    }

    private fun shiftLeftMemory(addr: Int) {
        val v = memory.read(addr)
        memory.write(addr, v) // 6502 RMW: write original byte back before writing modified value
        memory.write(addr, shiftLeft(v))
    }

    // LSR
    private fun lsrAccumulator() = 2.also { a = shiftRight(a) }
    private fun lsrZeroPage()  = 5.also { shiftRightMemory(addrZeroPage()) }
    private fun lsrZeroPageX() = 6.also { shiftRightMemory(addrZeroPageI(x)) }
    private fun lsrAbsolute()  = 6.also { shiftRightMemory(addrAbsolute()) }
    private fun lsrAbsoluteX() = 7.also { shiftRightMemory(addrAbsoluteI(x)) }

    private fun shiftRight(v: Int): Int {
        val result = (v ushr 1) and 0xFF
        setFlag((v and 0x01) != 0, FLAG_CARRY)
        setZN(result)
        return result
    }

    private fun shiftRightMemory(addr: Int) {
        val v = memory.read(addr)
        memory.write(addr, v) // 6502 RMW: write original byte back before writing modified value
        memory.write(addr, shiftRight(v))
    }

    // ROR
    private fun rorAccumulator() = 2.also {
        val result = rotateRight(a)
        a = result
    }
    private fun rorZeroPage()  = 5.also { rotateRightMemory(addrZeroPage()) }
    private fun rorZeroPageX() = 6.also { rotateRightMemory(addrZeroPageI(x)) }
    private fun rorAbsolute()  = 6.also { rotateRightMemory(addrAbsolute()) }
    private fun rorAbsoluteX() = 7.also { rotateRightMemory(addrAbsoluteI(x)) }

    private fun rotateRight(v: Int): Int {
        val carryIn = if (cSet()) 0x80 else 0
        val result = ((v ushr 1) or carryIn) and 0xFF
        setFlag((v and 0x01) != 0, FLAG_CARRY)
        setZN(result)
        return result
    }

    private fun rotateRightMemory(addr: Int) {
        val v = memory.read(addr)
        memory.write(addr, v) // 6502 RMW: write original byte back before writing modified value
        memory.write(addr, rotateRight(v))
    }

    // ROL
    private fun rolAccumulator() = 2.also {
        val result = rotateLeft(a)
        a = result
    }
    private fun rolZeroPage()  = 5.also { rotateLeftMemory(addrZeroPage()) }
    private fun rolZeroPageX() = 6.also { rotateLeftMemory(addrZeroPageI(x)) }
    private fun rolAbsolute()  = 6.also { rotateLeftMemory(addrAbsolute()) }
    private fun rolAbsoluteX() = 7.also { rotateLeftMemory(addrAbsoluteI(x)) }

    private fun rotateLeft(v: Int): Int {
        val carryIn = if (cSet()) 1 else 0
        val result = ((v shl 1) or carryIn) and 0xFF
        setFlag((v and 0x80) != 0, FLAG_CARRY)
        setZN(result)
        return result
    }

    private fun rotateLeftMemory(addr: Int) {
        val v = memory.read(addr)
        memory.write(addr, v) // 6502 RMW: write original byte back before writing modified value
        memory.write(addr, rotateLeft(v))
    }

    // ADC
    private fun adcImmediate() = 2.also { adc(readNextByte()) }
    private fun adcZeroPage()  = 3.also { adc(memory.read(addrZeroPage())) }
    private fun adcZeroPageX() = 4.also { adc(memory.read(addrZeroPageI(x))) }
    private fun adcAbsolute()  = 4.also { adc(memory.read(addrAbsolute())) }

    private fun adcAbsoluteX(): Int {
        val (v, extra) = readAbsoluteI(x)
        adc(v)
        return 4 + extra
    }

    private fun adcAbsoluteY(): Int {
        val (v, extra) = readAbsoluteI(y)
        adc(v)
        return 4 + extra
    }

    private fun adcIndirectX() = 6.also { adc(memory.read(addrIndirectX())) }

    private fun adcIndirectY(): Int {
        val (v, extra) = readIndirectY()
        adc(v)
        return 5 + extra
    }

    private fun adc(m: Int) {
        val carry = if (cSet()) 1 else 0
        val result = a + m + carry
        val overflow = ((a xor result) and (m xor result) and 0x80) != 0
        a = result and 0xFF
        setFlag(result > 0xFF, FLAG_CARRY)
        setFlag(overflow, FLAG_OVERFLOW)
        setZN(a)
    }

    // SBC
    private fun sbcImmediate() = 2.also { sbc(readNextByte()) }
    private fun sbcZeroPage()  = 3.also { sbc(memory.read(addrZeroPage())) }
    private fun sbcZeroPageX() = 4.also { sbc(memory.read(addrZeroPageI(x))) }
    private fun sbcAbsolute()  = 4.also { sbc(memory.read(addrAbsolute())) }

    private fun sbcAbsoluteX(): Int {
        val (v, extra) = readAbsoluteI(x)
        sbc(v)
        return 4 + extra
    }

    private fun sbcAbsoluteY(): Int {
        val (v, extra) = readAbsoluteI(y)
        sbc(v)
        return 4 + extra
    }

    private fun sbcIndirectX() = 6.also { sbc(memory.read(addrIndirectX())) }

    private fun sbcIndirectY(): Int {
        val (v, extra) = readIndirectY()
        sbc(v)
        return 5 + extra
    }

    private fun sbc(m: Int) {
        val notM = m.inv() and 0xFF
        val carry = if (cSet()) 1 else 0
        val result = a + notM + carry
        val overflow = ((a xor result) and (notM xor result) and 0x80) != 0
        a = result and 0xFF
        setFlag(result > 0xFF, FLAG_CARRY)
        setFlag(overflow, FLAG_OVERFLOW)
        setZN(a)
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

    private fun decZeroPage()  = 5.also { decrementMemory(addrZeroPage()) }
    private fun decZeroPageX() = 6.also { decrementMemory(addrZeroPageI(x)) }
    private fun decAbsolute()  = 6.also { decrementMemory(addrAbsolute()) }
    private fun decAbsoluteX() = 7.also { decrementMemory(addrAbsoluteI(x)) }

    private fun decrementMemory(addr: Int) {
        val v = memory.read(addr)
        memory.write(addr, v) // 6502 RMW: write original byte back before writing modified value
        val nv = (v - 1).to8bits()
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
    private fun clc() = 2.also { clearStatusFlag(FLAG_CARRY) }
    private fun cli() = 2.also { clearStatusFlag(FLAG_INTERRUPT_DISABLE) }
    private fun clv() = 2.also { clearStatusFlag(FLAG_OVERFLOW) }
    private fun sec() = 2.also { setStatusFlag(FLAG_CARRY) }
    private fun sed() = 2.also { setStatusFlag(FLAG_DECIMAL) }
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
