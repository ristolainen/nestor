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
})
