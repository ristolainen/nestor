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

    "CMP immediate" - {

        fun setup(a: Int, imm: Int): Triple<CPU, Int, Int> {
            val cpu = setupCpuWithInstruction(0xA9, a, 0xC9, imm) // LDA #a ; CMP #imm
            val cycles = cpu.step() + cpu.step()
            return Triple(cpu, imm, cycles)
        }

        "A > M sets C, clears Z and N" {
            val (cpu, _, _) = setup(0x42, 0x40)
            (cpu.status and FLAG_CARRY) shouldBe FLAG_CARRY
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
        }

        "A == M sets C and Z, clears N" {
            val (cpu, _, _) = setup(0x42, 0x42)
            (cpu.status and FLAG_CARRY) shouldBe FLAG_CARRY
            (cpu.status and FLAG_ZERO) shouldBe FLAG_ZERO
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
        }

        "A < M clears C and Z, sets N from bit7 of (A-M)" {
            val (cpu, _, _) = setup(0x40, 0x42)  // diff = 0xFE
            (cpu.status and FLAG_CARRY) shouldBe 0
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe FLAG_NEGATIVE
        }

        "Borrow case: 0x00 - 0x01 => 0xFF sets N, clears C and Z" {
            val (cpu, _, _) = setup(0x00, 0x01)
            (cpu.status and FLAG_CARRY) shouldBe 0
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe FLAG_NEGATIVE
        }
    }

    "DEX instruction" - {
        "should decrement X by 1" {
            val cpu = setupCpuWithInstruction(0xCA) // DEX
            cpu.x = 0x05

            val cycles = cpu.step()

            cpu.x shouldBe 0x04
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
            cycles shouldBe 2
        }

        "should wrap from 0x00 to 0xFF" {
            val cpu = setupCpuWithInstruction(0xCA)
            cpu.x = 0x00

            val cycles = cpu.step()

            cpu.x shouldBe 0xFF
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe FLAG_NEGATIVE
            cycles shouldBe 2
        }

        "should set ZERO when result is 0" {
            val cpu = setupCpuWithInstruction(0xCA)
            cpu.x = 0x01

            val cycles = cpu.step()

            cpu.x shouldBe 0x00
            (cpu.status and FLAG_ZERO) shouldBe FLAG_ZERO
            (cpu.status and FLAG_NEGATIVE) shouldBe 0
            cycles shouldBe 2
        }

        "should set NEGATIVE when bit 7 is set after decrement" {
            val cpu = setupCpuWithInstruction(0xCA)
            cpu.x = 0x81  // 0x81 - 1 = 0x80 → negative

            val cycles = cpu.step()

            cpu.x shouldBe 0x80
            (cpu.status and FLAG_ZERO) shouldBe 0
            (cpu.status and FLAG_NEGATIVE) shouldBe FLAG_NEGATIVE
            cycles shouldBe 2
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
})
