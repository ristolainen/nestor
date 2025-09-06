package nestor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe

class CPUTest : FreeSpec({
    fun setupCpuWithInstruction(
        vararg bytes: Int, // opcode + operands
        address: Int = 0x8000,
        ppu: PPU = PPU(emptyList())
    ): CPU {
        val prgRom = ByteArray(0x4000)
        val offset = address - 0x8000

        // Write the instruction bytes to the PRG-ROM at the desired address
        bytes.forEachIndexed { i, byte ->
            prgRom[offset + i] = byte.toByte()
        }

        // Set reset vector to point to the instruction address
        prgRom[0x3FFC] = (address and 0xFF).toByte()         // Low byte
        prgRom[0x3FFD] = ((address shr 8) and 0xFF).toByte() // High byte

        val memory = MemoryBus(ppu, prgRom)
        val cpu = CPU(memory)
        cpu.reset()

        return cpu
    }

    "SEI instruction" - {
        "should set the interrupt disable flag" {
            val cpu = setupCpuWithInstruction(0x78)
            cpu.status = 0b00000000

            val cycles = cpu.step()

            cpu.status shouldBe FLAG_INTERRUPT_DISABLE
            cycles shouldBe 2
        }
    }

    "CLD instruction" - {
        "should clear the decimal mode flag" {
            val cpu = setupCpuWithInstruction(0xD8)
            cpu.status = FLAG_DECIMAL

            val cycles = cpu.step()

            cpu.status shouldBe 0
            cycles shouldBe 2
        }
    }

    "Immediate loads (LDA/LDX/LDY)" - {
        "should load and set Z/N correctly" {
            forAll(
                // label, opcode, imm, targetReg, expectedZ, expectedN, initialStatus
                row("LDA non-zero", 0xA9, 0x42, 'A', 0, 0, 0),
                row("LDA zero", 0xA9, 0x00, 'A', FLAG_ZERO, 0, 0),
                row("LDA neg", 0xA9, 0x80, 'A', 0, FLAG_NEGATIVE, 0),
                row("LDA clear", 0xA9, 0x10, 'A', 0, 0, FLAG_ZERO or FLAG_NEGATIVE),

                row("LDX non-zero", 0xA2, 0x42, 'X', 0, 0, 0),
                row("LDX zero", 0xA2, 0x00, 'X', FLAG_ZERO, 0, 0),
                row("LDX neg", 0xA2, 0x80, 'X', 0, FLAG_NEGATIVE, 0),
                row("LDX clear", 0xA2, 0x10, 'X', 0, 0, FLAG_ZERO or FLAG_NEGATIVE),

                row("LDY non-zero", 0xA0, 0x33, 'Y', 0, 0, 0),
                row("LDY zero", 0xA0, 0x00, 'Y', FLAG_ZERO, 0, 0),
                row("LDY neg", 0xA0, 0x80, 'Y', 0, FLAG_NEGATIVE, 0),
                row("LDY clear", 0xA0, 0x10, 'Y', 0, 0, FLAG_ZERO or FLAG_NEGATIVE),
            ) { _, opcode, imm, target, expectedZ, expectedN, initialStatus ->
                val cpu = setupCpuWithInstruction(opcode, imm)
                cpu.status = initialStatus

                val cycles = cpu.step()

                val reg = when (target) {
                    'A' -> cpu.a
                    'X' -> cpu.x
                    'Y' -> cpu.y
                    else -> error("Unknown target $target")
                }
                reg shouldBe imm
                (cpu.status and FLAG_ZERO) shouldBe expectedZ
                (cpu.status and FLAG_NEGATIVE) shouldBe expectedN
                cycles shouldBe 2
            }
        }
    }

    "Absolute loads (LDA/LDX/LDY)" - {
        "should load from absolute address and set Z/N correctly" {
            io.kotest.data.forAll(
                // label, opcode, addrLo, addrHi, memVal, targetReg, expectedZ, expectedN, initialStatus
                row("LDA non-zero", 0xAD, 0x10, 0x00, 0x42, 'A', 0, 0, 0),
                row("LDA zero", 0xAD, 0x20, 0x00, 0x00, 'A', FLAG_ZERO, 0, 0),
                row("LDA neg", 0xAD, 0x30, 0x00, 0x80, 'A', 0, FLAG_NEGATIVE, 0),
                row("LDA clear", 0xAD, 0x40, 0x00, 0x10, 'A', 0, 0, FLAG_ZERO or FLAG_NEGATIVE),

                row("LDX non-zero", 0xAE, 0x50, 0x00, 0x42, 'X', 0, 0, 0),
                row("LDX zero", 0xAE, 0x60, 0x00, 0x00, 'X', FLAG_ZERO, 0, 0),
                row("LDX neg", 0xAE, 0x70, 0x00, 0x80, 'X', 0, FLAG_NEGATIVE, 0),
                row("LDX clear", 0xAE, 0x80, 0x00, 0x10, 'X', 0, 0, FLAG_ZERO or FLAG_NEGATIVE),

                row("LDY non-zero", 0xAC, 0x90, 0x00, 0x33, 'Y', 0, 0, 0),
                row("LDY zero", 0xAC, 0xA0, 0x00, 0x00, 'Y', FLAG_ZERO, 0, 0),
                row("LDY neg", 0xAC, 0xB0, 0x00, 0x80, 'Y', 0, FLAG_NEGATIVE, 0),
                row("LDY clear", 0xAC, 0xC0, 0x00, 0x10, 'Y', 0, 0, FLAG_ZERO or FLAG_NEGATIVE),
            ) { _, opcode, lo, hi, memVal, target, expectedZ, expectedN, initialStatus ->
                val absAddr = (hi shl 8) or lo

                // Place instruction (opcode + 16-bit address) at PC
                val cpu = setupCpuWithInstruction(opcode, lo, hi)

                // Seed the absolute address in CPU RAM with the value we expect to load.
                // MemoryBus.write() maps $0000-$1FFF to internal RAM (mirrored), so this works for our addresses.
                cpu.memory.write(absAddr, memVal)

                // Set starting status flags
                cpu.status = initialStatus

                val cycles = cpu.step()

                val reg = when (target) {
                    'A' -> cpu.a
                    'X' -> cpu.x
                    'Y' -> cpu.y
                    else -> error("Unknown target $target")
                }

                reg shouldBe memVal
                (cpu.status and FLAG_ZERO) shouldBe expectedZ
                (cpu.status and FLAG_NEGATIVE) shouldBe expectedN
                cycles shouldBe 4
            }
        }
    }

    "Absolute,X loads (LDA)" - {
        "should load from absolute,X and set Z/N correctly, without page cross" {
            io.kotest.data.forAll(
                // label, addrLo, addrHi, X, memVal, expectedZ, expectedN, initialStatus
                row("non-zero", 0x10, 0x00, 0x05, 0x42, 0, 0, 0),
                row("zero", 0x20, 0x00, 0x00, 0x00, FLAG_ZERO, 0, 0),
                row("neg", 0x30, 0x00, 0x01, 0x80, 0, FLAG_NEGATIVE, 0),
                row("clear", 0x40, 0x00, 0x00, 0x10, 0, 0, FLAG_ZERO or FLAG_NEGATIVE),
            ) { _, lo, hi, xVal, memVal, expectedZ, expectedN, initialStatus ->
                val base = (hi shl 8) or lo
                val eff = (base + xVal) and 0xFFFF

                val cpu = setupCpuWithInstruction(0xBD, lo, hi) // LDA abs,X
                cpu.memory.write(eff, memVal)                  // MemoryBus maps $0000-$1FFF to RAM
                cpu.x = xVal
                cpu.status = initialStatus

                val cycles = cpu.step()

                cpu.a shouldBe memVal
                (cpu.status and FLAG_ZERO) shouldBe expectedZ
                (cpu.status and FLAG_NEGATIVE) shouldBe expectedN
                cycles shouldBe 4
            }
        }

        "should add +1 cycle on page cross" {
            val lo = 0xFF;
            val hi = 0x00;
            val xVal = 0x01  // $00FF + 1 => $0100 (crosses page)
            val base = (hi shl 8) or lo
            val eff = (base + xVal) and 0xFFFF

            val cpu = setupCpuWithInstruction(0xBD, lo, hi)
            cpu.memory.write(eff, 0x2A)
            cpu.x = xVal

            val cycles = cpu.step()

            cpu.a shouldBe 0x2A
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
            cycles shouldBe 5  // extra cycle due to page cross
        }
    }

    "Absolute,Y loads (LDA)" - {
        "should load from absolute,Y and set Z/N correctly without page cross" {
            io.kotest.data.forAll(
                // label, baseLo, baseHi, Y, memVal, expectedZ, expectedN, initialStatus
                row("non-zero", 0x10, 0x00, 0x05, 0x42, 0, 0, 0),
                row("zero", 0x20, 0x00, 0x00, 0x00, FLAG_ZERO, 0, 0),
                row("neg", 0x30, 0x00, 0x01, 0x80, 0, FLAG_NEGATIVE, 0),
                row("clear", 0x40, 0x00, 0x00, 0x10, 0, 0, FLAG_ZERO or FLAG_NEGATIVE),
            ) { _, lo, hi, yVal, memVal, expectedZ, expectedN, initialStatus ->
                val base = (hi shl 8) or lo
                val eff = (base + yVal) and 0xFFFF

                val cpu = setupCpuWithInstruction(0xB9, lo, hi) // LDA abs,Y
                cpu.memory.write(eff, memVal)
                cpu.y = yVal
                cpu.status = initialStatus

                val cycles = cpu.step()

                cpu.a shouldBe memVal
                (cpu.status and FLAG_ZERO) shouldBe expectedZ
                (cpu.status and FLAG_NEGATIVE) shouldBe expectedN
                cycles shouldBe 4 // no page cross
            }
        }

        "should add +1 cycle on page cross" {
            val lo = 0xFF;
            val hi = 0x00;
            val yVal = 0x01  // base=$00FF, eff=$0100 (page cross in RAM)
            val base = (hi shl 8) or lo
            val eff = (base + yVal) and 0xFFFF

            val cpu = setupCpuWithInstruction(0xB9, lo, hi) // LDA abs,Y
            cpu.memory.write(eff, 0x2A)                     // writes to CPU RAM
            cpu.y = yVal

            val cycles = cpu.step()

            cpu.a shouldBe 0x2A
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
            cycles shouldBe 5  // extra cycle due to page cross
        }
    }

    "Branches (BPL/BMI/BEQ/BNE/BCS/BCC/BVS/BVC)" - {
        fun targetPc(startPC: Int, offset: Int): Int {
            val nextPC = (startPC + 2) and 0xFFFF
            val off = offset.toByte().toInt()           // sign-extend [-128,127]
            return (nextPC + off) and 0xFFFF
        }
        forAll(
            // label, opcode, initialStatus, offset, startPC, expectTaken, expectCycles

            // --- BPL (branch if !N) / BMI (branch if N) ---
            row("BPL not taken (N set)", 0x10, FLAG_NEGATIVE, 0x05, 0x8000, false, 2),
            row("BPL taken no cross", 0x10, 0, 0x05, 0x8000, true, 3),

            // page-cross downward: nextPC=$8102, target=$80FC
            row("BMI taken page cross", 0x30, FLAG_NEGATIVE, 0xFA, 0x8100, true, 4),

            // --- BEQ (branch if Z) / BNE (branch if !Z) ---
            row("BEQ taken no cross", 0xF0, FLAG_ZERO, 0x05, 0x8080, true, 3),
            row("BNE not taken (Z set)", 0xD0, FLAG_ZERO, 0x20, 0x8090, false, 2),

            // --- BCS (branch if C) / BCC (branch if !C) ---
            row("BCS taken no cross", 0xB0, FLAG_CARRY, 0x01, 0x8100, true, 3),

            // page-cross upward: nextPC=$80FF, target=$8100
            row("BCC taken page cross", 0x90, 0, 0x01, 0x80FD, true, 4),

            // --- BVS (branch if V) / BVC (branch if !V) ---
            row("BVS taken no cross", 0x70, FLAG_OVERFLOW, 0x10, 0x80A0, true, 3),
            row("BVC not taken (V set)", 0x50, FLAG_OVERFLOW, 0x10, 0x80A0, false, 2),
        ) { label, opcode, initialStatus, offset, startPC, expectTaken, expectCycles ->

            val cpu = setupCpuWithInstruction(opcode, offset, address = startPC)
            cpu.status = initialStatus

            val cycles =
                cpu.step() // fetch via readNextByte(); branch helper decides & maybe updates pc:contentReference[oaicite:1]{index=1}

            val expectedPc = if (expectTaken) targetPc(startPC, offset) else (startPC + 2) and 0xFFFF
            cpu.pc shouldBe expectedPc
            cycles shouldBe expectCycles
        }
    }

    "STA absolute instruction" - {
        "should write A to a CPU RAM address and not touch flags" {
            // Program: 8D 42 00  (STA $0042)
            val cpu = setupCpuWithInstruction(0x8D, 0x42, 0x00)
            cpu.a = 0xAB
            cpu.status = FLAG_ZERO or FLAG_NEGATIVE // pre-set some flags

            val cycles = cpu.step()

            // Value stored to $0042 (CPU RAM)
            cpu.memory.read(0x0042) shouldBe 0xAB
            // Flags unchanged
            cpu.status and FLAG_ZERO shouldBe FLAG_ZERO
            cpu.status and FLAG_NEGATIVE shouldBe FLAG_NEGATIVE
            // Cycles for STA abs = 4
            cycles shouldBe 4
        }
    }

    "Store instructions — zero page variants" - {

        data class StoreCase(
            val label: String,
            val opcode: Int,
            val baseZp: Int,
            val indexReg: Char?,   // 'X', 'Y', or null (no index)
            val indexVal: Int,     // used when indexReg != null
            val srcReg: Char,      // 'A', 'X', or 'Y'
            val srcVal: Int,
            val expectedCycles: Int
        )

        "STA/ STX/ STY cover zp and indexed zp (wrap-around too)" {
            forAll(
                // ---- STA ----
                row(StoreCase("STA zp ($10)", 0x85, 0x10, null, 0x00, 'A', 0x42, 3)),
                row(StoreCase("STA zp,X ($80 + X)", 0x95, 0x80, 'X', 0x05, 'A', 0x7F, 4)),
                row(StoreCase("STA zp,X wrap (FE+X)", 0x95, 0xFE, 'X', 0x05, 'A', 0x99, 4)), // eff = 0x03

                // ---- STX ----
                row(StoreCase("STX zp ($20)", 0x86, 0x20, null, 0x00, 'X', 0x33, 3)),
                row(StoreCase("STX zp,Y ($40 + Y)", 0x96, 0x40, 'Y', 0x0A, 'X', 0x55, 4)),
                row(StoreCase("STX zp,Y wrap (FF+Y)", 0x96, 0xFF, 'Y', 0x02, 'X', 0xAB, 4)), // eff = 0x01

                // ---- STY ----
                row(StoreCase("STY zp ($00)", 0x84, 0x00, null, 0x00, 'Y', 0x11, 3)),
                row(StoreCase("STY zp,X ($7F + X)", 0x94, 0x7F, 'X', 0x02, 'Y', 0xC4, 4)),
                row(StoreCase("STY zp,X wrap (FD+X)", 0x94, 0xFD, 'X', 0x07, 'Y', 0x80, 4))  // eff = 0x04
            ) { tc ->

                // Program: [opcode, baseZp]
                val cpu = setupCpuWithInstruction(tc.opcode, tc.baseZp)

                // Set source register
                when (tc.srcReg) {
                    'A' -> cpu.a = tc.srcVal
                    'X' -> cpu.x = tc.srcVal
                    'Y' -> cpu.y = tc.srcVal
                    else -> error("Unexpected srcReg ${tc.srcReg}")
                }

                // Set index register if used
                tc.indexReg?.let {
                    when (it) {
                        'X' -> cpu.x = tc.indexVal
                        'Y' -> cpu.y = tc.indexVal
                        else -> error("Unexpected indexReg $it")
                    }
                }

                val cycles = cpu.step()

                // Compute effective zero-page address with 8-bit wrap
                val eff = when (tc.indexReg) {
                    'X' -> (tc.baseZp + cpu.x) and 0xFF
                    'Y' -> (tc.baseZp + cpu.y) and 0xFF
                    else -> tc.baseZp
                }

                val expected = when (tc.srcReg) {
                    'A' -> cpu.a
                    'X' -> cpu.x
                    'Y' -> cpu.y
                    else -> error("Unexpected srcReg ${tc.srcReg}")
                }

                cpu.memory.read(eff) shouldBe expected
                cycles shouldBe tc.expectedCycles
            }
        }
    }

    "STA zero page instruction" - {
        "should store the accumulator into zero page memory" {
            val cpu = setupCpuWithInstruction(0x85, 0x10) // STA $10
            cpu.a = 0x42

            val cycles = cpu.step()

            cpu.memory.read(0x0010) shouldBe 0x42
            cycles shouldBe 3
        }

        "should overwrite existing value in zero page" {
            val cpu = setupCpuWithInstruction(0x85, 0x80) // STA $80
            cpu.memory.write(0x0080, 0x99) // existing value
            cpu.a = 0x55

            val cycles = cpu.step()

            cpu.memory.read(0x0080) shouldBe 0x55
            cycles shouldBe 3
        }
    }

    "TXS instruction" - {
        "should transfer X to stack pointer without affecting flags" {
            val cpu = setupCpuWithInstruction(0x9A) // TXS opcode
            cpu.x = 0x42
            cpu.sp = 0x00
            val originalStatus = cpu.status

            val cycles = cpu.step()

            cpu.sp shouldBe 0x42
            cpu.status shouldBe originalStatus // flags unchanged
            cycles shouldBe 2
        }
    }

    "TXA instruction" - {
        "should transfer X into A" {
            val cpu = setupCpuWithInstruction(0x8A) // TXA
            cpu.x = 0x42

            val cycles = cpu.step()

            cpu.a shouldBe 0x42
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
            cycles shouldBe 2
        }

        "should set ZERO flag when result is 0" {
            val cpu = setupCpuWithInstruction(0x8A)
            cpu.x = 0x00

            val cycles = cpu.step()

            cpu.a shouldBe 0x00
            (cpu.status and FLAG_ZERO) shouldBe FLAG_ZERO
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
            cycles shouldBe 2
        }

        "should set NEGATIVE flag when bit 7 is set" {
            val cpu = setupCpuWithInstruction(0x8A)
            cpu.x = 0x80

            val cycles = cpu.step()

            cpu.a shouldBe 0x80
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe FLAG_NEGATIVE
            cycles shouldBe 2
        }
    }

    "TYA instruction" - {
        "should transfer Y into A" {
            val cpu = setupCpuWithInstruction(0x98) // TYA
            cpu.y = 0x42

            val cycles = cpu.step()

            cpu.a shouldBe 0x42
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
            cycles shouldBe 2
        }

        "should set ZERO flag when result is 0" {
            val cpu = setupCpuWithInstruction(0x98)
            cpu.y = 0x00

            val cycles = cpu.step()

            cpu.a shouldBe 0x00
            (cpu.status and FLAG_ZERO) shouldBe FLAG_ZERO
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
            cycles shouldBe 2
        }

        "should set NEGATIVE flag when bit 7 is set" {
            val cpu = setupCpuWithInstruction(0x98)
            cpu.y = 0x80

            val cycles = cpu.step()

            cpu.a shouldBe 0x80
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe FLAG_NEGATIVE
            cycles shouldBe 2
        }
    }

    "Compare immediate (CMP/CPX/CPY)" - {

        fun setup(loadOp: Int, regVal: Int, cmpOp: Int, imm: Int): Triple<CPU, Int, Int> {
            // e.g. LDA #regVal ; CMP #imm  or  LDX #regVal ; CPX #imm, etc.
            val cpu = setupCpuWithInstruction(loadOp, regVal, cmpOp, imm)
            val cycles = cpu.step() + cpu.step()
            return Triple(cpu, imm, cycles)
        }

        // Rows: label, load opcode, compare opcode, reg selector, reg value, imm, expected C, expected Z, expected N
        io.kotest.data.forAll(
            // ----- A with CMP ($C9) -----
            row(
                "A > M sets C, !Z, !N",
                0xA9, 0xC9, 'A', 0x42, 0x40, FLAG_CARRY, 0, 0
            ),
            row(
                "A == M sets C and Z, !N",
                0xA9, 0xC9, 'A', 0x42, 0x42, FLAG_CARRY, FLAG_ZERO, 0
            ),
            row(
                "A < M clears C/Z, sets N from (A-M)",
                0xA9, 0xC9, 'A', 0x40, 0x42, 0, 0, FLAG_NEGATIVE
            ),
            row(
                "A borrow: 0x00-0x01 → 0xFF sets N, clears C/Z",
                0xA9, 0xC9, 'A', 0x00, 0x01, 0, 0, FLAG_NEGATIVE
            ),

            // ----- X with CPX ($E0) -----
            row(
                "X > M sets C, !Z, !N",
                0xA2, 0xE0, 'X', 0x42, 0x40, FLAG_CARRY, 0, 0
            ),
            row(
                "X == M sets C and Z, !N",
                0xA2, 0xE0, 'X', 0x42, 0x42, FLAG_CARRY, FLAG_ZERO, 0
            ),
            row(
                "X < M clears C/Z, sets N",
                0xA2, 0xE0, 'X', 0x40, 0x42, 0, 0, FLAG_NEGATIVE
            ),
            row(
                "X borrow: 0x00-0x01 → N, !C, !Z",
                0xA2, 0xE0, 'X', 0x00, 0x01, 0, 0, FLAG_NEGATIVE
            ),

            // ----- Y with CPY ($C0) -----
            row(
                "Y > M sets C, !Z, !N",
                0xA0, 0xC0, 'Y', 0x42, 0x40, FLAG_CARRY, 0, 0
            ),
            row(
                "Y == M sets C and Z, !N",
                0xA0, 0xC0, 'Y', 0x42, 0x42, FLAG_CARRY, FLAG_ZERO, 0
            ),
            row(
                "Y < M clears C/Z, sets N",
                0xA0, 0xC0, 'Y', 0x40, 0x42, 0, 0, FLAG_NEGATIVE
            ),
            row(
                "Y borrow: 0x00-0x01 → N, !C, !Z",
                0xA0, 0xC0, 'Y', 0x00, 0x01, 0, 0, FLAG_NEGATIVE
            ),
        ) { label, loadOp, cmpOp, reg, regVal, imm, expC, expZ, expN ->

            label {
                val (cpu, _, cycles) = setup(loadOp, regVal, cmpOp, imm)

                // Flags
                (cpu.status and FLAG_CARRY) shouldBe expC
                (cpu.status and FLAG_ZERO) shouldBe expZ
                (cpu.status and FLAG_NEGATIVE) shouldBe expN

                // Register not modified by compare
                when (reg) {
                    'A' -> cpu.a shouldBe regVal
                    'X' -> cpu.x shouldBe regVal
                    'Y' -> cpu.y shouldBe regVal
                }

                // 2 (LD*) + 2 (CP*) = 4 cycles
                cycles shouldBe 4
            }
        }
    }

    "Increment and decrement X and Y" - {
        fun setReg(cpu: CPU, reg: Char, v: Int) {
            when (reg) {
                'X' -> cpu.x = v
                'Y' -> cpu.y = v
                else -> error("reg must be 'X' or 'Y'")
            }
        }

        fun getReg(cpu: CPU, reg: Char): Int =
            when (reg) {
                'X' -> cpu.x
                'Y' -> cpu.y
                else -> error("reg must be 'X' or 'Y'")
            }

        "DEX/DEY/INX/INY behave correctly (wrap + Z/N flags)" {
            forAll(
                // label, opcode, reg, start, expected, expectZ, expectN
                row("DEX: dec 0x05 -> 0x04", 0xCA, 'X', 0x05, 0x04, 0, 0),
                row("DEX: wrap 0x00 -> 0xFF", 0xCA, 'X', 0x00, 0xFF, 0, FLAG_NEGATIVE),
                row("DEX: 0x01 -> 0x00 sets Z", 0xCA, 'X', 0x01, 0x00, FLAG_ZERO, 0),
                row("DEX: 0x81 -> 0x80 sets N", 0xCA, 'X', 0x81, 0x80, 0, FLAG_NEGATIVE),

                row("DEY: dec 0x15 -> 0x14", 0x88, 'Y', 0x15, 0x14, 0, 0),
                row("DEY: wrap 0x00 -> 0xFF", 0x88, 'Y', 0x00, 0xFF, 0, FLAG_NEGATIVE),
                row("DEY: 0x01 -> 0x00 sets Z", 0x88, 'Y', 0x01, 0x00, FLAG_ZERO, 0),
                row("DEY: 0x81 -> 0x80 sets N", 0x88, 'Y', 0x81, 0x80, 0, FLAG_NEGATIVE),

                row("INX: inc 0x05 -> 0x06", 0xE8, 'X', 0x05, 0x06, 0, 0),
                row("INX: wrap 0xFF -> 0x00 sets Z", 0xE8, 'X', 0xFF, 0x00, FLAG_ZERO, 0),
                row("INX: 0x7F -> 0x80 sets N", 0xE8, 'X', 0x7F, 0x80, 0, FLAG_NEGATIVE),

                row("INY: inc 0x05 -> 0x06", 0xC8, 'Y', 0x05, 0x06, 0, 0),
                row("INY: wrap 0xFF -> 0x00 sets Z", 0xC8, 'Y', 0xFF, 0x00, FLAG_ZERO, 0),
                row("INY: 0x7F -> 0x80 sets N", 0xC8, 'Y', 0x7F, 0x80, 0, FLAG_NEGATIVE),
            ) { label, opcode, reg, start, expected, expectZ, expectN ->
                val cpu = setupCpuWithInstruction(opcode)
                cpu.status = 0 // start clean
                setReg(cpu, reg, start)

                val cycles = cpu.step()

                getReg(cpu, reg) shouldBe expected
                (cpu.status and FLAG_ZERO) shouldBe expectZ
                (cpu.status and FLAG_NEGATIVE) shouldBe expectN
                cycles shouldBe 2
            }
        }
    }

    "JSR absolute" - {
        "pushes (PC-1) and jumps; 6 cycles" {
            // opcode at $8000, operand = $C123
            val cpu = setupCpuWithInstruction(0x20, 0x23, 0xC1)
            cpu.sp = 0xFF

            val cycles = cpu.step()

            cpu.pc shouldBe 0xC123
            cpu.sp shouldBe 0xFD                       // pushed two bytes

            // top of stack has high then low of (PC-1) = $8002
            cpu.memory.read(0x01FF) shouldBe 0x80      // high
            cpu.memory.read(0x01FE) shouldBe 0x02      // low
            cycles shouldBe 6
        }
    }

    "RTS instruction" - {

        "returns to the instruction after JSR; pulls two bytes; 6 cycles" {
            // Arrange: opcode RTS at $8000
            val cpu = setupCpuWithInstruction(0x60)
            // Simulate post-JSR stack: JSR pushed (PC-1) = $8002
            cpu.sp = 0xFD
            cpu.memory.write(0x01FE, 0x02)   // low byte
            cpu.memory.write(0x01FF, 0x80)   // high byte

            val originalStatus = 0b10100101
            cpu.status = originalStatus

            // Act
            val cycles = cpu.step()

            // Assert
            cpu.pc shouldBe 0x8003            // incremented to AFTER the JSR
            cpu.sp shouldBe 0xFF              // pulled two bytes
            cpu.status shouldBe originalStatus // RTS does not affect flags
            cycles shouldBe 6
        }

        "increments across page boundary (e.g., $80FF -> $8100)" {
            val cpu = setupCpuWithInstruction(0x60)
            cpu.sp = 0xFD
            // Return address on stack = $80FF; RTS should set PC to $8100
            cpu.memory.write(0x01FE, 0xFF)   // low
            cpu.memory.write(0x01FF, 0x80)   // high

            val cycles = cpu.step()

            cpu.pc shouldBe 0x8100
            cpu.sp shouldBe 0xFF
            cycles shouldBe 6
        }

        "does not corrupt extra stack bytes (only two pulls)" {
            val cpu = setupCpuWithInstruction(0x60)
            cpu.sp = 0xFC
            // Stack layout:
            // 0x01FD : sentinel
            // 0x01FE : low of return
            // 0x01FF : high of return
            cpu.memory.write(0x01FD, 0xAA)
            cpu.memory.write(0x01FE, 0x02)
            cpu.memory.write(0x01FF, 0x80)

            cpu.step()

            // Only two pulls → SP ends at 0xFE after pulling 0x01FF, then 0xFF (so 0x01FD remains)
            cpu.sp shouldBe ((0xFC + 2) and 0xFF)
            // More explicit check: the sentinel still there
            cpu.memory.read(0x01FD) shouldBe 0xAA
        }
    }

    "STA (indirect),Y and (indirect,X)" - {

        "STA (indirect),Y stores A to (zp + Y) and takes 6 cycles" {
            val zp = 0x20
            val cpu = setupCpuWithInstruction(0x91, zp)
            cpu.a = 0xAB
            cpu.y = 0x05

            // Zero-page pointer at $0020/$0021 -> base $1234
            cpu.memory.write(0x0020, 0x34) // low
            cpu.memory.write(0x0021, 0x12) // high

            val cycles = cpu.step()

            // Effective address = $1234 + Y(5) = $1239
            cpu.memory.read(0x1239) shouldBe 0xAB
            cycles shouldBe 6
        }

        "STA (indirect),Y pointer wrap at $00FF/$0000" {
            val zp = 0xFF
            val cpu = setupCpuWithInstruction(0x91, zp)
            cpu.a = 0x7E
            cpu.y = 0x01

            // Base pointer uses ZP wrap for high byte: $00FF (lo), $0000 (hi)
            cpu.memory.write(0x00FF, 0xF0) // low
            cpu.memory.write(0x0000, 0x10) // high  (wrap from $FF to $00)
            // Base = $10F0, plus Y(1) -> $10F1

            val cycles = cpu.step()

            cpu.memory.read(0x10F1) shouldBe 0x7E
            cycles shouldBe 6
        }

        "STA (indirect,X) uses pointer at (zp + X) & 0xFF; stores to $0205; 6 cycles" {
            val zp = 0x10
            val cpu = setupCpuWithInstruction(0x81, zp)
            cpu.a = 0x55
            cpu.x = 0x0A

            // (0x10 + 0x0A) & 0xFF = $001A -> pointer bytes = $0205
            cpu.memory.write(0x001A, 0x05) // low
            cpu.memory.write(0x001B, 0x02) // high

            val cycles = cpu.step()

            cpu.memory.read(0x0205) shouldBe 0x55
            cycles shouldBe 6
        }

        "STA (indirect,X) ZP wrap: (0xFE + 0x05) -> $0003/$0004 -> $0210" {
            val zp = 0xFE
            val cpu = setupCpuWithInstruction(0x81, zp)
            cpu.a = 0x99
            cpu.x = 0x05

            // Pointer at $0003/$0004 = $0210
            cpu.memory.write(0x0003, 0x10) // low
            cpu.memory.write(0x0004, 0x02) // high

            val cycles = cpu.step()

            cpu.memory.read(0x0210) shouldBe 0x99
            cycles shouldBe 6
        }
    }

    "BIT absolute" - {

        fun setup(aVal: Int, memVal: Int): Triple<CPU, Int, Int> {
            // Program: LDA #aVal ; BIT $1234
            val cpu = setupCpuWithInstruction(0xA9, aVal, 0x2C, 0x34, 0x12)
            cpu.memory.write(0x1234, memVal)
            val cycles = cpu.step() + cpu.step()
            return Triple(cpu, memVal, cycles)
        }

        "sets Z when (A & M) == 0" {
            val (cpu, _, cycles) = setup(0x0F, 0xF0) // 0x0F & 0xF0 == 0
            (cpu.status and FLAG_ZERO) shouldBe FLAG_ZERO
            cycles shouldBe 2 /* LDA #imm */ + 4 /* BIT abs */
        }

        "clears Z when (A & M) != 0" {
            val (cpu, _, _) = setup(0x0F, 0x0F)
            (cpu.status and FLAG_ZERO) shouldBe 0
        }

        "copies bit 7 of M into N" {
            val (cpu1, _, _) = setup(0xFF, 0b1000_0000)
            (cpu1.status and FLAG_NEGATIVE) shouldBe FLAG_NEGATIVE

            val (cpu2, _, _) = setup(0xFF, 0b0111_1111)
            (cpu2.status and FLAG_NEGATIVE) shouldBe 0
        }

        "copies bit 6 of M into V" {
            val (cpu1, _, _) = setup(0xFF, 0b0100_0000)
            (cpu1.status and FLAG_OVERFLOW) shouldBe FLAG_OVERFLOW

            val (cpu2, _, _) = setup(0xFF, 0b1011_1111)
            (cpu2.status and FLAG_OVERFLOW) shouldBe 0
        }
    }

    "STA absolute indexed (X/Y)" - {

        data class Case(
            val label: String,
            val opcode: Int,        // 0x9D = STA abs,X, 0x99 = STA abs,Y
            val indexReg: Char,     // 'X' or 'Y'
            val lo: Int,
            val hi: Int,
            val indexVal: Int,
            val aVal: Int
        )

        suspend fun runCase(c: Case) {
            c.label {
                // Program: LDA #aVal ; STA $hi$lo,index
                val cpu = setupCpuWithInstruction(0xA9, c.aVal, c.opcode, c.lo, c.hi)
                if (c.indexReg == 'X') cpu.x = c.indexVal else cpu.y = c.indexVal

                val cycles = cpu.step() + cpu.step()

                val base = (c.hi shl 8) or c.lo
                val eff = (base + c.indexVal) and 0xFFFF

                cpu.memory.read(eff) shouldBe c.aVal
                // LDA #imm = 2 cycles, STA abs,indexed = 5 cycles (no extra on page cross for stores)
                cycles shouldBe 7
            }
        }

        io.kotest.data.forAll(
            // -------- abs,X ----------
            row(Case("abs,X no page cross", 0x9D, 'X', 0x10, 0x10, 0x05, 0x3C)), // $1010 + 5 -> $2015
            row(Case("abs,X page cross", 0x9D, 'X', 0xFF, 0x10, 0x02, 0x7E)), // $10FF + 2 -> $2101

            // -------- abs,Y ----------
            row(Case("abs,Y no page cross", 0x99, 'Y', 0x10, 0x10, 0x05, 0x55)), // $1010 + 5 -> $1015
            row(Case("abs,Y page cross", 0x99, 'Y', 0xFF, 0x10, 0x02, 0xAA)), // $10FF + 2 -> $1101
        ) { c -> runCase(c) }
    }

    "ORA instruction" - {

        // Utility: write a value into CPU memory via the MemoryBus
        fun write(cpu: CPU, addr: Int, value: Int) = cpu.memory.write(addr, value)

        // 1) Result/flags across all addressing modes (no page cross)
        "should OR A with memory/immediate and set Z/N; correct base cycles" - {
            // Each row sets up: initial A, opcode stream, memory writes, and expected (A, Z, N, cycles)
            io.kotest.data.forAll(
                // label, bytes, setup, expectedA, expectedZ, expectedN, expectedCycles
                row(
                    "Immediate ($09)", intArrayOf(0x09, 0b0000_1010),
                    { cpu: CPU -> cpu.a = 0b0011_0000 },
                    0b0011_1010, 0, 0, 2
                ),
                row(
                    "Zero Page ($05)", intArrayOf(0x05, 0x10),
                    { cpu: CPU ->
                        cpu.a = 0b0100_0000
                        write(cpu, 0x0010, 0b0000_1111)
                    },
                    0b0100_1111, 0, 0, 3
                ),
                row(
                    "Zero Page,X ($15)", intArrayOf(0x15, 0x80),
                    { cpu: CPU ->
                        cpu.a = 0b0000_0000
                        cpu.x = 0x05
                        write(cpu, 0x0085, 0b1000_0000) // (0x80 + X) & 0xFF
                    },
                    0b1000_0000, 0, FLAG_NEGATIVE, 4
                ),
                row(
                    "Absolute ($0D)", intArrayOf(0x0D, 0x34, 0x20), // $2034
                    { cpu: CPU ->
                        cpu.a = 0b0000_0011
                        write(cpu, 0x2034, 0b0000_0000)
                    },
                    0b0000_0011, 0, 0, 4
                ),
                row(
                    "Absolute,X no cross ($1D)", intArrayOf(0x1D, 0xF0, 0x00), // base $20F0
                    { cpu: CPU ->
                        cpu.a = 0b0000_0011
                        cpu.x = 0x0E // eff $00FE (no cross)
                        write(cpu, 0x00FE, 0b1111_0000)
                    },
                    0b1111_0011, 0, FLAG_NEGATIVE, 4
                ),
                row(
                    "Absolute,Y no cross ($19)", intArrayOf(0x19, 0xF0, 0x00), // base $20F0
                    { cpu: CPU ->
                        cpu.a = 0
                        cpu.y = 0x0E // eff $00FE
                        write(cpu, 0x00FE, 0b0000_0000)
                    },
                    0, FLAG_ZERO, 0, 4
                ),
                row(
                    "(Indirect,X) ($01)", intArrayOf(0x01, 0x10),
                    { cpu: CPU ->
                        cpu.a = 0b0000_0101
                        cpu.x = 0x04
                        // zp idx = (0x10 + X) & 0xFF = 0x14 → pointer @ $0014/$0015
                        write(cpu, 0x0014, 0x78) // lo
                        write(cpu, 0x0015, 0x10) // hi → eff $1078
                        write(cpu, 0x1078, 0b1000_0000)
                    },
                    0b1000_0101, 0, FLAG_NEGATIVE, 6
                ),
                row(
                    "(Indirect),Y no cross ($11)", intArrayOf(0x11, 0x20),
                    { cpu: CPU ->
                        cpu.a = 0xFF
                        cpu.y = 0x00
                        // pointer @ $0020/$0021 = $0100; Y=0 → eff $0100 (CPU RAM)
                        write(cpu, 0x0020, 0x00) // lo
                        write(cpu, 0x0021, 0x01) // hi
                        write(cpu, 0x0100, 0x00)
                    },
                    0xFF, 0, FLAG_NEGATIVE, 5
                ),
            ) { label, bytes, setup, expectedA, expectedZ, expectedN, expectedCycles ->
                label {
                    val cpu = setupCpuWithInstruction(*bytes)
                    setup(cpu)

                    val cycles = cpu.step()

                    cpu.a shouldBe expectedA
                    (cpu.status and FLAG_ZERO) shouldBe expectedZ
                    (cpu.status and FLAG_NEGATIVE) shouldBe expectedN
                    cycles shouldBe expectedCycles
                }
            }
        }

        "should add +1 cycle on page cross for Absolute,X and Absolute,Y" {
            // Absolute,X cross: base $00FF + X=0x05 -> $0104
            run {
                val cpu = setupCpuWithInstruction(0xBD, 0xFF, 0x00) // LDA abs,X
                cpu.a = 0
                cpu.x = 0x05
                write(cpu, 0x0104, 0x01)

                val cycles = cpu.step()

                cpu.a shouldBe 0x01
                (cpu.status and FLAG_ZERO) shouldBe 0
                (cpu.status and FLAG_NEGATIVE) shouldBe 0
                cycles shouldBe 5
            }
            // Absolute,Y cross: base $00FE + Y=0x02 -> $0100
            run {
                val cpu = setupCpuWithInstruction(0xB9, 0xFE, 0x00) // LDA abs,Y
                cpu.a = 0
                cpu.y = 0x02
                write(cpu, 0x0100, 0x80)

                val cycles = cpu.step()

                cpu.a shouldBe 0x80
                (cpu.status and FLAG_ZERO) shouldBe 0
                (cpu.status and FLAG_NEGATIVE) shouldBe FLAG_NEGATIVE
                cycles shouldBe 5
            }
        }

        // 3) Page-cross penalty for (Indirect),Y
        "(Indirect),Y should add +1 cycle on page cross" {
            // Pointer at $0040/$0041 = $00FF; Y=+2 → eff $0101 (crosses $00xx → $01xx)
            val cpu = setupCpuWithInstruction(0x11, 0x40)
            cpu.a = 0
            cpu.y = 0x02
            write(cpu, 0x0040, 0xFF) // lo
            write(cpu, 0x0041, 0x00) // hi  → base = $00FF
            write(cpu, 0x0101, 0x7F) // effective addr after Y

            val cycles = cpu.step()

            cpu.a shouldBe 0x7F
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
            cycles shouldBe 6
        }
    }

    "AND instruction" - {

        // Utility: write a value into CPU memory via the MemoryBus
        fun write(cpu: CPU, addr: Int, value: Int) = cpu.memory.write(addr, value)

        // 1) Result/flags across all addressing modes (no page cross)
        "should AND A with memory/immediate and set Z/N; correct base cycles" - {
            io.kotest.data.forAll(
                // label, bytes, setup, expectedA, expectedZ, expectedN, expectedCycles

                row(
                    "Immediate ($29)", intArrayOf(0x29, 0b0000_1111),
                    { cpu: CPU -> cpu.a = 0b0011_1010 },
                    0b0000_1010, 0, 0, 2
                ),
                row(
                    "Zero Page ($25)", intArrayOf(0x25, 0x10),
                    { cpu: CPU ->
                        cpu.a = 0b0011_0011
                        write(cpu, 0x0010, 0b1111_0000)
                    },
                    0b0011_0000, 0, 0, 3
                ),
                row(
                    "Zero Page,X ($35)", intArrayOf(0x35, 0x80),
                    { cpu: CPU ->
                        cpu.a = 0xFF
                        cpu.x = 0x05
                        write(cpu, 0x0085, 0b1000_0000) // (0x80 + X) & 0xFF
                    },
                    0b1000_0000, 0, FLAG_NEGATIVE, 4
                ),
                row(
                    "Absolute ($2D)", intArrayOf(0x2D, 0x34, 0x20), // $2034
                    { cpu: CPU ->
                        cpu.a = 0b0000_0011
                        write(cpu, 0x2034, 0b0000_0000)
                    },
                    0b0000_0000, FLAG_ZERO, 0, 4
                ),
                row(
                    "Absolute,X no cross ($3D)", intArrayOf(0x3D, 0xF0, 0x00), // base $00F0
                    { cpu: CPU ->
                        cpu.a = 0b1111_0011
                        cpu.x = 0x0E // eff $00FE (no cross)
                        write(cpu, 0x00FE, 0b1111_0000)
                    },
                    0b1111_0000, 0, FLAG_NEGATIVE, 4
                ),
                row(
                    "Absolute,Y no cross ($39)", intArrayOf(0x39, 0xF0, 0x00), // base $00F0
                    { cpu: CPU ->
                        cpu.a = 0xFF
                        cpu.y = 0x0E // eff $00FE
                        write(cpu, 0x00FE, 0x00)
                    },
                    0x00, FLAG_ZERO, 0, 4
                ),
                row(
                    "(Indirect,X) ($21)", intArrayOf(0x21, 0x10),
                    { cpu: CPU ->
                        cpu.a = 0xFF
                        cpu.x = 0x04
                        // zp idx = (0x10 + X) & 0xFF = 0x14 → pointer @ $0014/$0015
                        write(cpu, 0x0014, 0x78) // lo
                        write(cpu, 0x0015, 0x10) // hi → eff $1078
                        write(cpu, 0x1078, 0b1000_0000)
                    },
                    0b1000_0000, 0, FLAG_NEGATIVE, 6
                ),
                row(
                    "(Indirect),Y no cross ($31)", intArrayOf(0x31, 0x20),
                    { cpu: CPU ->
                        cpu.a = 0xFF
                        cpu.y = 0x00
                        // pointer @ $0020/$0021 = $0100; Y=0 → eff $0100
                        write(cpu, 0x0020, 0x00) // lo
                        write(cpu, 0x0021, 0x01) // hi
                        write(cpu, 0x0100, 0x7F)
                    },
                    0x7F, 0, 0, 5
                ),
            ) { label, bytes, setup, expectedA, expectedZ, expectedN, expectedCycles ->
                label {
                    val cpu = setupCpuWithInstruction(*bytes)
                    setup(cpu)

                    val cycles = cpu.step()

                    cpu.a shouldBe expectedA
                    (cpu.status and FLAG_ZERO) shouldBe expectedZ
                    (cpu.status and FLAG_NEGATIVE) shouldBe expectedN
                    cycles shouldBe expectedCycles
                }
            }
        }

        // 2) Page-cross penalties for Absolute,X and Absolute,Y (AND should incur +1 on cross)
        "should add +1 cycle on page cross for Absolute,X and Absolute,Y" {
            // Absolute,X cross: base $00FF + X=0x05 -> $0104
            run {
                val cpu = setupCpuWithInstruction(0x3D, 0xFF, 0x00) // AND abs,X
                cpu.a = 0xFF
                cpu.x = 0x05
                write(cpu, 0x0104, 0x01)

                val cycles = cpu.step()

                cpu.a shouldBe 0x01
                (cpu.status and FLAG_ZERO) shouldBe 0
                (cpu.status and FLAG_NEGATIVE) shouldBe 0
                cycles shouldBe 5
            }
            // Absolute,Y cross: base $00FE + Y=0x02 -> $0100
            run {
                val cpu = setupCpuWithInstruction(0x39, 0xFE, 0x00) // AND abs,Y
                cpu.a = 0xFF
                cpu.y = 0x02
                write(cpu, 0x0100, 0x80)

                val cycles = cpu.step()

                cpu.a shouldBe 0x80
                (cpu.status and FLAG_ZERO) shouldBe 0
                (cpu.status and FLAG_NEGATIVE) shouldBe FLAG_NEGATIVE
                cycles shouldBe 5
            }
        }

        // 3) Page-cross penalty for (Indirect),Y
        "(Indirect),Y should add +1 cycle on page cross" {
            // Pointer at $0040/$0041 = $00FF; Y=+2 → eff $0101 (crosses $00xx → $01xx)
            val cpu = setupCpuWithInstruction(0x31, 0x40) // AND (ind),Y
            cpu.a = 0x7F
            cpu.y = 0x02
            write(cpu, 0x0040, 0xFF) // lo
            write(cpu, 0x0041, 0x00) // hi  → base = $00FF
            write(cpu, 0x0101, 0x7F) // effective addr after Y

            val cycles = cpu.step()

            cpu.a shouldBe 0x7F
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
            cycles shouldBe 6
        }
    }
})
