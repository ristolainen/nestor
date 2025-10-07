package nestor

import nestor.Trace.traceOnce
import java.util.*

/**
 * Drop-in utilities to print CPU + PPU state *after each instruction*.
 * Works even if your CPU doesn't expose a callback — just wrap your normal
 * single-instruction step call in [traceOnce].
 *
 * The log format is one JSON line per instruction, plus a compact human string.
 * This makes it easy to diff against reference logs (e.g., nestest.log) and
 * for another person to eyeball correctness.
 *
 * You likely only need to adapt the few read helpers at the bottom to match
 * your MemoryBus and PPU field names.
 */
object Trace {
    // ===== Public entry points =================================================

    /**
     * Call this instead of your normal `cpu.step(...)`.
     *
     * Example usage:
     * ```kotlin
     * while (running) {
     *     Trace.traceOnce(cpu, ppu, bus) {
     *         cpu.step(bus, ppu) // <- whatever you normally call for ONE instr
     *     }
     * }
     * ```
     */
    fun traceOnce(
        cpu: CPU,
        ppu: PPU,
        bus: MemoryBus,
        log: (String) -> Unit = ::println,
        maxInstrBytes: Int = 3,
        doOneInstruction: () -> Int
    ): Int {
        val pcBefore = getPC(cpu)
        val cycBefore = getCycles(cpu)
        val bytes = readInstrBytes(bus, pcBefore, maxInstrBytes)

        val result = doOneInstruction()

        val frame = buildFrame(cpu, ppu, bus, pcBefore, bytes, cycBefore)
        log(formatFrame(frame))

        return result
    }

    // ===== Data model ==========================================================

    data class CpuFrame(
        val pc: Int,
        val a: Int,
        val x: Int,
        val y: Int,
        val sp: Int,
        val status: Int,
        val statusFlags: String,
        val cyclesTotal: Long,
        val cyclesDelta: Long,
        val instrBytes: List<Int>
    )

    data class PpuFrame(
        val scanline: Int?,
        val dot: Int?,
        val frame: Long?,
        val v: Int?,
        val t: Int?,
        val fineX: Int?,
        val w: Int?,
        val nmiOutput: Boolean?,
        val nmiOccurred: Boolean?,
        val mask: Int?,
        val ctrl: Int?,
        val status: Int?,
        val oamAddr: Int?,
        val openBus: Int?
    )

    data class TraceFrame(val cpu: CpuFrame, val ppu: PpuFrame)

    // ===== Formatting ===========================================================

    fun formatFrame(f: TraceFrame): String {
        val json = buildString {
            append('{')
            append("\"cpu\":{")
            append("\"pc\":").append(f.cpu.pc).append(',')
            append("\"a\":").append(f.cpu.a).append(',')
            append("\"x\":").append(f.cpu.x).append(',')
            append("\"y\":").append(f.cpu.y).append(',')
            append("\"sp\":").append(f.cpu.sp).append(',')
            append("\"p\":").append(f.cpu.status).append(',')
            append("\"flags\":\"").append(f.cpu.statusFlags).append("\",")
            append("\"cyclesTotal\":").append(f.cpu.cyclesTotal).append(',')
            append("\"cyclesDelta\":").append(f.cpu.cyclesDelta).append(',')
            append("\"bytes\":[")
            f.cpu.instrBytes.forEachIndexed { i, b ->
                if (i > 0) append(',')
                when (i) {
                    0 -> append(b.hex())
                    else -> append(b)
                }
            }
            append("]},")
            append("\"ppu\":{")
            fun a(name: String, v: Any?) {
                if (v == null) return
                if (last() != '{') append(',')
                append('"').append(name).append('"').append(':')
                when (v) {
                    is String -> append('"').append(v).append('"')
                    is Boolean -> append(if (v) "true" else "false")
                    else -> append(v)
                }
            }
            a("scanline", f.ppu.scanline)
            a("dot", f.ppu.dot)
            a("frame", f.ppu.frame)
            a("v", f.ppu.v)
            a("t", f.ppu.t)
            a("fineX", f.ppu.fineX)
            a("w", f.ppu.w)
            a("nmiOutput", f.ppu.nmiOutput)
            a("nmiOccurred", f.ppu.nmiOccurred)
            a("mask", f.ppu.mask)
            a("ctrl", f.ppu.ctrl)
            a("status", f.ppu.status)
            a("oamAddr", f.ppu.oamAddr)
            a("openBus", f.ppu.openBus)
            append("}}")
            append('}')
        }

        // Human-friendly line inspired by nestest.log
        val human = buildString {
            append("PC=").append(hex16(f.cpu.pc)).append(' ')
            append("A=").append(hex8(f.cpu.a)).append(' ')
            append("X=").append(hex8(f.cpu.x)).append(' ')
            append("Y=").append(hex8(f.cpu.y)).append(' ')
            append("P=").append(hex8(f.cpu.status)).append(' ')
            append("SP=").append(hex8(f.cpu.sp)).append(' ')
            append("FLAGS=").append(f.cpu.statusFlags).append(' ')
            append("CYC=").append(f.cpu.cyclesTotal)
            f.ppu.scanline?.let { s ->
                f.ppu.dot?.let { d ->
                    append(" SL=").append(s).append(" DOT=").append(d)
                }
            }
            append(" | ")
            append(f.cpu.instrBytes.joinToString(" ") { hex8(it) })
        }

        return json
        //return "$json\n$human"
    }

    // ===== Frame builders =======================================================

    fun buildFrame(
        cpu: CPU,
        ppu: PPU,
        bus: MemoryBus,
        pcBefore: Int,
        instrBytes: List<Int>,
        cycBefore: Long
    ): TraceFrame {
        val a = getA(cpu)
        val x = getX(cpu)
        val y = getY(cpu)
        val sp = getSP(cpu)
        val p = getStatus(cpu)
        val flagsStr = flagsToString(p)
        val cycAfter = getCycles(cpu)
        val ppuSnap = PpuFrame(
            scanline = ppu.scanline,
            dot = ppu.cycle,
            frame = ppu.frame,
            v = tryGetInt(ppu, "v"),
            t = tryGetInt(ppu, "t"),
            fineX = tryGetInt(ppu, "x") ?: tryGetInt(ppu, "fineX"),
            w = tryGetInt(ppu, "w"),
            nmiOutput = ppu.nmiOutput,
            nmiOccurred = ppu.nmiOccurred,
            mask = ppu.mask,
            ctrl = ppu.control,
            status = ppu.status,
            oamAddr = ppu.oamAddr,
            openBus = tryGetInt(ppu, "openBus")
        )

        val cpuFrame = CpuFrame(
            pc = pcBefore,
            a = a, x = x, y = y, sp = sp,
            status = p,
            statusFlags = flagsStr,
            cyclesTotal = cycAfter,
            cyclesDelta = cycAfter - cycBefore,
            instrBytes = instrBytes
        )
        return TraceFrame(cpuFrame, ppuSnap)
    }

    // ===== Helpers you might need to adapt ====================================

    fun readInstrBytes(bus: MemoryBus, pc: Int, count: Int): List<Int> {
        // Adapt if your bus uses a different API name than read8
        fun r(addr: Int): Int = try {
            val m = bus.javaClass.methods.firstOrNull { it.name == "read8" && it.parameterTypes.contentEquals(arrayOf(Int::class.java)) }
                ?: bus.javaClass.methods.firstOrNull { it.name == "read" && it.parameterTypes.size == 1 }
                ?: error("MemoryBus needs read8(addr:Int) or read(addr)")
            ((m.invoke(bus, addr and 0xFFFF) as Number).toInt()) and 0xFF
        } catch (e: Exception) {
            0
        }
        return (0 until count).map { r(pc + it) }
    }

    private fun getA(cpu: CPU) = getIntField(cpu, "A") ?: getIntField(cpu, "a") ?: 0
    private fun getX(cpu: CPU) = getIntField(cpu, "X") ?: getIntField(cpu, "x") ?: 0
    private fun getY(cpu: CPU) = getIntField(cpu, "Y") ?: getIntField(cpu, "y") ?: 0
    private fun getSP(cpu: CPU) = getIntField(cpu, "SP") ?: getIntField(cpu, "sp") ?: 0
    fun getPC(cpu: CPU) = getIntField(cpu, "PC") ?: getIntField(cpu, "pc") ?: 0
    private fun getStatus(cpu: CPU) = getIntField(cpu, "P") ?: getIntField(cpu, "status") ?: 0
    fun getCycles(cpu: CPU) = getLongField(cpu, "cycles") ?: 0

    // Reflection shims so this works with your field names without extra edits
    private fun getIntField(obj: Any, name: String): Int? = try { (obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj) as Number).toInt() } catch (_: Throwable) { null }
    private fun getLongField(obj: Any, name: String): Long? = try { (obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj) as Number).toLong() } catch (_: Throwable) { null }
    private fun tryGetInt(obj: Any, name: String): Int? = try { getIntField(obj, name) } catch (_: Throwable) { null }
    private fun tryGetLong(obj: Any, name: String): Long? = try { getLongField(obj, name) } catch (_: Throwable) { null }
    private fun tryGetBool(obj: Any, name: String): Boolean? = try {
        val f = obj.javaClass.getDeclaredField(name); f.isAccessible = true; f.get(obj) as? Boolean
    } catch (_: Throwable) { null }

    private fun tryReadPpuReg(ppu: PPU, hint: String): Int? {
        // Try typical Kotlin/Java patterns: ppu.ctrl, ppu.getCtrl(), ppu.readCtrl()
        return tryGetInt(ppu, hint)
            ?: callNoArgInt(ppu, "get${hint.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }}")
            ?: callNoArgInt(ppu, "read${hint.uppercase(Locale.ROOT)}")
    }

    private fun callNoArgInt(obj: Any, name: String): Int? = try {
        val m = obj.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() } ?: return null
        (m.invoke(obj) as Number).toInt()
    } catch (_: Throwable) { null }

    private fun flagsToString(p: Int): String {
        val n = (p shr 7) and 1
        val v = (p shr 6) and 1
        val u = (p shr 5) and 1 // unused (always 1 on the 6502, but emus vary)
        val b = (p shr 4) and 1
        val d = (p shr 3) and 1
        val i = (p shr 2) and 1
        val z = (p shr 1) and 1
        val c = (p shr 0) and 1
        return buildString(8) {
            append(if (n == 1) 'N' else '-')
            append(if (v == 1) 'V' else '-')
            append(if (u == 1) 'U' else '-')
            append(if (b == 1) 'B' else '-')
            append(if (d == 1) 'D' else '-')
            append(if (i == 1) 'I' else '-')
            append(if (z == 1) 'Z' else '-')
            append(if (c == 1) 'C' else '-')
        }
    }

    private fun hex8(v: Int) = "${'$'}{(v and 0xFF).toString(16).uppercase().padStart(2, '0')}"
    private fun hex16(v: Int) = "${'$'}{(v and 0xFFFF).toString(16).uppercase().padStart(4, '0')}"
}
