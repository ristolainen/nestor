package nestor

enum class AddrMode(val operandBytes: Int) {
    IMP(0), ACC(0),
    IMM(1), ZP(1), ZPX(1), ZPY(1), REL(1), INX(1), INY(1),
    ABS(2), ABX(2), ABY(2), IND(2),
}

enum class Opcode(val byte: Int, val mode: AddrMode) {
    // ADC
    ADC_IMM(0x69, AddrMode.IMM), ADC_ZP(0x65, AddrMode.ZP),   ADC_ZPX(0x75, AddrMode.ZPX),
    ADC_ABS(0x6D, AddrMode.ABS), ADC_ABX(0x7D, AddrMode.ABX), ADC_ABY(0x79, AddrMode.ABY),
    ADC_INX(0x61, AddrMode.INX), ADC_INY(0x71, AddrMode.INY),
    // AND
    AND_IMM(0x29, AddrMode.IMM), AND_ZP(0x25, AddrMode.ZP),   AND_ZPX(0x35, AddrMode.ZPX),
    AND_ABS(0x2D, AddrMode.ABS), AND_ABX(0x3D, AddrMode.ABX), AND_ABY(0x39, AddrMode.ABY),
    AND_INX(0x21, AddrMode.INX), AND_INY(0x31, AddrMode.INY),
    // ASL
    ASL_ACC(0x0A, AddrMode.ACC), ASL_ZP(0x06, AddrMode.ZP),   ASL_ZPX(0x16, AddrMode.ZPX),
    ASL_ABS(0x0E, AddrMode.ABS), ASL_ABX(0x1E, AddrMode.ABX),
    // Branches
    BCC(0x90, AddrMode.REL), BCS(0xB0, AddrMode.REL), BEQ(0xF0, AddrMode.REL),
    BMI(0x30, AddrMode.REL), BNE(0xD0, AddrMode.REL), BPL(0x10, AddrMode.REL),
    BVC(0x50, AddrMode.REL), BVS(0x70, AddrMode.REL),
    // BIT
    BIT_ZP(0x24, AddrMode.ZP), BIT_ABS(0x2C, AddrMode.ABS),
    // BRK
    BRK(0x00, AddrMode.IMP),
    // CLC/CLD/CLI/CLV
    CLC(0x18, AddrMode.IMP), CLD(0xD8, AddrMode.IMP), CLI(0x58, AddrMode.IMP), CLV(0xB8, AddrMode.IMP),
    // CMP
    CMP_IMM(0xC9, AddrMode.IMM), CMP_ZP(0xC5, AddrMode.ZP),   CMP_ZPX(0xD5, AddrMode.ZPX),
    CMP_ABS(0xCD, AddrMode.ABS), CMP_ABX(0xDD, AddrMode.ABX), CMP_ABY(0xD9, AddrMode.ABY),
    CMP_INX(0xC1, AddrMode.INX), CMP_INY(0xD1, AddrMode.INY),
    // CPX
    CPX_IMM(0xE0, AddrMode.IMM), CPX_ZP(0xE4, AddrMode.ZP), CPX_ABS(0xEC, AddrMode.ABS),
    // CPY
    CPY_IMM(0xC0, AddrMode.IMM), CPY_ZP(0xC4, AddrMode.ZP), CPY_ABS(0xCC, AddrMode.ABS),
    // DEC
    DEC_ZP(0xC6, AddrMode.ZP), DEC_ZPX(0xD6, AddrMode.ZPX),
    DEC_ABS(0xCE, AddrMode.ABS), DEC_ABX(0xDE, AddrMode.ABX),
    // DEX/DEY
    DEX(0xCA, AddrMode.IMP), DEY(0x88, AddrMode.IMP),
    // EOR
    EOR_IMM(0x49, AddrMode.IMM), EOR_ZP(0x45, AddrMode.ZP),   EOR_ZPX(0x55, AddrMode.ZPX),
    EOR_ABS(0x4D, AddrMode.ABS), EOR_ABX(0x5D, AddrMode.ABX), EOR_ABY(0x59, AddrMode.ABY),
    EOR_INX(0x41, AddrMode.INX), EOR_INY(0x51, AddrMode.INY),
    // INC
    INC_ZP(0xE6, AddrMode.ZP), INC_ZPX(0xF6, AddrMode.ZPX),
    INC_ABS(0xEE, AddrMode.ABS), INC_ABX(0xFE, AddrMode.ABX),
    // INX/INY
    INX(0xE8, AddrMode.IMP), INY(0xC8, AddrMode.IMP),
    // JMP
    JMP_ABS(0x4C, AddrMode.ABS), JMP_IND(0x6C, AddrMode.IND),
    // JSR
    JSR(0x20, AddrMode.ABS),
    // LDA
    LDA_IMM(0xA9, AddrMode.IMM), LDA_ZP(0xA5, AddrMode.ZP),   LDA_ZPX(0xB5, AddrMode.ZPX),
    LDA_ABS(0xAD, AddrMode.ABS), LDA_ABX(0xBD, AddrMode.ABX), LDA_ABY(0xB9, AddrMode.ABY),
    LDA_INX(0xA1, AddrMode.INX), LDA_INY(0xB1, AddrMode.INY),
    // LDX
    LDX_IMM(0xA2, AddrMode.IMM), LDX_ZP(0xA6, AddrMode.ZP),   LDX_ZPY(0xB6, AddrMode.ZPY),
    LDX_ABS(0xAE, AddrMode.ABS), LDX_ABY(0xBE, AddrMode.ABY),
    // LDY
    LDY_IMM(0xA0, AddrMode.IMM), LDY_ZP(0xA4, AddrMode.ZP),   LDY_ZPX(0xB4, AddrMode.ZPX),
    LDY_ABS(0xAC, AddrMode.ABS), LDY_ABX(0xBC, AddrMode.ABX),
    // LSR
    LSR_ACC(0x4A, AddrMode.ACC), LSR_ZP(0x46, AddrMode.ZP),   LSR_ZPX(0x56, AddrMode.ZPX),
    LSR_ABS(0x4E, AddrMode.ABS), LSR_ABX(0x5E, AddrMode.ABX),
    // NOP
    NOP(0xEA, AddrMode.IMP),
    // ORA
    ORA_IMM(0x09, AddrMode.IMM), ORA_ZP(0x05, AddrMode.ZP),   ORA_ZPX(0x15, AddrMode.ZPX),
    ORA_ABS(0x0D, AddrMode.ABS), ORA_ABX(0x1D, AddrMode.ABX), ORA_ABY(0x19, AddrMode.ABY),
    ORA_INX(0x01, AddrMode.INX), ORA_INY(0x11, AddrMode.INY),
    // Stack
    PHA(0x48, AddrMode.IMP), PHP(0x08, AddrMode.IMP),
    PLA(0x68, AddrMode.IMP), PLP(0x28, AddrMode.IMP),
    // ROL
    ROL_ACC(0x2A, AddrMode.ACC), ROL_ZP(0x26, AddrMode.ZP),   ROL_ZPX(0x36, AddrMode.ZPX),
    ROL_ABS(0x2E, AddrMode.ABS), ROL_ABX(0x3E, AddrMode.ABX),
    // ROR
    ROR_ACC(0x6A, AddrMode.ACC), ROR_ZP(0x66, AddrMode.ZP),   ROR_ZPX(0x76, AddrMode.ZPX),
    ROR_ABS(0x6E, AddrMode.ABS), ROR_ABX(0x7E, AddrMode.ABX),
    // RTI/RTS
    RTI(0x40, AddrMode.IMP), RTS(0x60, AddrMode.IMP),
    // SBC
    SBC_IMM(0xE9, AddrMode.IMM), SBC_ZP(0xE5, AddrMode.ZP),   SBC_ZPX(0xF5, AddrMode.ZPX),
    SBC_ABS(0xED, AddrMode.ABS), SBC_ABX(0xFD, AddrMode.ABX), SBC_ABY(0xF9, AddrMode.ABY),
    SBC_INX(0xE1, AddrMode.INX), SBC_INY(0xF1, AddrMode.INY),
    // SEC/SED/SEI
    SEC(0x38, AddrMode.IMP), SED(0xF8, AddrMode.IMP), SEI(0x78, AddrMode.IMP),
    // STA
    STA_ZP(0x85, AddrMode.ZP),   STA_ZPX(0x95, AddrMode.ZPX),
    STA_ABS(0x8D, AddrMode.ABS), STA_ABX(0x9D, AddrMode.ABX), STA_ABY(0x99, AddrMode.ABY),
    STA_INX(0x81, AddrMode.INX), STA_INY(0x91, AddrMode.INY),
    // STX
    STX_ZP(0x86, AddrMode.ZP), STX_ZPY(0x96, AddrMode.ZPY), STX_ABS(0x8E, AddrMode.ABS),
    // STY
    STY_ZP(0x84, AddrMode.ZP), STY_ZPX(0x94, AddrMode.ZPX), STY_ABS(0x8C, AddrMode.ABS),
    // Transfer
    TAX(0xAA, AddrMode.IMP), TAY(0xA8, AddrMode.IMP),
    TSX(0xBA, AddrMode.IMP), TXA(0x8A, AddrMode.IMP),
    TXS(0x9A, AddrMode.IMP), TYA(0x98, AddrMode.IMP);

    val mnemonic: String get() = name.substringBefore('_')

    companion object {
        private val byByte = entries.associateBy { it.byte }
        fun fromByte(b: Int): Opcode? = byByte[b]
    }
}
