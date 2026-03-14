package nestor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe

/**
 * Generates a 2bpp NES tile with a checkerboard pattern (color indices 0 and 1).
 * Color layout:
 *  1 0 1 0 1 0 1 0
 *  0 1 0 1 0 1 0 1
 *  ...
 */
fun makeCheckerboardTileData(): ByteArray {
    val tile = ByteArray(16)
    val evenRow = 0b10101010
    val oddRow = 0b01010101

    // plane 0 holds the low bits: alternate 1s and 0s
    for (i in 0 until 8) {
        tile[i] = if (i % 2 == 0) evenRow.toByte() else oddRow.toByte()
    }

    // plane 1 holds the high bits: all 0s for indices 0 and 1 only
    for (i in 8 until 16) {
        tile[i] = 0x00
    }

    return tile
}

/**
 * Creates a checkerboard tile (8x8) as Array<IntArray>,
 * alternating color indices 1 and 0.
 */
fun makeCheckerboardTile() = Array(8) { y ->
    IntArray(8) { x ->
        // Alternate every pixel in a checkerboard pattern: (x + y) % 2
        if ((x + y) % 2 == 0) 1 else 0
    }
}

fun makeStripeTileData(): ByteArray {
    val tile = ByteArray(16)

    val rowLowBits = 0b10101010 // plane 0: bit 0 for 2/3
    val rowHighBits = 0b11111111 // plane 1: bit 1 for 2/3

    for (i in 0 until 8) {
        tile[i] = rowLowBits.toByte()      // plane 0
        tile[8 + i] = rowHighBits.toByte() // plane 1
    }

    return tile
}

/**
 * Creates a striped tile (8x8) as Array<IntArray>,
 * alternating rows of color index 3 and 2.
 */
fun makeStripedTile() = Array(8) {
    IntArray(8) { x -> if (x % 2 == 0) 3 else 2 }
}

/**
 * Creates a blank tile (8x8) where all color indices are 0.
 */
fun makeBlankTile() = Array(8) { IntArray(8) { 0 } }

fun printTileBitplane(tileData: ByteArray) {
    require(tileData.size == 16) { "Tile must be 16 bytes (8 bytes per bitplane)" }

    val plane0 = tileData.slice(0 until 8)
    val plane1 = tileData.slice(8 until 16)

    println("Tile pixel color indices (0-3):")
    for (y in 0 until 8) {
        val row0 = plane0[y].toInt() and 0xFF
        val row1 = plane1[y].toInt() and 0xFF

        val row = buildString {
            for (x in 7 downTo 0) {
                val bit0 = (row0 shr x) and 1
                val bit1 = (row1 shr x) and 1
                val colorIndex = (bit1 shl 1) or bit0
                append(colorIndex)
            }
        }
        println(row)
    }
}

/**
 * Prints a tile as 8x8 grid of digits (0–3).
 */
fun printTile(tile: Array<IntArray>) {
    require(tile.size == 8 && tile.all { it.size == 8 }) {
        "Tile must be 8x8"
    }

    for (row in tile) {
        println(row.joinToString("") { it.toString() })
    }
}

fun rgbToAnsi(rgb: Int): String {
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF

    return when {
        r < 32 && g < 32 && b < 32 -> "\u001B[40m" // black
        r > 200 && g > 200 && b > 200 -> "\u001B[47m" // white
        r > 200 && g > 200 -> "\u001B[43m" // yellow
        r > 200 -> "\u001B[41m" // red
        g > 200 -> "\u001B[42m" // green
        b > 200 -> "\u001B[44m" // blue
        r > 150 && b > 150 -> "\u001B[45m" // magenta
        g > 150 && b > 150 -> "\u001B[46m" // cyan
        else -> "\u001B[100m" // dark gray fallback
    }
}

fun printAnsiFrame(frame: IntArray) {
    val colorMap = frame.toSet().associateWith { rgbToAnsi(it) }

    for (y in 0 until FRAME_HEIGHT) {
        val row = buildString {
            for (x in 0 until FRAME_WIDTH) {
                val color = frame[y * FRAME_WIDTH + x]
                append("${colorMap[color]}  ")
            }
            append("\u001B[0m")
        }
        println(row)
    }
}

fun printAnsiTile(frame: IntArray, tileX: Int, tileY: Int) {
    val startX = tileX * TILE_WIDTH
    val startY = tileY * TILE_HEIGHT

    if (startX + TILE_WIDTH > FRAME_WIDTH || startY + TILE_HEIGHT > FRAME_HEIGHT) {
        println("Tile out of bounds!")
        return
    }

    val tilePixels = mutableListOf<Int>()
    for (y in 0 until TILE_HEIGHT) {
        for (x in 0 until TILE_WIDTH) {
            val globalX = startX + x
            val globalY = startY + y
            val index = globalY * FRAME_WIDTH + globalX
            tilePixels.add(frame[index])
        }
    }

    val colorMap = tilePixels.toSet().associateWith { rgbToAnsi(it) }

    for (y in 0 until TILE_HEIGHT) {
        val row = buildString {
            for (x in 0 until TILE_WIDTH) {
                val color = tilePixels[y * TILE_HEIGHT + x]
                append("${colorMap[color]}  ")
            }
            append("\u001B[0m")
        }
        println(row)
    }
}

// ── CPU test DSL ─────────────────────────────────────────────────────────────

data class CpuState(
    val a: Int,
    val x: Int,
    val y: Int,
    val sp: Int,
    val pc: Int,
    val carry: Boolean,
    val zero: Boolean,
    val negative: Boolean,
    val overflow: Boolean,
    val interrupt: Boolean,
    val decimal: Boolean,
)

data class CpuDelta(
    val a: Int? = null,
    val x: Int? = null,
    val y: Int? = null,
    val sp: Int? = null,
    val carry: Boolean? = null,
    val zero: Boolean? = null,
    val negative: Boolean? = null,
    val overflow: Boolean? = null,
    val interrupt: Boolean? = null,
    val decimal: Boolean? = null,
    val mem: Map<Int, Int> = emptyMap(),
)

data class ExpectedStepOutcome(
    val cycles: Int,
    val a: Int? = null,
    val x: Int? = null,
    val y: Int? = null,
    val sp: Int? = null,
    val pc: Int? = null,
    val carry: Boolean? = null,
    val zero: Boolean? = null,
    val negative: Boolean? = null,
    val overflow: Boolean? = null,
    val interrupt: Boolean? = null,
    val decimal: Boolean? = null,
    val mem: Map<Int, Int> = emptyMap(),
) {
    fun verify(actual: CpuDelta, after: CpuState) {
        // Rule 1 — check expected final state
        a?.let         { withClue("a")         { after.a         shouldBe it } }
        x?.let         { withClue("x")         { after.x         shouldBe it } }
        y?.let         { withClue("y")         { after.y         shouldBe it } }
        sp?.let        { withClue("sp")        { after.sp        shouldBe it } }
        carry?.let     { withClue("carry")     { after.carry     shouldBe it } }
        zero?.let      { withClue("zero")      { after.zero      shouldBe it } }
        negative?.let  { withClue("negative")  { after.negative  shouldBe it } }
        overflow?.let  { withClue("overflow")  { after.overflow  shouldBe it } }
        interrupt?.let { withClue("interrupt") { after.interrupt shouldBe it } }
        decimal?.let   { withClue("decimal")   { after.decimal   shouldBe it } }

        // Rule 2 — reject unexpected changes
        if (a         == null) withClue("unexpected change to a")         { actual.a         shouldBe null }
        if (x         == null) withClue("unexpected change to x")         { actual.x         shouldBe null }
        if (y         == null) withClue("unexpected change to y")         { actual.y         shouldBe null }
        if (sp        == null) withClue("unexpected change to sp")        { actual.sp        shouldBe null }
        if (carry     == null) withClue("unexpected change to carry")     { actual.carry     shouldBe null }
        if (zero      == null) withClue("unexpected change to zero")      { actual.zero      shouldBe null }
        if (negative  == null) withClue("unexpected change to negative")  { actual.negative  shouldBe null }
        if (overflow  == null) withClue("unexpected change to overflow")  { actual.overflow  shouldBe null }
        if (interrupt == null) withClue("unexpected change to interrupt") { actual.interrupt shouldBe null }
        if (decimal   == null) withClue("unexpected change to decimal")   { actual.decimal   shouldBe null }

        // Rule 3 — memory writes must match exactly
        withClue("memory writes") { actual.mem shouldBe mem }
    }
}

class CpuSetup {
    var a: Int? = null
    var x: Int? = null
    var y: Int? = null
    var sp: Int? = null
    var carry: Boolean? = null
    var zero: Boolean? = null
    var negative: Boolean? = null
    var overflow: Boolean? = null
    var interrupt: Boolean? = null
    var decimal: Boolean? = null
    private val _mem = mutableMapOf<Int, Int>()
    val mem: Map<Int, Int> get() = _mem

    fun a(v: Int) = apply { this.a = v }
    fun x(v: Int) = apply { this.x = v }
    fun y(v: Int) = apply { this.y = v }
    fun sp(v: Int) = apply { this.sp = v }
    fun carry(v: Boolean) = apply { this.carry = v }
    fun zero(v: Boolean) = apply { this.zero = v }
    fun negative(v: Boolean) = apply { this.negative = v }
    fun overflow(v: Boolean) = apply { this.overflow = v }
    fun interrupt(v: Boolean) = apply { this.interrupt = v }
    fun decimal(v: Boolean) = apply { this.decimal = v }
    fun mem(addr: Int, vararg values: Int) = apply {
        values.forEachIndexed { i, v -> _mem[addr + i] = v }
    }
}

class Instruction(val opcode: Int, vararg val operands: Int) {
    val bytes = intArrayOf(opcode) + operands
    val size = 1 + operands.size
}

private fun snapshotCpu(cpu: CPU) = CpuState(
    a         = cpu.a,
    x         = cpu.x,
    y         = cpu.y,
    sp        = cpu.sp,
    pc        = cpu.pc,
    carry     = (cpu.status and FLAG_CARRY)             != 0,
    zero      = (cpu.status and FLAG_ZERO)              != 0,
    negative  = (cpu.status and FLAG_NEGATIVE)          != 0,
    overflow  = (cpu.status and FLAG_OVERFLOW)          != 0,
    interrupt = (cpu.status and FLAG_INTERRUPT_DISABLE) != 0,
    decimal   = (cpu.status and FLAG_DECIMAL)           != 0,
)

class CpuFixture(
    val cpu: CPU,
    private val instruction: Instruction,
    private val startAddress: Int,
) {
    fun withState(setup: CpuSetup) = apply {
        setup.a?.let  { cpu.a  = it }
        setup.x?.let  { cpu.x  = it }
        setup.y?.let  { cpu.y  = it }
        setup.sp?.let { cpu.sp = it }

        var s = cpu.status
        setup.carry?.let     { s = if (it) s or FLAG_CARRY             else s and FLAG_CARRY.inv() }
        setup.zero?.let      { s = if (it) s or FLAG_ZERO              else s and FLAG_ZERO.inv() }
        setup.negative?.let  { s = if (it) s or FLAG_NEGATIVE          else s and FLAG_NEGATIVE.inv() }
        setup.overflow?.let  { s = if (it) s or FLAG_OVERFLOW          else s and FLAG_OVERFLOW.inv() }
        setup.interrupt?.let { s = if (it) s or FLAG_INTERRUPT_DISABLE else s and FLAG_INTERRUPT_DISABLE.inv() }
        setup.decimal?.let   { s = if (it) s or FLAG_DECIMAL           else s and FLAG_DECIMAL.inv() }
        cpu.status = s

        setup.mem.forEach { (addr, v) -> cpu.memory.write(addr, v) }
    }

    fun assertDelta(expected: ExpectedStepOutcome) {
        val before = snapshotCpu(cpu)
        val writes = mutableMapOf<Int, Int>()
        cpu.memory.addJournal { addr, v -> writes[addr] = v }

        val actualCycles = cpu.step()
        val after = snapshotCpu(cpu)

        withClue("cycles") { actualCycles shouldBe expected.cycles }
        withClue("pc") { cpu.pc shouldBe (expected.pc ?: (startAddress + instruction.size)) }
        expected.verify(computeDelta(before, after, writes), after)
    }

    private fun computeDelta(before: CpuState, after: CpuState, memWrites: Map<Int, Int>) = CpuDelta(
        a         = if (after.a         != before.a)         after.a         else null,
        x         = if (after.x         != before.x)         after.x         else null,
        y         = if (after.y         != before.y)         after.y         else null,
        sp        = if (after.sp        != before.sp)        after.sp        else null,
        carry     = if (after.carry     != before.carry)     after.carry     else null,
        zero      = if (after.zero      != before.zero)      after.zero      else null,
        negative  = if (after.negative  != before.negative)  after.negative  else null,
        overflow  = if (after.overflow  != before.overflow)  after.overflow  else null,
        interrupt = if (after.interrupt != before.interrupt) after.interrupt else null,
        decimal   = if (after.decimal   != before.decimal)   after.decimal   else null,
        mem       = memWrites,
    )
}

fun cpu(instruction: Instruction, address: Int = 0x8000, ppu: PPU = PPU(emptyList())): CpuFixture {
    val prgRom = ByteArray(0x4000)
    val offset = address - 0x8000
    instruction.bytes.forEachIndexed { i, b -> prgRom[offset + i] = b.toByte() }
    prgRom[0x3FFC] = (address and 0xFF).toByte()
    prgRom[0x3FFD] = ((address shr 8) and 0xFF).toByte()
    val cpu = CPU(MemoryBus(ppu, prgRom))
    cpu.reset()
    return CpuFixture(cpu, instruction, startAddress = address)
}

suspend fun FreeSpecContainerScope.testStep(
    label: String,
    instruction: Instruction,
    setup: CpuSetup,
    expected: ExpectedStepOutcome,
    address: Int = 0x8000,
) = label {
    cpu(instruction, address).withState(setup).assertDelta(expected)
}
