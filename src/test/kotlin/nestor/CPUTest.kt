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
            val lo = 0xFF; val hi = 0x00; val yVal = 0x01  // base=$00FF, eff=$0100 (page cross in RAM)
            val base = (hi shl 8) or lo
            val eff  = (base + yVal) and 0xFFFF

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
})
