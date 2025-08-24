package nestor

/**
 * ⏱ Timing math:
 *  - NES runs at 1.79 MHz → ~29780 CPU cycles per 1/60 sec frame
 *  - Each CPU cycle = 3 PPU cycles
 *  - PPU runs at ~5.37 MHz → 89342 PPU cycles per frame
 *  - 262 scanlines × 341 PPU clocks = 89342
 */
class Emulation(
    val cpu: CPU,
    val ppu: PPU,
    val memoryBus: MemoryBus,
) {
    fun run() {
        println("Nestor NES Emulator starting...")
        cpu.reset()
    }

    fun runFrame() {
        println("Running one frame")
        cpu.reset()
        var cycles = 0
        while (cycles < 29780) {
            cycles += step()
        }
    }

    fun runAFewTicks() {
        println("Running a few ticks")
        cpu.reset()
        var cycles = 0
        while (cycles < 120000) {
            cycles += step()
        }
    }

    private fun step() = Trace.traceOnce(cpu, ppu, memoryBus) {
        if (cpu.abort) {
            System.exit(0)
        }
        val cpuCycles = cpu.step()
        ppu.tick(cpuCycles * 3)
        cpuCycles
    }
}