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
    val screen: ScreenRenderer,
    private val tracer: Tracer = NullTracer,
) {
    fun run() {
        println("Nestor NES Emulator starting...")
        cpu.reset()
    }

    fun runFrames(frames: Int) {
        println("Running $frames frame(s)")
        cpu.reset()
        var cycles = 0
        val limit = 29780 * frames
        while (cycles < limit) {
            cycles += step()
        }
        tracer.close()
    }

    internal fun step(): Int {
        if (cpu.abort) {
            tracer.close()
            System.exit(0)
        }
        tracer.trace(cpu.traceLine())
        val cpuCycles = cpu.step()
        ppu.tick(cpuCycles * 3)
        if (ppu.frameReady) {
            screen.draw(ppu.currentFrame())
            ppu.frameReady = false
        }
        cpu.pollNmi()
        return cpuCycles
    }
}