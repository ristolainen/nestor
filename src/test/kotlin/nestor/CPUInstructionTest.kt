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
    }
    "TXA implied" - {
        testStep(
            "transfers X to A",
            Instruction(TXA),
            CpuSetup()
                .x(0x42),
            ExpectedStepOutcome(cycles = 2, a = 0x42, zero = false, negative = false)
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
