package nestor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class MemoryBusTest : FreeSpec({
    val dummyPpu = mockk<PPU>(relaxed = true)
    val prgRom = ByteArray(0x4000) { it.toByte() }
    val bus = MemoryBus(ppu = dummyPpu, prgRom = prgRom)

    beforeTest { clearAllMocks() }

    "CPU RAM should mirror every 0x0800 bytes" {
        val value = 0x42
        bus.write(0x0000, value)
        bus.read(0x0000) shouldBe value
        bus.read(0x0800) shouldBe value
        bus.read(0x1000) shouldBe value
        bus.read(0x1800) shouldBe value
    }

    "Writing to RAM should store byte correctly" {
        bus.write(0x0003, 0x99)
        bus.read(0x0003) shouldBe 0x99
    }

    "Reading PPU register should call cpuRead with mirrored address" {
        every { dummyPpu.cpuRead(any()) } returns 0x55
        bus.read(0x2008) shouldBe 0x55  // 0x2008 → mirror of 0x2000
        verify { dummyPpu.cpuRead(0x2000) }
    }

    "Writing to PPU register should call cpuWrite with mirrored address" {
        bus.write(0x3FFF, 0x80)
        verify { dummyPpu.cpuWrite(0x2007, 0x80) }  // 0x3FFF % 8 = 7 → 0x2000 + 7
    }

    "Reading from PRG-ROM should return correct byte (16KB mirrored)" {
        bus.read(0x8000) shouldBe prgRom[0].toUByte().toInt()
        bus.read(0xBFFF) shouldBe prgRom[0x3FFF].toUByte().toInt()
        bus.read(0xC000) shouldBe prgRom[0].toUByte().toInt() // mirror if only 16KB
    }

    "Reading from unhandled address should return zero" {
        bus.read(0x5000) shouldBe 0
    }

    "Writing to PRG-ROM should have no effect" {
        val original = bus.read(0x8000)
        bus.write(0x8000, 0xAB)
        bus.read(0x8000) shouldBe original
    }
})
