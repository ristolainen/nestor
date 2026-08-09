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
    val controller1 = Controller()
    val controller2 = Controller()
    val bus = MemoryBus(ppu = dummyPpu, prgRom = prgRom, controller1 = controller1, controller2 = controller2)

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

    "OAMDMA: writing to \$4014 should copy 256 bytes from the specified CPU page to OAM" {
        val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
        val cpuRam = ByteArray(0x0800)
        val oamBus = MemoryBus(
            ppu = ppu,
            prgRom = ByteArray(0x4000),
            controller1 = Controller(),
            controller2 = Controller(),
            cpuRam = cpuRam,
        )

        // Write known data to CPU page 2 ($0200–$02FF)
        for (i in 0 until 256) {
            oamBus.write(0x0200 + i, i)
        }

        ppu.oamAddr = 0x00
        oamBus.write(0x4014, 0x02)  // DMA from page $02

        for (i in 0 until 256) {
            ppu.oamRam[i].toUByte().toInt() shouldBe i
        }
    }

    "OAMDMA: DMA respects oamAddr offset" {
        val ppu = PPU(ByteArray(0), MirroringMode.VERTICAL)
        val cpuRam = ByteArray(0x0800)
        val oamBus = MemoryBus(
            ppu = ppu,
            prgRom = ByteArray(0x4000),
            controller1 = Controller(),
            controller2 = Controller(),
            cpuRam = cpuRam,
        )

        for (i in 0 until 256) {
            oamBus.write(0x0300 + i, 0xFF)
        }

        ppu.oamAddr = 0x10
        oamBus.write(0x4014, 0x03)

        // First byte written to oamRam[0x10]
        ppu.oamRam[0x10].toUByte().toInt() shouldBe 0xFF
        // Wraps around: oamRam[0x0F] gets the last byte
        ppu.oamRam[0x0F].toUByte().toInt() shouldBe 0xFF
    }

    "controllers" - {
        // The bus is shared by every test in this spec, so start each one from a known state.
        beforeTest {
            Button.entries.forEach {
                controller1.release(it)
                controller2.release(it)
            }
            bus.write(0x4016, 1)
            bus.write(0x4016, 0)
        }

        fun strobe() {
            bus.write(0x4016, 1)
            bus.write(0x4016, 0)
        }

        "\$4016 reads controller 1" {
            controller1.press(Button.A)
            strobe()
            bus.read(0x4016) shouldBe 0x41
        }

        "\$4017 reads controller 2" {
            controller2.press(Button.A)
            strobe()
            bus.read(0x4017) shouldBe 0x41
            bus.read(0x4016) shouldBe 0x40   // port 1 untouched
        }

        "the two ports have independent shift registers" {
            controller1.press(Button.A)
            controller2.press(Button.B)
            strobe()

            bus.read(0x4016) shouldBe 0x41   // pad1 bit 0: A pressed
            bus.read(0x4017) shouldBe 0x40   // pad2 bit 0: A not pressed
            bus.read(0x4016) shouldBe 0x40   // pad1 bit 1: B not pressed
            bus.read(0x4017) shouldBe 0x41   // pad2 bit 1: B pressed
        }

        "a single write to \$4016 strobes both controllers" {
            controller1.press(Button.START)
            controller2.press(Button.START)
            strobe()

            repeat(3) {
                bus.read(0x4016)
                bus.read(0x4017)
            }
            bus.read(0x4016) shouldBe 0x41   // 4th bit: Start
            bus.read(0x4017) shouldBe 0x41
        }

        "writing \$4017 is the APU frame counter and must not strobe the controllers" {
            controller2.press(Button.A)
            bus.write(0x4017, 1)
            bus.write(0x4017, 0)

            // Never latched, so the shift register still holds the pre-press snapshot.
            bus.read(0x4017) shouldBe 0x40
        }

        "reads return open bus \$40 in the undriven upper bits" {
            strobe()
            bus.read(0x4016) shouldBe 0x40   // nothing pressed
            controller1.press(Button.A)
            strobe()
            bus.read(0x4016) shouldBe 0x41
        }

        "peek does not consume a bit" {
            controller1.press(Button.B)
            strobe()

            bus.peek(0x4016) shouldBe 0x40
            bus.peek(0x4016) shouldBe 0x40
            bus.read(0x4016) shouldBe 0x40   // A
            bus.peek(0x4016) shouldBe 0x41
            bus.read(0x4016) shouldBe 0x41   // B
        }

        "controller ports are not mirrored" {
            controller1.press(Button.A)
            strobe()

            // $4016 is a bare address in the APU/IO range — $4018+ is not a mirror of it.
            bus.read(0x4018) shouldBe 0
            bus.read(0x4016) shouldBe 0x41   // still on the first bit
        }
    }
})
