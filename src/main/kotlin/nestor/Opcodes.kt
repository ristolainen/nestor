package nestor

// LDA
const val LDA_IMM = 0xA9
const val LDA_ABS = 0xAD
const val LDA_ABX = 0xBD
const val LDA_ABY = 0xB9
const val LDA_INX = 0xA1
const val LDA_INY = 0xB1

// LDX
const val LDX_IMM = 0xA2
const val LDX_ABS = 0xAE
const val LDX_ABY = 0xBE

// LDY
const val LDY_IMM = 0xA0
const val LDY_ABS = 0xAC

// STA
const val STA_ZP  = 0x85
const val STA_ZPX = 0x95
const val STA_ABS = 0x8D
const val STA_ABX = 0x9D
const val STA_ABY = 0x99
const val STA_INX = 0x81
const val STA_INY = 0x91

// STX
const val STX_ZP  = 0x86
const val STX_ZPY = 0x96

// STY
const val STY_ZP  = 0x84
const val STY_ZPX = 0x94

// Transfer
const val TAX = 0xAA
const val TXA = 0x8A
const val TYA = 0x98
const val TXS = 0x9A

// Stack
const val PHA = 0x48
const val PLA = 0x68

// AND
const val AND_IMM = 0x29
const val AND_ZP  = 0x25
const val AND_ZPX = 0x35
const val AND_ABS = 0x2D
const val AND_ABX = 0x3D
const val AND_ABY = 0x39
const val AND_INX = 0x21
const val AND_INY = 0x31

// ORA
const val ORA_IMM = 0x09
const val ORA_ZP  = 0x05
const val ORA_ZPX = 0x15
const val ORA_ABS = 0x0D
const val ORA_ABX = 0x1D
const val ORA_ABY = 0x19
const val ORA_INX = 0x01
const val ORA_INY = 0x11

// BIT
const val BIT_ZP  = 0x24
const val BIT_ABS = 0x2C

// Shift
const val LSR_ACC = 0x4A

// Compare
const val CMP_IMM = 0xC9
const val CPX_IMM = 0xE0
const val CPY_IMM = 0xC0

// Branch
const val BPL = 0x10
const val BMI = 0x30
const val BVC = 0x50
const val BVS = 0x70
const val BCC = 0x90
const val BCS = 0xB0
const val BNE = 0xD0
const val BEQ = 0xF0

// INC/DEC
const val INC_ZP  = 0xE6
const val INC_ZPX = 0xF6
const val INC_ABS = 0xEE
const val INC_ABX = 0xFE
const val INX = 0xE8
const val INY = 0xC8
const val DEX = 0xCA
const val DEY = 0x88

// Jump / call
const val JMP_ABS = 0x4C
const val JMP_IND = 0x6C
const val JSR = 0x20
const val RTS = 0x60

// Flags
const val SEI = 0x78
const val CLD = 0xD8

// Misc
const val NOP = 0xEA
