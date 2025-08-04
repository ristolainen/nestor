package nestor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class CPUTest : FreeSpec({
    fun setupCpuWithInstruction(
        opcode: Int,
        address: Int = 0x8000,
        ppu: PPU = PPU(emptyList())
    ): CPU {
        val prgRom = ByteArray(0x4000)
        val offset = address - 0x8000

        prgRom[offset] = opcode.toByte()
        prgRom[0x3FFC] = (address and 0xFF).toByte()         // low byte of address
        prgRom[0x3FFD] = ((address shr 8) and 0xFF).toByte() // high byte of address

        val memory = MemoryBus(ppu, prgRom)
        val cpu = CPU(memory)
        cpu.reset()

        return cpu
    }

    "SEI instruction" - {
        "should set the interrupt disable flag" {
            val cpu = setupCpuWithInstruction(0x78)
            cpu.status = 0x00 // clear all flags for test

            val cycles = cpu.step()

            cpu.status shouldBe FLAG_INTERRUPT_DISABLE
            cycles shouldBe 2
        }
    }
})
