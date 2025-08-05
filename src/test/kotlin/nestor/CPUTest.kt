package nestor

import io.kotest.core.spec.style.FreeSpec
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

    "LDA immediate instruction" - {
        "should load a non-zero value into the accumulator" {
            val cpu = setupCpuWithInstruction(0xA9, 0x42)

            val cycles = cpu.step()

            cpu.accumulator shouldBe 0x42
            cpu.status and FLAG_ZERO shouldBe 0
            cpu.status and FLAG_NEGATIVE shouldBe 0
            cycles shouldBe 2
        }

        "should set the zero flag when loading 0" {
            val cpu = setupCpuWithInstruction(0xA9, 0x00)

            val cycles = cpu.step()

            cpu.accumulator shouldBe 0x00
            cpu.status and FLAG_ZERO shouldBe FLAG_ZERO
            cpu.status and FLAG_NEGATIVE shouldBe 0
            cycles shouldBe 2
        }

        "should set the negative flag when loading a value with bit 7 set" {
            val cpu = setupCpuWithInstruction(0xA9, 0x80)

            val cycles = cpu.step()

            cpu.accumulator shouldBe 0x80
            cpu.status and FLAG_ZERO shouldBe 0
            cpu.status and FLAG_NEGATIVE shouldBe FLAG_NEGATIVE
            cycles shouldBe 2
        }

        "should clear zero and negative flags if they were set and the loaded value is positive and non-zero" {
            val cpu = setupCpuWithInstruction(0xA9, 0x10)
            cpu.status = FLAG_ZERO or FLAG_NEGATIVE

            val cycles = cpu.step()

            cpu.accumulator shouldBe 0x10
            cpu.status and FLAG_ZERO shouldBe 0
            cpu.status and FLAG_NEGATIVE shouldBe 0
            cycles shouldBe 2
        }
    }
})
