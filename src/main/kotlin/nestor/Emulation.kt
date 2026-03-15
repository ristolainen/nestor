package nestor

import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    private var traceWriter: PrintWriter? = null

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

    fun runFrames(frames: Int) {
        println("Running $frames frame(s)")
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val traceFile = java.io.File("traces/$timestamp.txt")
        traceFile.parentFile.mkdirs()
        traceWriter = PrintWriter(traceFile)

        cpu.reset()
        var cycles = 0
        val limit = 29780 * frames
        while (cycles < limit) {
            cycles += step()
        }

        traceWriter?.close()
        traceWriter = null
        println("Trace saved to ${traceFile.path}")
    }

    internal fun step(): Int {
        if (cpu.abort) {
            traceWriter?.close()
            System.exit(0)
        }
        val line = cpu.traceLine()
        println(line)
        traceWriter?.println(line)
        val cpuCycles = cpu.step()
        ppu.tick(cpuCycles * 3)
        cpu.pollNmi()
        return cpuCycles
    }
}