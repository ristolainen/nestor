package nestor

/**
 * ⏱ Timing math:
 *  - NES runs at 1.79 MHz → ~29780 CPU cycles per 1/60 sec frame
 *  - Each CPU cycle = 3 PPU cycles
 *  - PPU runs at ~5.37 MHz → 89342 PPU cycles per frame
 *  - 262 scanlines × 341 PPU clocks = 89342
 */
class NES(
    rom: INESRom,
    private val screen: ScreenRenderer,
    private val tracer: Tracer = NullTracer,
) {
    val cpu: CPU
    val ppu: PPU
    val controller1: Controller
    val controller2: Controller
    val memory: MemoryBus

    init {
        ppu = PPU(rom.chrData, rom.header.mirroring)
        controller1 = Controller()
        controller2 = Controller()
        memory = MemoryBus(ppu, rom.prgData, controller1, controller2)
        cpu = CPU(memory)
    }

    fun run(entryPoint: Int? = null, until: () -> Boolean = { false }) {
        cpu.reset()
        entryPoint?.let {
            cpu.pc = it
            cpu.cycles = 7
            ppu.cycle = 21
        }
        try {
            while (!until()) {
                step()
            }
        } finally {
            tracer.close()
        }
    }

    internal fun step(): Int {
        tracer.trace { cpu.traceLine() }
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