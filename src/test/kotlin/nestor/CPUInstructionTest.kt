package nestor

import io.kotest.core.spec.style.FreeSpec
import nestor.Opcode.*

class CPUInstructionTest : FreeSpec({

    // ── ORA ──────────────────────────────────────────────────────────────
    "ORA immediate" - {
        testStep(
            "sets result",
            Instruction(ORA_IMM, 0x0F),
            CpuSetup()
                .a(0xF0),
            ExpectedStepOutcome(cycles = 2, a = 0xFF, zero = false, negative = true)
        )
        testStep(
            "zero result",
            Instruction(ORA_IMM, 0x00),
            CpuSetup()
                .a(0x00),
            ExpectedStepOutcome(cycles = 2, a = 0x00, zero = true, negative = false)
        )
    }
    "ORA zero page" - {
        testStep(
            "basic",
            Instruction(ORA_ZP, 0x10),
            CpuSetup()
                .a(0x0F)
                .mem(0x10, 0xF0),
            ExpectedStepOutcome(cycles = 3, a = 0xFF, zero = false, negative = true)
        )
    }
    "ORA zero page,X" - {
        testStep(
            "indexed",
            Instruction(ORA_ZPX, 0x10),
            CpuSetup()
                .a(0x0F)
                .x(0x04)
                .mem(0x14, 0x70),
            ExpectedStepOutcome(cycles = 4, a = 0x7F, zero = false, negative = false)
        )
        testStep(
            "zero page wraps",
            Instruction(ORA_ZPX, 0xFF),
            CpuSetup()
                .a(0x01)
                .x(0x02)
                .mem(0x01, 0x02),
            ExpectedStepOutcome(cycles = 4, a = 0x03, zero = false, negative = false)
        )
    }
    "ORA absolute" - {
        testStep(
            "basic",
            Instruction(ORA_ABS, 0x00, 0x02),
            CpuSetup()
                .a(0x0F)
                .mem(0x0200, 0xF0),
            ExpectedStepOutcome(cycles = 4, a = 0xFF, zero = false, negative = true)
        )
    }
    "ORA absolute,X" - {
        testStep(
            "no page cross",
            Instruction(ORA_ABX, 0x00, 0x02),
            CpuSetup()
                .a(0x01)
                .x(0x01)
                .mem(0x0201, 0x02),
            ExpectedStepOutcome(cycles = 4, a = 0x03, zero = false, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(ORA_ABX, 0xFF, 0x01),
            CpuSetup()
                .a(0x01)
                .x(0x01)
                .mem(0x0200, 0x02),
            ExpectedStepOutcome(cycles = 5, a = 0x03, zero = false, negative = false)
        )
    }
    "ORA absolute,Y" - {
        testStep(
            "no page cross",
            Instruction(ORA_ABY, 0x00, 0x02),
            CpuSetup()
                .a(0x01)
                .y(0x01)
                .mem(0x0201, 0x02),
            ExpectedStepOutcome(cycles = 4, a = 0x03, zero = false, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(ORA_ABY, 0xFF, 0x01),
            CpuSetup()
                .a(0x01)
                .y(0x01)
                .mem(0x0200, 0x02),
            ExpectedStepOutcome(cycles = 5, a = 0x03, zero = false, negative = false)
        )
    }
    "ORA (indirect,X)" - {
        testStep(
            "basic",
            Instruction(ORA_INX, 0x10),
            CpuSetup()
                .a(0x0F)
                .x(0x04)
                .mem(0x14, 0x00, 0x02)
                .mem(0x0200, 0xF0),
            ExpectedStepOutcome(cycles = 6, a = 0xFF, zero = false, negative = true)
        )
        testStep(
            "zero page pointer wraps",
            Instruction(ORA_INX, 0xFF),
            CpuSetup()
                .a(0x01)
                .x(0x02)
                .mem(0x01, 0x00, 0x02)
                .mem(0x0200, 0x02),
            ExpectedStepOutcome(cycles = 6, a = 0x03, zero = false, negative = false)
        )
    }
    "ORA (indirect),Y" - {
        testStep(
            "no page cross",
            Instruction(ORA_INY, 0x10),
            CpuSetup()
                .a(0x0F)
                .y(0x01)
                .mem(0x10, 0x00, 0x02)
                .mem(0x0201, 0xF0),
            ExpectedStepOutcome(cycles = 5, a = 0xFF, zero = false, negative = true)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(ORA_INY, 0x10),
            CpuSetup()
                .a(0x01)
                .y(0x01)
                .mem(0x10, 0xFF, 0x01)
                .mem(0x0200, 0x02),
            ExpectedStepOutcome(cycles = 6, a = 0x03, zero = false, negative = false)
        )
    }

    // ── AND ──────────────────────────────────────────────────────────────
    "AND immediate" - {
        testStep(
            "masks bits",
            Instruction(AND_IMM, 0x0F),
            CpuSetup()
                .a(0xFF),
            ExpectedStepOutcome(cycles = 2, a = 0x0F, zero = false, negative = false)
        )
        testStep(
            "zero result",
            Instruction(AND_IMM, 0x00),
            CpuSetup()
                .a(0xFF),
            ExpectedStepOutcome(cycles = 2, a = 0x00, zero = true, negative = false)
        )
        testStep(
            "negative result",
            Instruction(AND_IMM, 0xFF),
            CpuSetup()
                .a(0x80),
            ExpectedStepOutcome(cycles = 2, a = 0x80, zero = false, negative = true)
        )
    }
    "AND zero page" - {
        testStep(
            "basic",
            Instruction(AND_ZP, 0x10),
            CpuSetup()
                .a(0xFF)
                .mem(0x10, 0x0F),
            ExpectedStepOutcome(cycles = 3, a = 0x0F, zero = false, negative = false)
        )
    }
    "AND zero page,X" - {
        testStep(
            "indexed",
            Instruction(AND_ZPX, 0x10),
            CpuSetup()
                .a(0xFF)
                .x(0x04)
                .mem(0x14, 0x0F),
            ExpectedStepOutcome(cycles = 4, a = 0x0F, zero = false, negative = false)
        )
    }
    "AND absolute" - {
        testStep(
            "basic",
            Instruction(AND_ABS, 0x00, 0x02),
            CpuSetup()
                .a(0xFF)
                .mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 4, a = 0x0F, zero = false, negative = false)
        )
    }
    "AND absolute,X" - {
        testStep(
            "no page cross",
            Instruction(AND_ABX, 0x00, 0x02),
            CpuSetup()
                .a(0xFF)
                .x(0x01)
                .mem(0x0201, 0x0F),
            ExpectedStepOutcome(cycles = 4, a = 0x0F, zero = false, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(AND_ABX, 0xFF, 0x01),
            CpuSetup()
                .a(0xFF)
                .x(0x01)
                .mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 5, a = 0x0F, zero = false, negative = false)
        )
    }
    "AND absolute,Y" - {
        testStep(
            "no page cross",
            Instruction(AND_ABY, 0x00, 0x02),
            CpuSetup()
                .a(0xFF)
                .y(0x01)
                .mem(0x0201, 0x0F),
            ExpectedStepOutcome(cycles = 4, a = 0x0F, zero = false, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(AND_ABY, 0xFF, 0x01),
            CpuSetup()
                .a(0xFF)
                .y(0x01)
                .mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 5, a = 0x0F, zero = false, negative = false)
        )
    }
    "AND (indirect,X)" - {
        testStep(
            "basic",
            Instruction(AND_INX, 0x10),
            CpuSetup()
                .a(0xFF)
                .x(0x04)
                .mem(0x14, 0x00, 0x02)
                .mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 6, a = 0x0F, zero = false, negative = false)
        )
    }
    "AND (indirect),Y" - {
        testStep(
            "no page cross",
            Instruction(AND_INY, 0x10),
            CpuSetup()
                .a(0xFF)
                .y(0x01)
                .mem(0x10, 0x00, 0x02)
                .mem(0x0201, 0x0F),
            ExpectedStepOutcome(cycles = 5, a = 0x0F, zero = false, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(AND_INY, 0x10),
            CpuSetup()
                .a(0xFF)
                .y(0x01)
                .mem(0x10, 0xFF, 0x01)
                .mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 6, a = 0x0F, zero = false, negative = false)
        )
    }

    // ── BIT ──────────────────────────────────────────────────────────────
    // BIT: Z = (A & M) == 0, N = M bit 7, V = M bit 6. A is not modified.
    "BIT zero page" - {
        testStep(
            "all flags set",
            Instruction(BIT_ZP, 0x10),
            CpuSetup()
                .a(0x00)
                .mem(0x10, 0xFF),
            ExpectedStepOutcome(cycles = 3, zero = true, negative = true, overflow = true)
        )
        testStep(
            "Z clear N clear V set",
            Instruction(BIT_ZP, 0x10),
            CpuSetup()
                .a(0xFF)
                .mem(0x10, 0x7F),
            ExpectedStepOutcome(cycles = 3, zero = false, negative = false, overflow = true)
        )
    }
    "BIT absolute" - {
        testStep(
            "Z set N set V set",
            Instruction(BIT_ABS, 0x00, 0x02),
            CpuSetup()
                .a(0x00)
                .mem(0x0200, 0xFF),
            ExpectedStepOutcome(cycles = 4, zero = true, negative = true, overflow = true)
        )
        testStep(
            "Z clear when A AND M is non-zero",
            Instruction(BIT_ABS, 0x00, 0x02),
            CpuSetup()
                .a(0x0F)
                .mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 4, zero = false, negative = false, overflow = false)
        )
    }

    // ── LSR ──────────────────────────────────────────────────────────────
    // LSR accumulator: C = old bit 0, N always 0
    "LSR accumulator" - {
        testStep(
            "shifts right, carry set",
            Instruction(LSR_ACC),
            CpuSetup()
                .a(0x03),
            ExpectedStepOutcome(cycles = 2, a = 0x01, carry = true, zero = false, negative = false)
        )
        testStep(
            "even value, carry clear",
            Instruction(LSR_ACC),
            CpuSetup()
                .a(0x80),
            ExpectedStepOutcome(cycles = 2, a = 0x40, carry = false, zero = false, negative = false)
        )
        testStep(
            "result zero",
            Instruction(LSR_ACC),
            CpuSetup()
                .a(0x01),
            ExpectedStepOutcome(cycles = 2, a = 0x00, carry = true, zero = true, negative = false)
        )
    }
    "LSR zero page" - {
        testStep(
            "shifts right carry set",
            Instruction(LSR_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x03),
            ExpectedStepOutcome(cycles = 5, carry = true, zero = false, negative = false, mem = mapOf(0x10 to 0x01))
        )
        testStep(
            "even value carry clear",
            Instruction(LSR_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x80),
            ExpectedStepOutcome(cycles = 5, carry = false, zero = false, negative = false, mem = mapOf(0x10 to 0x40))
        )
        testStep(
            "result zero",
            Instruction(LSR_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x01),
            ExpectedStepOutcome(cycles = 5, carry = true, zero = true, negative = false, mem = mapOf(0x10 to 0x00))
        )
    }
    "LSR zero page,X" - {
        testStep(
            "indexed shift",
            Instruction(LSR_ZPX, 0x10),
            CpuSetup()
                .x(0x04)
                .mem(0x14, 0x03),
            ExpectedStepOutcome(cycles = 6, carry = true, zero = false, negative = false, mem = mapOf(0x14 to 0x01))
        )
        testStep(
            "zero page wraps",
            Instruction(LSR_ZPX, 0xFF),
            CpuSetup()
                .x(0x02)
                .mem(0x01, 0x03),
            ExpectedStepOutcome(cycles = 6, carry = true, zero = false, negative = false, mem = mapOf(0x01 to 0x01))
        )
    }
    "LSR absolute" - {
        testStep(
            "shifts at 16-bit address",
            Instruction(LSR_ABS, 0x00, 0x02),
            CpuSetup()
                .mem(0x0200, 0x03),
            ExpectedStepOutcome(cycles = 6, carry = true, zero = false, negative = false, mem = mapOf(0x0200 to 0x01))
        )
    }
    "LSR absolute,X" - {
        testStep(
            "always 7 cycles RMW",
            Instruction(LSR_ABX, 0x00, 0x02),
            CpuSetup()
                .x(0x01)
                .mem(0x0201, 0x03),
            ExpectedStepOutcome(cycles = 7, carry = true, zero = false, negative = false, mem = mapOf(0x0201 to 0x01))
        )
    }

    // ── ASL ──────────────────────────────────────────────────────────────
    // ASL: C = old bit 7, shift left by 1. N = bit 7 of result, Z = result is zero.
    "ASL accumulator" - {
        testStep(
            "shifts left no carry",
            Instruction(ASL_ACC),
            CpuSetup()
                .a(0x40),
            ExpectedStepOutcome(cycles = 2, a = 0x80, carry = false, zero = false, negative = true)
        )
        testStep(
            "carry out",
            Instruction(ASL_ACC),
            CpuSetup()
                .a(0x81),
            ExpectedStepOutcome(cycles = 2, a = 0x02, carry = true, zero = false, negative = false)
        )
        testStep(
            "result zero",
            Instruction(ASL_ACC),
            CpuSetup()
                .a(0x80),
            ExpectedStepOutcome(cycles = 2, a = 0x00, carry = true, zero = true, negative = false)
        )
    }
    "ASL zero page" - {
        testStep(
            "shifts left sets N",
            Instruction(ASL_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x40),
            ExpectedStepOutcome(cycles = 5, carry = false, zero = false, negative = true, mem = mapOf(0x10 to 0x80))
        )
        testStep(
            "carry out result zero",
            Instruction(ASL_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x80),
            ExpectedStepOutcome(cycles = 5, carry = true, zero = true, negative = false, mem = mapOf(0x10 to 0x00))
        )
    }
    "ASL zero page,X" - {
        testStep(
            "indexed shift",
            Instruction(ASL_ZPX, 0x10),
            CpuSetup()
                .x(0x04)
                .mem(0x14, 0x40),
            ExpectedStepOutcome(cycles = 6, carry = false, zero = false, negative = true, mem = mapOf(0x14 to 0x80))
        )
        testStep(
            "zero page wraps",
            Instruction(ASL_ZPX, 0xFF),
            CpuSetup()
                .x(0x02)
                .mem(0x01, 0x40),
            ExpectedStepOutcome(cycles = 6, carry = false, zero = false, negative = true, mem = mapOf(0x01 to 0x80))
        )
    }
    "ASL absolute" - {
        testStep(
            "shifts at 16-bit address",
            Instruction(ASL_ABS, 0x00, 0x02),
            CpuSetup()
                .mem(0x0200, 0x40),
            ExpectedStepOutcome(cycles = 6, carry = false, zero = false, negative = true, mem = mapOf(0x0200 to 0x80))
        )
    }
    "ASL absolute,X" - {
        testStep(
            "always 7 cycles RMW",
            Instruction(ASL_ABX, 0x00, 0x02),
            CpuSetup()
                .x(0x01)
                .mem(0x0201, 0x40),
            ExpectedStepOutcome(cycles = 7, carry = false, zero = false, negative = true, mem = mapOf(0x0201 to 0x80))
        )
    }

    // ── ROL ──────────────────────────────────────────────────────────────
    // ROL: result = (value << 1) | old_carry. C = old bit 7. N, Z from result.
    "ROL accumulator" - {
        testStep(
            "shifts left, carry in clears, carry out clears",
            Instruction(ROL_ACC),
            CpuSetup()
                .a(0x40),
            ExpectedStepOutcome(cycles = 2, a = 0x80, carry = false, zero = false, negative = true)
        )
        testStep(
            "carry in rotates into bit 0",
            Instruction(ROL_ACC),
            CpuSetup()
                .a(0x40)
                .carry(true),
            ExpectedStepOutcome(cycles = 2, a = 0x81, carry = false, zero = false, negative = true)
        )
        testStep(
            "old bit 7 goes to carry",
            Instruction(ROL_ACC),
            CpuSetup()
                .a(0x81),
            ExpectedStepOutcome(cycles = 2, a = 0x02, carry = true, zero = false, negative = false)
        )
        testStep(
            "result zero",
            Instruction(ROL_ACC),
            CpuSetup()
                .a(0x80),
            ExpectedStepOutcome(cycles = 2, a = 0x00, carry = true, zero = true, negative = false)
        )
    }
    "ROL zero page" - {
        testStep(
            "rotates memory value",
            Instruction(ROL_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x40),
            ExpectedStepOutcome(cycles = 5, carry = false, zero = false, negative = true, mem = mapOf(0x10 to 0x80))
        )
        testStep(
            "carry in sets bit 0",
            Instruction(ROL_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x00)
                .carry(true),
            ExpectedStepOutcome(cycles = 5, carry = false, zero = false, negative = false, mem = mapOf(0x10 to 0x01))
        )
    }
    "ROL zero page,X" - {
        testStep(
            "indexed rotate",
            Instruction(ROL_ZPX, 0x10),
            CpuSetup()
                .x(0x04)
                .mem(0x14, 0x40),
            ExpectedStepOutcome(cycles = 6, carry = false, zero = false, negative = true, mem = mapOf(0x14 to 0x80))
        )
        testStep(
            "zero page wraps",
            Instruction(ROL_ZPX, 0xFF),
            CpuSetup()
                .x(0x02)
                .mem(0x01, 0x40),
            ExpectedStepOutcome(cycles = 6, carry = false, zero = false, negative = true, mem = mapOf(0x01 to 0x80))
        )
    }
    "ROL absolute" - {
        testStep(
            "rotates at 16-bit address",
            Instruction(ROL_ABS, 0x00, 0x02),
            CpuSetup()
                .mem(0x0200, 0x40),
            ExpectedStepOutcome(cycles = 6, carry = false, zero = false, negative = true, mem = mapOf(0x0200 to 0x80))
        )
    }
    "ROL absolute,X" - {
        testStep(
            "always 7 cycles RMW",
            Instruction(ROL_ABX, 0x00, 0x02),
            CpuSetup()
                .x(0x01)
                .mem(0x0201, 0x40),
            ExpectedStepOutcome(cycles = 7, carry = false, zero = false, negative = true, mem = mapOf(0x0201 to 0x80))
        )
    }

    // ── LDA ──────────────────────────────────────────────────────────────
    "LDA immediate" - {
        testStep(
            "positive value",
            Instruction(LDA_IMM, 0x42),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 2, a = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero sets Z",
            Instruction(LDA_IMM, 0x00),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 2, a = 0x00, zero = true, negative = false)
        )
        testStep(
            "negative sets N",
            Instruction(LDA_IMM, 0x80),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 2, a = 0x80, zero = false, negative = true)
        )
    }
    "LDA absolute" - {
        testStep(
            "basic",
            Instruction(LDA_ABS, 0x00, 0x02),
            CpuSetup()
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 4, a = 0x42, zero = false, negative = false)
        )
    }
    "LDA zero page" - {
        testStep(
            "basic positive value",
            Instruction(LDA_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x42),
            ExpectedStepOutcome(cycles = 3, a = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero sets Z",
            Instruction(LDA_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x00),
            ExpectedStepOutcome(cycles = 3, a = 0x00, zero = true, negative = false)
        )
    }
    "LDA zero page,X" - {
        testStep(
            "indexed access",
            Instruction(LDA_ZPX, 0x10),
            CpuSetup()
                .x(0x04)
                .mem(0x14, 0x42),
            ExpectedStepOutcome(cycles = 4, a = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero page wraps",
            Instruction(LDA_ZPX, 0xFF),
            CpuSetup()
                .x(0x02)
                .mem(0x01, 0x42),
            ExpectedStepOutcome(cycles = 4, a = 0x42, zero = false, negative = false)
        )
    }
    "LDA absolute,X" - {
        testStep(
            "no page cross",
            Instruction(LDA_ABX, 0x00, 0x02),
            CpuSetup()
                .x(0x01)
                .mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 4, a = 0x42, zero = false, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(LDA_ABX, 0xFF, 0x01),
            CpuSetup()
                .x(0x01)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 5, a = 0x42, zero = false, negative = false)
        )
    }
    "LDA absolute,Y" - {
        testStep(
            "no page cross",
            Instruction(LDA_ABY, 0x00, 0x02),
            CpuSetup()
                .y(0x01)
                .mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 4, a = 0x42, zero = false, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(LDA_ABY, 0xFF, 0x01),
            CpuSetup()
                .y(0x01)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 5, a = 0x42, zero = false, negative = false)
        )
    }
    "LDA (indirect,X)" - {
        testStep(
            "basic",
            Instruction(LDA_INX, 0x10),
            CpuSetup()
                .x(0x04)
                .mem(0x14, 0x00, 0x02)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 6, a = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero page pointer wraps",
            Instruction(LDA_INX, 0xFF),
            CpuSetup()
                .x(0x02)
                .mem(0x01, 0x00, 0x02)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 6, a = 0x42, zero = false, negative = false)
        )
    }
    "LDA (indirect),Y" - {
        testStep(
            "no page cross",
            Instruction(LDA_INY, 0x10),
            CpuSetup()
                .y(0x01)
                .mem(0x10, 0x00, 0x02)
                .mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 5, a = 0x42, zero = false, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(LDA_INY, 0x10),
            CpuSetup()
                .y(0x01)
                .mem(0x10, 0xFF, 0x01)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 6, a = 0x42, zero = false, negative = false)
        )
        testStep(
            "pointer wraps at zp=FF reads hi from 0x0000",
            Instruction(LDA_INY, 0xFF),
            CpuSetup()
                .y(0x01)
                .mem(0x00FF, 0x34)
                .mem(0x0000, 0x12)
                .mem(0x1235, 0x2A),
            ExpectedStepOutcome(cycles = 5, a = 0x2A, zero = false, negative = false)
        )
    }

    // ── LDX ──────────────────────────────────────────────────────────────
    "LDX immediate" - {
        testStep(
            "positive value",
            Instruction(LDX_IMM, 0x42),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 2, x = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero sets Z",
            Instruction(LDX_IMM, 0x00),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 2, x = 0x00, zero = true, negative = false)
        )
    }
    "LDX absolute" - {
        testStep(
            "basic",
            Instruction(LDX_ABS, 0x00, 0x02),
            CpuSetup()
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 4, x = 0x42, zero = false, negative = false)
        )
    }
    "LDX zero page" - {
        testStep(
            "basic load",
            Instruction(LDX_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x42),
            ExpectedStepOutcome(cycles = 3, x = 0x42, zero = false, negative = false)
        )
        testStep(
            "negative value sets N",
            Instruction(LDX_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x80),
            ExpectedStepOutcome(cycles = 3, x = 0x80, zero = false, negative = true)
        )
    }
    "LDX zero page,Y" - {
        testStep(
            "indexed access",
            Instruction(LDX_ZPY, 0x10),
            CpuSetup()
                .y(0x04)
                .mem(0x14, 0x42),
            ExpectedStepOutcome(cycles = 4, x = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero page wraps",
            Instruction(LDX_ZPY, 0xFE),
            CpuSetup()
                .y(0x02)
                .mem(0x00, 0x42),
            ExpectedStepOutcome(cycles = 4, x = 0x42, zero = false, negative = false)
        )
    }
    "LDX absolute,Y" - {
        testStep(
            "no page cross",
            Instruction(LDX_ABY, 0x00, 0x02),
            CpuSetup()
                .y(0x01)
                .mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 4, x = 0x42, zero = false, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(LDX_ABY, 0xFF, 0x01),
            CpuSetup()
                .y(0x01)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 5, x = 0x42, zero = false, negative = false)
        )
    }

    // ── LDY ──────────────────────────────────────────────────────────────
    "LDY immediate" - {
        testStep(
            "positive value",
            Instruction(LDY_IMM, 0x42),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 2, y = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero sets Z",
            Instruction(LDY_IMM, 0x00),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 2, y = 0x00, zero = true, negative = false)
        )
    }
    "LDY absolute" - {
        testStep(
            "basic",
            Instruction(LDY_ABS, 0x00, 0x02),
            CpuSetup()
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 4, y = 0x42, zero = false, negative = false)
        )
    }
    "LDY zero page" - {
        testStep(
            "basic load",
            Instruction(LDY_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x42),
            ExpectedStepOutcome(cycles = 3, y = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero value sets Z",
            Instruction(LDY_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x00),
            ExpectedStepOutcome(cycles = 3, y = 0x00, zero = true, negative = false)
        )
    }
    "LDY zero page,X" - {
        testStep(
            "indexed access",
            Instruction(LDY_ZPX, 0x10),
            CpuSetup()
                .x(0x04)
                .mem(0x14, 0x42),
            ExpectedStepOutcome(cycles = 4, y = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero page wraps",
            Instruction(LDY_ZPX, 0xFF),
            CpuSetup()
                .x(0x02)
                .mem(0x01, 0x42),
            ExpectedStepOutcome(cycles = 4, y = 0x42, zero = false, negative = false)
        )
    }
    "LDY absolute,X" - {
        testStep(
            "no page cross",
            Instruction(LDY_ABX, 0x00, 0x02),
            CpuSetup()
                .x(0x01)
                .mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 4, y = 0x42, zero = false, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(LDY_ABX, 0xFF, 0x01),
            CpuSetup()
                .x(0x01)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 5, y = 0x42, zero = false, negative = false)
        )
    }

    // ── STA ──────────────────────────────────────────────────────────────
    "STA zero page" - {
        testStep(
            "stores A",
            Instruction(STA_ZP, 0x10),
            CpuSetup()
                .a(0x42),
            ExpectedStepOutcome(cycles = 3, mem = mapOf(0x10 to 0x42))
        )
    }
    "STA zero page,X" - {
        testStep(
            "indexed store",
            Instruction(STA_ZPX, 0x10),
            CpuSetup()
                .a(0x42)
                .x(0x04),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x14 to 0x42))
        )
        testStep(
            "zero page wraps",
            Instruction(STA_ZPX, 0xFF),
            CpuSetup()
                .a(0x42)
                .x(0x02),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x01 to 0x42))
        )
    }
    "STA absolute" - {
        testStep(
            "stores A",
            Instruction(STA_ABS, 0x00, 0x02),
            CpuSetup()
                .a(0x42),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x0200 to 0x42))
        )
    }
    "STA absolute,X" - {
        testStep(
            "stores A",
            Instruction(STA_ABX, 0x00, 0x02),
            CpuSetup()
                .a(0x42)
                .x(0x01),
            ExpectedStepOutcome(cycles = 5, mem = mapOf(0x0201 to 0x42))
        )
    }
    "STA absolute,Y" - {
        testStep(
            "stores A",
            Instruction(STA_ABY, 0x00, 0x02),
            CpuSetup()
                .a(0x42)
                .y(0x01),
            ExpectedStepOutcome(cycles = 5, mem = mapOf(0x0201 to 0x42))
        )
    }
    "STA (indirect,X)" - {
        testStep(
            "stores A",
            Instruction(STA_INX, 0x10),
            CpuSetup()
                .a(0x42)
                .x(0x04)
                .mem(0x14, 0x00, 0x02),
            ExpectedStepOutcome(cycles = 6, mem = mapOf(0x0200 to 0x42))
        )
    }
    "STA (indirect),Y" - {
        testStep(
            "stores A",
            Instruction(STA_INY, 0x10),
            CpuSetup()
                .a(0x42)
                .y(0x01)
                .mem(0x10, 0x00, 0x02),
            ExpectedStepOutcome(cycles = 6, mem = mapOf(0x0201 to 0x42))
        )
    }

    // ── STX ──────────────────────────────────────────────────────────────
    "STX zero page" - {
        testStep(
            "stores X",
            Instruction(STX_ZP, 0x10),
            CpuSetup()
                .x(0x42),
            ExpectedStepOutcome(cycles = 3, mem = mapOf(0x10 to 0x42))
        )
    }
    "STX zero page,Y" - {
        testStep(
            "indexed store",
            Instruction(STX_ZPY, 0x10),
            CpuSetup()
                .x(0x42)
                .y(0x04),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x14 to 0x42))
        )
    }
    "STX absolute" - {
        testStep(
            "stores X",
            Instruction(STX_ABS, 0x00, 0x02),
            CpuSetup()
                .x(0x42),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x0200 to 0x42))
        )
    }

    // ── STY ──────────────────────────────────────────────────────────────
    "STY zero page" - {
        testStep(
            "stores Y",
            Instruction(STY_ZP, 0x10),
            CpuSetup()
                .y(0x42),
            ExpectedStepOutcome(cycles = 3, mem = mapOf(0x10 to 0x42))
        )
    }
    "STY zero page,X" - {
        testStep(
            "indexed store",
            Instruction(STY_ZPX, 0x10),
            CpuSetup()
                .y(0x42)
                .x(0x04),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x14 to 0x42))
        )
    }
    "STY absolute" - {
        testStep(
            "stores Y",
            Instruction(STY_ABS, 0x00, 0x02),
            CpuSetup()
                .y(0x42),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x0200 to 0x42))
        )
    }

    // ── CMP ──────────────────────────────────────────────────────────────
    // CMP: C = A >= M, Z = A == M, N = bit 7 of (A - M)
    "CMP immediate" - {
        testStep(
            "equal sets Z and C",
            Instruction(CMP_IMM, 0x42),
            CpuSetup()
                .a(0x42),
            ExpectedStepOutcome(cycles = 2, carry = true, zero = true, negative = false)
        )
        testStep(
            "greater sets C",
            Instruction(CMP_IMM, 0x01),
            CpuSetup()
                .a(0x02),
            ExpectedStepOutcome(cycles = 2, carry = true, zero = false, negative = false)
        )
        testStep(
            "less clears C sets N",
            Instruction(CMP_IMM, 0x02),
            CpuSetup()
                .a(0x01),
            ExpectedStepOutcome(cycles = 2, carry = false, zero = false, negative = true)
        )
    }
    "CMP zero page" - {
        testStep(
            "equal sets Z and C",
            Instruction(CMP_ZP, 0x10),
            CpuSetup()
                .a(0x42)
                .mem(0x10, 0x42),
            ExpectedStepOutcome(cycles = 3, carry = true, zero = true, negative = false)
        )
        testStep(
            "less than clears C sets N",
            Instruction(CMP_ZP, 0x10),
            CpuSetup()
                .a(0x01)
                .mem(0x10, 0x02),
            ExpectedStepOutcome(cycles = 3, carry = false, zero = false, negative = true)
        )
    }
    "CMP zero page,X" - {
        testStep(
            "greater than sets C",
            Instruction(CMP_ZPX, 0x10),
            CpuSetup()
                .a(0x42)
                .x(0x04)
                .mem(0x14, 0x01),
            ExpectedStepOutcome(cycles = 4, carry = true, zero = false, negative = false)
        )
        testStep(
            "zero page wraps",
            Instruction(CMP_ZPX, 0xFF),
            CpuSetup()
                .a(0x42)
                .x(0x02)
                .mem(0x01, 0x42),
            ExpectedStepOutcome(cycles = 4, carry = true, zero = true, negative = false)
        )
    }
    "CMP absolute" - {
        testStep(
            "basic verify all flags",
            Instruction(CMP_ABS, 0x00, 0x02),
            CpuSetup()
                .a(0x42)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 4, carry = true, zero = true, negative = false)
        )
    }
    "CMP absolute,X" - {
        testStep(
            "no page cross",
            Instruction(CMP_ABX, 0x00, 0x02),
            CpuSetup()
                .a(0x42)
                .x(0x01)
                .mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 4, carry = true, zero = true, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(CMP_ABX, 0xFF, 0x01),
            CpuSetup()
                .a(0x42)
                .x(0x01)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 5, carry = true, zero = true, negative = false)
        )
    }
    "CMP absolute,Y" - {
        testStep(
            "no page cross",
            Instruction(CMP_ABY, 0x00, 0x02),
            CpuSetup()
                .a(0x42)
                .y(0x01)
                .mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 4, carry = true, zero = true, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(CMP_ABY, 0xFF, 0x01),
            CpuSetup()
                .a(0x42)
                .y(0x01)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 5, carry = true, zero = true, negative = false)
        )
    }
    "CMP (indirect,X)" - {
        testStep(
            "pointer lookup via zp+X",
            Instruction(CMP_INX, 0x10),
            CpuSetup()
                .a(0x42)
                .x(0x04)
                .mem(0x14, 0x00, 0x02)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 6, carry = true, zero = true, negative = false)
        )
    }
    "CMP (indirect),Y" - {
        testStep(
            "no page cross",
            Instruction(CMP_INY, 0x10),
            CpuSetup()
                .a(0x42)
                .y(0x01)
                .mem(0x10, 0x00, 0x02)
                .mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 5, carry = true, zero = true, negative = false)
        )
        testStep(
            "page cross +1 cycle",
            Instruction(CMP_INY, 0x10),
            CpuSetup()
                .a(0x42)
                .y(0x01)
                .mem(0x10, 0xFF, 0x01)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 6, carry = true, zero = true, negative = false)
        )
    }

    // ── CPX ──────────────────────────────────────────────────────────────
    "CPX immediate" - {
        testStep(
            "equal sets Z and C",
            Instruction(CPX_IMM, 0x42),
            CpuSetup()
                .x(0x42),
            ExpectedStepOutcome(cycles = 2, carry = true, zero = true, negative = false)
        )
        testStep(
            "less clears C sets N",
            Instruction(CPX_IMM, 0x02),
            CpuSetup()
                .x(0x01),
            ExpectedStepOutcome(cycles = 2, carry = false, zero = false, negative = true)
        )
    }
    "CPX zero page" - {
        testStep(
            "equal sets Z and C",
            Instruction(CPX_ZP, 0x10),
            CpuSetup()
                .x(0x42)
                .mem(0x10, 0x42),
            ExpectedStepOutcome(cycles = 3, carry = true, zero = true, negative = false)
        )
    }
    "CPX absolute" - {
        testStep(
            "greater than sets C",
            Instruction(CPX_ABS, 0x00, 0x02),
            CpuSetup()
                .x(0x42)
                .mem(0x0200, 0x01),
            ExpectedStepOutcome(cycles = 4, carry = true, zero = false, negative = false)
        )
    }

    // ── CPY ──────────────────────────────────────────────────────────────
    "CPY immediate" - {
        testStep(
            "equal sets Z and C",
            Instruction(CPY_IMM, 0x42),
            CpuSetup()
                .y(0x42),
            ExpectedStepOutcome(cycles = 2, carry = true, zero = true, negative = false)
        )
        testStep(
            "less clears C sets N",
            Instruction(CPY_IMM, 0x02),
            CpuSetup()
                .y(0x01),
            ExpectedStepOutcome(cycles = 2, carry = false, zero = false, negative = true)
        )
    }
    "CPY zero page" - {
        testStep(
            "less than clears C sets N",
            Instruction(CPY_ZP, 0x10),
            CpuSetup()
                .y(0x01)
                .mem(0x10, 0x02),
            ExpectedStepOutcome(cycles = 3, carry = false, zero = false, negative = true)
        )
    }
    "CPY absolute" - {
        testStep(
            "equal sets Z and C",
            Instruction(CPY_ABS, 0x00, 0x02),
            CpuSetup()
                .y(0x42)
                .mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 4, carry = true, zero = true, negative = false)
        )
    }

    // ── INC ──────────────────────────────────────────────────────────────
    // Journal records last write per address (map semantics), so double-write in
    // incrementMemory is transparent — the test sees only the final incremented value.
    "INC zero page" - {
        testStep(
            "increments value",
            Instruction(INC_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x41),
            ExpectedStepOutcome(cycles = 5, zero = false, negative = false, mem = mapOf(0x10 to 0x42))
        )
        testStep(
            "wraps 0xFF to 0x00 sets Z",
            Instruction(INC_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0xFF),
            ExpectedStepOutcome(cycles = 5, zero = true, negative = false, mem = mapOf(0x10 to 0x00))
        )
        testStep(
            "result 0x80 sets N",
            Instruction(INC_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x7F),
            ExpectedStepOutcome(cycles = 5, zero = false, negative = true, mem = mapOf(0x10 to 0x80))
        )
    }
    "INC zero page,X" - {
        testStep(
            "indexed increment",
            Instruction(INC_ZPX, 0x10),
            CpuSetup()
                .x(0x04)
                .mem(0x14, 0x41),
            ExpectedStepOutcome(cycles = 6, zero = false, negative = false, mem = mapOf(0x14 to 0x42))
        )
    }
    "INC absolute" - {
        testStep(
            "increments value",
            Instruction(INC_ABS, 0x00, 0x02),
            CpuSetup()
                .mem(0x0200, 0x41),
            ExpectedStepOutcome(cycles = 6, zero = false, negative = false, mem = mapOf(0x0200 to 0x42))
        )
    }
    "INC absolute,X" - {
        testStep(
            "indexed increment",
            Instruction(INC_ABX, 0x00, 0x02),
            CpuSetup()
                .x(0x01)
                .mem(0x0201, 0x41),
            ExpectedStepOutcome(cycles = 7, zero = false, negative = false, mem = mapOf(0x0201 to 0x42))
        )
    }

    // ── DEC ──────────────────────────────────────────────────────────────
    "DEC zero page" - {
        testStep(
            "decrements value",
            Instruction(DEC_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x43),
            ExpectedStepOutcome(cycles = 5, zero = false, negative = false, mem = mapOf(0x10 to 0x42))
        )
        testStep(
            "result zero sets Z",
            Instruction(DEC_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x01),
            ExpectedStepOutcome(cycles = 5, zero = true, negative = false, mem = mapOf(0x10 to 0x00))
        )
        testStep(
            "wraps 0x00 to 0xFF sets N",
            Instruction(DEC_ZP, 0x10),
            CpuSetup()
                .mem(0x10, 0x00),
            ExpectedStepOutcome(cycles = 5, zero = false, negative = true, mem = mapOf(0x10 to 0xFF))
        )
    }
    "DEC zero page,X" - {
        testStep(
            "indexed decrement",
            Instruction(DEC_ZPX, 0x10),
            CpuSetup()
                .x(0x04)
                .mem(0x14, 0x43),
            ExpectedStepOutcome(cycles = 6, zero = false, negative = false, mem = mapOf(0x14 to 0x42))
        )
    }
    "DEC absolute" - {
        testStep(
            "decrements at 16-bit address",
            Instruction(DEC_ABS, 0x00, 0x02),
            CpuSetup()
                .mem(0x0200, 0x43),
            ExpectedStepOutcome(cycles = 6, zero = false, negative = false, mem = mapOf(0x0200 to 0x42))
        )
    }
    "DEC absolute,X" - {
        testStep(
            "always 7 cycles RMW",
            Instruction(DEC_ABX, 0x00, 0x02),
            CpuSetup()
                .x(0x01)
                .mem(0x0201, 0x43),
            ExpectedStepOutcome(cycles = 7, zero = false, negative = false, mem = mapOf(0x0201 to 0x42))
        )
    }

    // ── INX / INY / DEX / DEY ────────────────────────────────────────────
    "INX implied" - {
        testStep(
            "increments X",
            Instruction(INX),
            CpuSetup()
                .x(0x41),
            ExpectedStepOutcome(cycles = 2, x = 0x42, zero = false, negative = false)
        )
        testStep(
            "wraps 0xFF to 0x00 sets Z",
            Instruction(INX),
            CpuSetup()
                .x(0xFF),
            ExpectedStepOutcome(cycles = 2, x = 0x00, zero = true, negative = false)
        )
    }
    "INY implied" - {
        testStep(
            "increments Y",
            Instruction(INY),
            CpuSetup()
                .y(0x41),
            ExpectedStepOutcome(cycles = 2, y = 0x42, zero = false, negative = false)
        )
    }
    "DEX implied" - {
        testStep(
            "decrements X",
            Instruction(DEX),
            CpuSetup()
                .x(0x43),
            ExpectedStepOutcome(cycles = 2, x = 0x42, zero = false, negative = false)
        )
        testStep(
            "wraps 0x00 to 0xFF sets N",
            Instruction(DEX),
            CpuSetup()
                .x(0x00),
            ExpectedStepOutcome(cycles = 2, x = 0xFF, zero = false, negative = true)
        )
        testStep(
            "result 0x00 sets Z",
            Instruction(DEX),
            CpuSetup()
                .x(0x01),
            ExpectedStepOutcome(cycles = 2, x = 0x00, zero = true, negative = false)
        )
    }
    "DEY implied" - {
        testStep(
            "decrements Y",
            Instruction(DEY),
            CpuSetup()
                .y(0x43),
            ExpectedStepOutcome(cycles = 2, y = 0x42, zero = false, negative = false)
        )
    }

    // ── Transfers ────────────────────────────────────────────────────────
    "TAX implied" - {
        testStep(
            "transfers A to X",
            Instruction(TAX),
            CpuSetup()
                .a(0x42),
            ExpectedStepOutcome(cycles = 2, x = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero sets Z",
            Instruction(TAX),
            CpuSetup()
                .a(0x00),
            ExpectedStepOutcome(cycles = 2, x = 0x00, zero = true, negative = false)
        )
        testStep(
            "negative sets N",
            Instruction(TAX),
            CpuSetup()
                .a(0x80),
            ExpectedStepOutcome(cycles = 2, x = 0x80, zero = false, negative = true)
        )
    }
    "TXA implied" - {
        testStep(
            "transfers X to A",
            Instruction(TXA),
            CpuSetup()
                .x(0x42),
            ExpectedStepOutcome(cycles = 2, a = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero sets Z",
            Instruction(TXA),
            CpuSetup()
                .x(0x00),
            ExpectedStepOutcome(cycles = 2, a = 0x00, zero = true, negative = false)
        )
        testStep(
            "negative sets N",
            Instruction(TXA),
            CpuSetup()
                .x(0x80),
            ExpectedStepOutcome(cycles = 2, a = 0x80, zero = false, negative = true)
        )
    }
    "TYA implied" - {
        testStep(
            "transfers Y to A",
            Instruction(TYA),
            CpuSetup()
                .y(0x42),
            ExpectedStepOutcome(cycles = 2, a = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero sets Z",
            Instruction(TYA),
            CpuSetup()
                .y(0x00),
            ExpectedStepOutcome(cycles = 2, a = 0x00, zero = true, negative = false)
        )
        testStep(
            "negative sets N",
            Instruction(TYA),
            CpuSetup()
                .y(0x80),
            ExpectedStepOutcome(cycles = 2, a = 0x80, zero = false, negative = true)
        )
    }
    "TXS implied" - {
        // TXS does NOT set flags
        testStep(
            "transfers X to SP",
            Instruction(TXS),
            CpuSetup()
                .x(0xFD),
            ExpectedStepOutcome(cycles = 2, sp = 0xFD)
        )
    }
    "TAY implied" - {
        testStep(
            "transfers A to Y",
            Instruction(TAY),
            CpuSetup()
                .a(0x42),
            ExpectedStepOutcome(cycles = 2, y = 0x42, zero = false, negative = false)
        )
        testStep(
            "zero sets Z",
            Instruction(TAY),
            CpuSetup()
                .a(0x00),
            ExpectedStepOutcome(cycles = 2, y = 0x00, zero = true, negative = false)
        )
        testStep(
            "negative sets N",
            Instruction(TAY),
            CpuSetup()
                .a(0x80),
            ExpectedStepOutcome(cycles = 2, y = 0x80, zero = false, negative = true)
        )
    }
    // TSX sets N/Z; TXS does not
    "TSX implied" - {
        testStep(
            "transfers SP to X sets N",
            Instruction(TSX),
            CpuSetup()
                .sp(0xFD),
            ExpectedStepOutcome(cycles = 2, x = 0xFD, zero = false, negative = true)
        )
        testStep(
            "zero sets Z",
            Instruction(TSX),
            CpuSetup()
                .sp(0x00),
            ExpectedStepOutcome(cycles = 2, x = 0x00, zero = true, negative = false)
        )
    }

    // ── PHA / PLA ─────────────────────────────────────────────────────────
    "PHA implied" - {
        testStep(
            "pushes A, decrements SP",
            Instruction(PHA),
            CpuSetup()
                .a(0x42)
                .sp(0xFF),
            ExpectedStepOutcome(cycles = 3, sp = 0xFE, mem = mapOf(0x01FF to 0x42))
        )
    }
    "PLA implied" - {
        testStep(
            "pulls A, sets Z/N, increments SP",
            Instruction(PLA),
            CpuSetup()
                .sp(0xFE)
                .mem(0x01FF, 0x42),
            ExpectedStepOutcome(cycles = 4, a = 0x42, sp = 0xFF, zero = false, negative = false)
        )
        testStep(
            "pulls zero value sets Z",
            Instruction(PLA),
            CpuSetup()
                .sp(0xFE)
                .mem(0x01FF, 0x00),
            ExpectedStepOutcome(cycles = 4, a = 0x00, sp = 0xFF, zero = true, negative = false)
        )
        testStep(
            "pulls negative value sets N",
            Instruction(PLA),
            CpuSetup()
                .sp(0xFE)
                .mem(0x01FF, 0x80),
            ExpectedStepOutcome(cycles = 4, a = 0x80, sp = 0xFF, zero = false, negative = true)
        )
    }
    "PHP implied" - {
        // PHP pushes status with bits 4 (B) and 5 forced set; CPU status is unchanged.
        // After reset, status = 0x24 (bit 5 | FLAG_INTERRUPT). Clearing interrupt → 0x20.
        testStep(
            "pushes status with B bit set decrements SP",
            Instruction(PHP),
            CpuSetup()
                .sp(0xFF)
                .interrupt(false),
            // status = 0x20, pushed = 0x20 | 0x30 = 0x30
            ExpectedStepOutcome(cycles = 3, sp = 0xFE, mem = mapOf(0x01FF to 0x30))
        )
        testStep(
            "B bit set in pushed byte regardless of other flags",
            Instruction(PHP),
            CpuSetup()
                .sp(0xFF)
                .carry(true)
                .interrupt(false),
            // status = 0x21, pushed = 0x21 | 0x30 = 0x31
            ExpectedStepOutcome(cycles = 3, sp = 0xFE, mem = mapOf(0x01FF to 0x31))
        )
    }
    "PLP implied" - {
        // PLP restores all flags; bit 5 always 1 in status after pull; bit 4 (B) cleared.
        testStep(
            "pulls all flags from stack",
            Instruction(PLP),
            CpuSetup()
                .sp(0xFE)
                .interrupt(false)
                .mem(0x01FF, 0xFF),
            // pulled 0xFF → status = (0xFF & 0xEF) | 0x20 = 0xEF
            ExpectedStepOutcome(cycles = 4, sp = 0xFF, carry = true, zero = true, negative = true, overflow = true, interrupt = true, decimal = true)
        )
        testStep(
            "clears all flags",
            Instruction(PLP),
            CpuSetup()
                .sp(0xFE)
                .carry(true)
                .zero(true)
                .negative(true)
                .overflow(true)
                .interrupt(true)
                .decimal(true)
                .mem(0x01FF, 0x00),
            // pulled 0x00 → status = (0x00 & 0xEF) | 0x20 = 0x20
            ExpectedStepOutcome(cycles = 4, sp = 0xFF, carry = false, zero = false, negative = false, overflow = false, interrupt = false, decimal = false)
        )
    }

    // ── JMP ──────────────────────────────────────────────────────────────
    "JMP absolute" - {
        testStep(
            "jumps to address",
            Instruction(JMP_ABS, 0x00, 0xC0),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 3, pc = 0xC000)
        )
    }
    "JMP indirect" - {
        testStep(
            "jumps via pointer",
            Instruction(JMP_IND, 0x00, 0x02),
            CpuSetup()
                .mem(0x0200, 0x00, 0xC0),
            ExpectedStepOutcome(cycles = 5, pc = 0xC000)
        )
        // Hardware bug: indirect JMP wraps within page when low byte is 0xFF
        testStep(
            "page wrap bug: reads hi from same page",
            Instruction(JMP_IND, 0xFF, 0x02),
            CpuSetup()
                .mem(0x02FF, 0x34)
                .mem(0x0200, 0x12),
            ExpectedStepOutcome(cycles = 5, pc = 0x1234)
        )
    }

    // ── JSR / RTS ─────────────────────────────────────────────────────────
    "JSR absolute" - {
        // JSR at 0x8000: target = 0xC123
        // reads 3 bytes → PC = 0x8003, pushes 0x8003-1 = 0x8002 (hi=0x80, lo=0x02)
        testStep(
            "pushes return address and jumps",
            Instruction(JSR, 0x23, 0xC1),
            CpuSetup()
                .sp(0xFF),
            ExpectedStepOutcome(cycles = 6, pc = 0xC123, sp = 0xFD, mem = mapOf(0x01FF to 0x80, 0x01FE to 0x02))
        )
    }
    "RTS implied" - {
        // RTS: pull lo then hi, pc = word(lo, hi) + 1
        testStep(
            "pulls return address and increments PC",
            Instruction(RTS),
            CpuSetup()
                .sp(0xFD)
                .mem(0x01FE, 0x02, 0x80),
            ExpectedStepOutcome(cycles = 6, pc = 0x8003, sp = 0xFF)
        )
        testStep(
            "increments across page boundary",
            Instruction(RTS),
            CpuSetup()
                .sp(0xFD)
                .mem(0x01FE, 0xFF, 0x80),
            ExpectedStepOutcome(cycles = 6, pc = 0x8100, sp = 0xFF)
        )
    }

    // ── Branches ─────────────────────────────────────────────────────────
    // For each branch: not-taken (2 cycles, pc omitted — fixture checks startAddress+2),
    //                  taken same page (3 cycles, pc = startAddress+2+offset),
    //                  taken cross page (4 cycles, address = 0x80FA so PC after reads
    //                  = 0x80FC, target = 0x80FC + 0x04 = 0x8100).

    "BPL relative" - {
        testStep(
            "not taken when N set",
            Instruction(BPL, 0x04),
            CpuSetup()
                .negative(true),
            ExpectedStepOutcome(cycles = 2)
        )
        testStep(
            "taken when N clear",
            Instruction(BPL, 0x04),
            CpuSetup()
                .negative(false),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006)
        )
        testStep(
            "taken cross page",
            Instruction(BPL, 0x04),
            CpuSetup()
                .negative(false),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA
        )
    }
    "BMI relative" - {
        testStep(
            "not taken when N clear",
            Instruction(BMI, 0x04),
            CpuSetup()
                .negative(false),
            ExpectedStepOutcome(cycles = 2)
        )
        testStep(
            "taken when N set",
            Instruction(BMI, 0x04),
            CpuSetup()
                .negative(true),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006)
        )
        testStep(
            "taken cross page",
            Instruction(BMI, 0x04),
            CpuSetup()
                .negative(true),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA
        )
    }
    "BVC relative" - {
        testStep(
            "not taken when V set",
            Instruction(BVC, 0x04),
            CpuSetup()
                .overflow(true),
            ExpectedStepOutcome(cycles = 2)
        )
        testStep(
            "taken when V clear",
            Instruction(BVC, 0x04),
            CpuSetup()
                .overflow(false),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006)
        )
        testStep(
            "taken cross page",
            Instruction(BVC, 0x04),
            CpuSetup()
                .overflow(false),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA
        )
    }
    "BVS relative" - {
        testStep(
            "not taken when V clear",
            Instruction(BVS, 0x04),
            CpuSetup()
                .overflow(false),
            ExpectedStepOutcome(cycles = 2)
        )
        testStep(
            "taken when V set",
            Instruction(BVS, 0x04),
            CpuSetup()
                .overflow(true),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006)
        )
        testStep(
            "taken cross page",
            Instruction(BVS, 0x04),
            CpuSetup()
                .overflow(true),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA
        )
    }
    "BCC relative" - {
        testStep(
            "not taken when C set",
            Instruction(BCC, 0x04),
            CpuSetup()
                .carry(true),
            ExpectedStepOutcome(cycles = 2)
        )
        testStep(
            "taken when C clear",
            Instruction(BCC, 0x04),
            CpuSetup()
                .carry(false),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006)
        )
        testStep(
            "taken cross page",
            Instruction(BCC, 0x04),
            CpuSetup()
                .carry(false),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA
        )
    }
    "BCS relative" - {
        testStep(
            "not taken when C clear",
            Instruction(BCS, 0x04),
            CpuSetup()
                .carry(false),
            ExpectedStepOutcome(cycles = 2)
        )
        testStep(
            "taken when C set",
            Instruction(BCS, 0x04),
            CpuSetup()
                .carry(true),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006)
        )
        testStep(
            "taken cross page",
            Instruction(BCS, 0x04),
            CpuSetup()
                .carry(true),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA
        )
    }
    "BNE relative" - {
        testStep(
            "not taken when Z set",
            Instruction(BNE, 0x04),
            CpuSetup()
                .zero(true),
            ExpectedStepOutcome(cycles = 2)
        )
        testStep(
            "taken forward when Z clear",
            Instruction(BNE, 0x04),
            CpuSetup()
                .zero(false),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006)
        )
        testStep(
            "taken backward when Z clear",
            Instruction(BNE, 0xFE),   // offset = -2 signed → target = 0x8002 + (-2) = 0x8000
            CpuSetup()
                .zero(false),
            ExpectedStepOutcome(cycles = 3, pc = 0x8000)
        )
        testStep(
            "taken cross page",
            Instruction(BNE, 0x04),
            CpuSetup()
                .zero(false),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA
        )
    }
    "BEQ relative" - {
        testStep(
            "not taken when Z clear",
            Instruction(BEQ, 0x04),
            CpuSetup()
                .zero(false),
            ExpectedStepOutcome(cycles = 2)
        )
        testStep(
            "taken when Z set",
            Instruction(BEQ, 0x04),
            CpuSetup()
                .zero(true),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006)
        )
        testStep(
            "taken cross page",
            Instruction(BEQ, 0x04),
            CpuSetup()
                .zero(true),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA
        )
    }

    // ── SEI ──────────────────────────────────────────────────────────────
    "SEI implied" - {
        testStep(
            "sets I flag",
            Instruction(SEI),
            CpuSetup()
                .interrupt(false),
            ExpectedStepOutcome(cycles = 2, interrupt = true)
        )
    }

    // ── CLD ──────────────────────────────────────────────────────────────
    "CLD implied" - {
        testStep(
            "clears D flag",
            Instruction(CLD),
            CpuSetup()
                .decimal(true),
            ExpectedStepOutcome(cycles = 2, decimal = false)
        )
    }
    "CLC implied" - {
        testStep(
            "clears C flag",
            Instruction(CLC),
            CpuSetup()
                .carry(true),
            ExpectedStepOutcome(cycles = 2, carry = false)
        )
    }
    "SEC implied" - {
        testStep(
            "sets C flag",
            Instruction(SEC),
            CpuSetup()
                .carry(false),
            ExpectedStepOutcome(cycles = 2, carry = true)
        )
    }
    "CLI implied" - {
        testStep(
            "clears I flag",
            Instruction(CLI),
            CpuSetup()
                .interrupt(true),
            ExpectedStepOutcome(cycles = 2, interrupt = false)
        )
    }
    "CLV implied" - {
        testStep(
            "clears V flag",
            Instruction(CLV),
            CpuSetup()
                .overflow(true),
            ExpectedStepOutcome(cycles = 2, overflow = false)
        )
    }
    "SED implied" - {
        testStep(
            "sets D flag",
            Instruction(SED),
            CpuSetup()
                .decimal(false),
            ExpectedStepOutcome(cycles = 2, decimal = true)
        )
    }

    // ── SBC ──────────────────────────────────────────────────────────────
    // SBC: A = A - M - (1 - C) = A + ~M + C. C=1 if no borrow. V set on signed overflow.
    "SBC immediate" - {
        testStep(
            "no borrow, carry in set",
            Instruction(SBC_IMM, 0x10),
            CpuSetup()
                .a(0x50)
                .carry(true),
            ExpectedStepOutcome(cycles = 2, a = 0x40, carry = true, zero = false, negative = false, overflow = false)
        )
        testStep(
            "borrow occurs when carry in clear",
            Instruction(SBC_IMM, 0x50),
            CpuSetup()
                .a(0x50)
                .carry(false),
            ExpectedStepOutcome(cycles = 2, a = 0xFF, carry = false, zero = false, negative = true, overflow = false)
        )
        testStep(
            "zero result",
            Instruction(SBC_IMM, 0x4F),
            CpuSetup()
                .a(0x50)
                .carry(false),
            ExpectedStepOutcome(cycles = 2, a = 0x00, carry = true, zero = true, negative = false, overflow = false)
        )
        testStep(
            "signed overflow positive minus negative",
            Instruction(SBC_IMM, 0xB0),
            CpuSetup()
                .a(0x50)
                .carry(true),
            ExpectedStepOutcome(cycles = 2, a = 0xA0, carry = false, zero = false, negative = true, overflow = true)
        )
        testStep(
            "signed overflow negative minus positive",
            Instruction(SBC_IMM, 0x70),
            CpuSetup()
                .a(0xD0)
                .carry(true),
            ExpectedStepOutcome(cycles = 2, a = 0x60, carry = true, zero = false, negative = false, overflow = true)
        )
    }
    "SBC zero page" - {
        testStep(
            "reads from zero page",
            Instruction(SBC_ZP, 0x10),
            CpuSetup()
                .a(0x30)
                .carry(true)
                .mem(0x10, 0x10),
            ExpectedStepOutcome(cycles = 3, a = 0x20, carry = true, zero = false, negative = false, overflow = false)
        )
    }
    "SBC zero page,X" - {
        testStep(
            "indexed",
            Instruction(SBC_ZPX, 0x10),
            CpuSetup()
                .a(0x30)
                .x(0x04)
                .carry(true)
                .mem(0x14, 0x10),
            ExpectedStepOutcome(cycles = 4, a = 0x20, carry = true, zero = false, negative = false, overflow = false)
        )
        testStep(
            "zero page wraps",
            Instruction(SBC_ZPX, 0xFF),
            CpuSetup()
                .a(0x30)
                .x(0x02)
                .carry(true)
                .mem(0x01, 0x10),
            ExpectedStepOutcome(cycles = 4, a = 0x20, carry = true, zero = false, negative = false, overflow = false)
        )
    }
    "SBC absolute" - {
        testStep(
            "reads from 16-bit address",
            Instruction(SBC_ABS, 0x00, 0x02),
            CpuSetup()
                .a(0x50)
                .carry(true)
                .mem(0x0200, 0x10),
            ExpectedStepOutcome(cycles = 4, a = 0x40, carry = true, zero = false, negative = false, overflow = false)
        )
    }
    "SBC absolute,X" - {
        testStep(
            "no page cross",
            Instruction(SBC_ABX, 0x00, 0x02),
            CpuSetup()
                .a(0x50)
                .x(0x01)
                .carry(true)
                .mem(0x0201, 0x10),
            ExpectedStepOutcome(cycles = 4, a = 0x40, carry = true, zero = false, negative = false, overflow = false)
        )
        testStep(
            "page cross adds cycle",
            Instruction(SBC_ABX, 0xFF, 0x01),
            CpuSetup()
                .a(0x50)
                .x(0x01)
                .carry(true)
                .mem(0x0200, 0x10),
            ExpectedStepOutcome(cycles = 5, a = 0x40, carry = true, zero = false, negative = false, overflow = false)
        )
    }
    "SBC absolute,Y" - {
        testStep(
            "no page cross",
            Instruction(SBC_ABY, 0x00, 0x02),
            CpuSetup()
                .a(0x50)
                .y(0x01)
                .carry(true)
                .mem(0x0201, 0x10),
            ExpectedStepOutcome(cycles = 4, a = 0x40, carry = true, zero = false, negative = false, overflow = false)
        )
        testStep(
            "page cross adds cycle",
            Instruction(SBC_ABY, 0xFF, 0x01),
            CpuSetup()
                .a(0x50)
                .y(0x01)
                .carry(true)
                .mem(0x0200, 0x10),
            ExpectedStepOutcome(cycles = 5, a = 0x40, carry = true, zero = false, negative = false, overflow = false)
        )
    }
    "SBC (indirect,X)" - {
        testStep(
            "reads via indexed indirect",
            Instruction(SBC_INX, 0x20),
            CpuSetup()
                .a(0x50)
                .x(0x04)
                .carry(true)
                .mem(0x24, 0x00, 0x03)
                .mem(0x0300, 0x10),
            ExpectedStepOutcome(cycles = 6, a = 0x40, carry = true, zero = false, negative = false, overflow = false)
        )
    }
    "SBC (indirect),Y" - {
        testStep(
            "no page cross",
            Instruction(SBC_INY, 0x20),
            CpuSetup()
                .a(0x50)
                .y(0x01)
                .carry(true)
                .mem(0x20, 0x00, 0x03)
                .mem(0x0301, 0x10),
            ExpectedStepOutcome(cycles = 5, a = 0x40, carry = true, zero = false, negative = false, overflow = false)
        )
        testStep(
            "page cross adds cycle",
            Instruction(SBC_INY, 0x20),
            CpuSetup()
                .a(0x50)
                .y(0x01)
                .carry(true)
                .mem(0x20, 0xFF, 0x02)
                .mem(0x0300, 0x10),
            ExpectedStepOutcome(cycles = 6, a = 0x40, carry = true, zero = false, negative = false, overflow = false)
        )
    }

    // ── NOP ──────────────────────────────────────────────────────────────
    "NOP implied" - {
        testStep(
            "does nothing",
            Instruction(NOP),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 2)
        )
    }
})
