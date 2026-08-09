# Implementation Plan: CpuFixture test DSL

## Overview

Three files change. One file is created. `CPUTest.kt` is never touched.

---

## 1. `src/main/kotlin/nestor/MemoryBus.kt`

Add a journal list and `addJournal()` after the existing constructor fields. Call journals at the end of every `write()` branch.

```kotlin
// After the cpuRam field, before read():
private val journals = mutableListOf<(addr: Int, value: Int) -> Unit>()

fun addJournal(fn: (addr: Int, value: Int) -> Unit) { journals.add(fn) }
```

In `write()`, append one line at the end of each `when` branch that actually performs a write (the two RAM branches and the PPU branch). The `PRG-ROM` and `else` branches are no-ops so they skip it.

Actually simpler: add a single call after the `when` block:

```kotlin
fun write(address: Int, value: Int) {
    when (address) { ... }   // existing
    journals.forEach { it(address, value) }
}
```

> This fires for every `write()` call including the no-op branches, but that is harmless — the journal just records the address/value as passed, not what was actually stored. For PRG-ROM writes (ignored) the journal will still record them. If that turns out to be an issue, move the call inside the individual branches. For now, no test writes to PRG-ROM addresses so it won't matter.

---

## 2. `src/test/kotlin/nestor/TestUtils.kt`

Append all DSL types and functions at the bottom of the existing file (after `printAnsiTile`). No existing code is removed or changed.

### 2a. `CpuState`

```kotlin
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
```

### 2b. `CpuDelta`

No `pc` field — PC is checked separately in `assertDelta`.

```kotlin
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
```

### 2c. `ExpectedStepOutcome`

`verify()` takes both the delta (to detect unexpected changes via rule 2) and the after-state (to check expected final values via rule 1).

```kotlin
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
        if (a        == null) withClue("unexpected change to a")        { actual.a        shouldBe null }
        if (x        == null) withClue("unexpected change to x")        { actual.x        shouldBe null }
        if (y        == null) withClue("unexpected change to y")        { actual.y        shouldBe null }
        if (sp       == null) withClue("unexpected change to sp")       { actual.sp       shouldBe null }
        if (carry    == null) withClue("unexpected change to carry")    { actual.carry    shouldBe null }
        if (zero     == null) withClue("unexpected change to zero")     { actual.zero     shouldBe null }
        if (negative == null) withClue("unexpected change to negative") { actual.negative shouldBe null }
        if (overflow == null) withClue("unexpected change to overflow") { actual.overflow shouldBe null }
        if (interrupt == null) withClue("unexpected change to interrupt") { actual.interrupt shouldBe null }
        if (decimal  == null) withClue("unexpected change to decimal")  { actual.decimal  shouldBe null }

        // Rule 3 — memory writes must match exactly
        withClue("memory writes") { actual.mem shouldBe mem }
    }
}
```

### 2d. `CpuSetup`

Properties and fluent setters share the same name — valid in Kotlin (property access `setup.a` vs call `setup.a(v)` are distinct).

```kotlin
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
```

### 2e. `Instruction`

```kotlin
class Instruction(val opcode: Int, vararg val operands: Int) {
    val bytes = intArrayOf(opcode) + operands
    val size = 1 + operands.size
}
```

### 2f. Helper: `snapshotCpu`

Private to the fixture but defined as a top-level private helper in TestUtils.

```kotlin
private fun snapshotCpu(cpu: CPU) = CpuState(
    a         = cpu.a,
    x         = cpu.x,
    y         = cpu.y,
    sp        = cpu.sp,
    pc        = cpu.pc,
    carry     = (cpu.status and FLAG_CARRY)            != 0,
    zero      = (cpu.status and FLAG_ZERO)             != 0,
    negative  = (cpu.status and FLAG_NEGATIVE)         != 0,
    overflow  = (cpu.status and FLAG_OVERFLOW)         != 0,
    interrupt = (cpu.status and FLAG_INTERRUPT_DISABLE) != 0,
    decimal   = (cpu.status and FLAG_DECIMAL)          != 0,
)
```

### 2g. `CpuFixture`

```kotlin
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

        actualCycles shouldBe expected.cycles
        cpu.pc shouldBe (expected.pc ?: (startAddress + instruction.size))
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
```

### 2h. `cpu()` factory

```kotlin
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
```

### 2i. `testStep()`

```kotlin
suspend fun FreeSpecContainerScope.testStep(
    label: String,
    instruction: Instruction,
    setup: CpuSetup,
    expected: ExpectedStepOutcome,
    address: Int = 0x8000,
) = label {
    cpu(instruction, address).withState(setup).assertDelta(expected)
}
```

**Imports to add to TestUtils.kt:**
```kotlin
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
```

---

## 3. `src/test/kotlin/nestor/CPUInstructionTest.kt` — new file

Full class skeleton and all test groups. Each group maps to one `"<MNEMONIC> <mode>" -` container, each case is one `testStep(...)` call with one argument per line.

### Initial CPU state after `cpu().withState(CpuSetup())`
- `a = x = y = sp = 0`
- `status = 0x24` → carry=F, zero=F, interrupt=**T**, decimal=F, overflow=F, negative=F
- Interrupt is **true** by default. Tests that need to verify a flag change must set the flag to the opposite state in `CpuSetup` first.

### Address constraints for test memory
- Zero page (0x00–0xFF): always RAM ✓
- Absolute addresses used in tests: keep in 0x0000–0x1FFF (RAM) to avoid PPU registers (0x2000+) ✓
- Stack (0x0100–0x01FF): RAM ✓
- Indirect target pointers: keep targets in 0x0000–0x1FFF ✓

### Branch PC arithmetic
For a branch instruction at `address`:
- After reading opcode + offset: `PC = address + 2`
- Not taken: `PC stays at address + 2` → omit `pc` in expected, fixture checks automatically
- Taken same page: `PC = (address + 2) + signedOffset` → specify `pc`
- Taken cross page: same formula, +1 cycle → specify `pc` and `cycles = 4`

### Test groups

```kotlin
class CPUInstructionTest : FreeSpec({

    // ── ORA ──────────────────────────────────────────────────────────────
    "ORA immediate" - {
        testStep("sets result", Instruction(0x09, 0x0F), CpuSetup().a(0xF0),
            ExpectedStepOutcome(cycles = 2, a = 0xFF, zero = false, negative = true))
        testStep("zero result", Instruction(0x09, 0x00), CpuSetup().a(0x00),
            ExpectedStepOutcome(cycles = 2, a = 0x00, zero = true, negative = false))
    }
    "ORA zero page" - {
        testStep("basic", Instruction(0x05, 0x10), CpuSetup().a(0x0F).mem(0x10, 0xF0),
            ExpectedStepOutcome(cycles = 3, a = 0xFF, zero = false, negative = true))
    }
    "ORA zero page,X" - {
        testStep("indexed", Instruction(0x15, 0x10), CpuSetup().a(0x0F).x(0x04).mem(0x14, 0x70),
            ExpectedStepOutcome(cycles = 4, a = 0x7F, zero = false, negative = false))
        testStep("zero page wraps", Instruction(0x15, 0xFF), CpuSetup().a(0x01).x(0x02).mem(0x01, 0x02),
            ExpectedStepOutcome(cycles = 4, a = 0x03, zero = false, negative = false))
    }
    "ORA absolute" - {
        testStep("basic", Instruction(0x0D, 0x00, 0x02), CpuSetup().a(0x0F).mem(0x0200, 0xF0),
            ExpectedStepOutcome(cycles = 4, a = 0xFF, zero = false, negative = true))
    }
    "ORA absolute,X" - {
        testStep("no page cross", Instruction(0x1D, 0x00, 0x02), CpuSetup().a(0x01).x(0x01).mem(0x0201, 0x02),
            ExpectedStepOutcome(cycles = 4, a = 0x03, zero = false, negative = false))
        testStep("page cross +1 cycle", Instruction(0x1D, 0xFF, 0x01), CpuSetup().a(0x01).x(0x01).mem(0x0200, 0x02),
            ExpectedStepOutcome(cycles = 5, a = 0x03, zero = false, negative = false))
    }
    "ORA absolute,Y" - {
        testStep("no page cross", Instruction(0x19, 0x00, 0x02), CpuSetup().a(0x01).y(0x01).mem(0x0201, 0x02),
            ExpectedStepOutcome(cycles = 4, a = 0x03, zero = false, negative = false))
        testStep("page cross +1 cycle", Instruction(0x19, 0xFF, 0x01), CpuSetup().a(0x01).y(0x01).mem(0x0200, 0x02),
            ExpectedStepOutcome(cycles = 5, a = 0x03, zero = false, negative = false))
    }
    "ORA (indirect,X)" - {
        testStep("basic", Instruction(0x01, 0x10), CpuSetup().a(0x0F).x(0x04).mem(0x14, 0x00, 0x02).mem(0x0200, 0xF0),
            ExpectedStepOutcome(cycles = 6, a = 0xFF, zero = false, negative = true))
        testStep("zero page pointer wraps", Instruction(0x01, 0xFF), CpuSetup().a(0x01).x(0x02).mem(0x01, 0x00, 0x02).mem(0x0200, 0x02),
            ExpectedStepOutcome(cycles = 6, a = 0x03, zero = false, negative = false))
    }
    "ORA (indirect),Y" - {
        testStep("no page cross", Instruction(0x11, 0x10), CpuSetup().a(0x0F).y(0x01).mem(0x10, 0x00, 0x02).mem(0x0201, 0xF0),
            ExpectedStepOutcome(cycles = 5, a = 0xFF, zero = false, negative = true))
        testStep("page cross +1 cycle", Instruction(0x11, 0x10), CpuSetup().a(0x01).y(0x01).mem(0x10, 0xFF, 0x01).mem(0x0200, 0x02),
            ExpectedStepOutcome(cycles = 6, a = 0x03, zero = false, negative = false))
    }

    // ── AND ──────────────────────────────────────────────────────────────
    "AND immediate" - {
        testStep("masks bits", Instruction(0x29, 0x0F), CpuSetup().a(0xFF),
            ExpectedStepOutcome(cycles = 2, a = 0x0F, zero = false, negative = false))
        testStep("zero result", Instruction(0x29, 0x00), CpuSetup().a(0xFF),
            ExpectedStepOutcome(cycles = 2, a = 0x00, zero = true, negative = false))
        testStep("negative result", Instruction(0x29, 0xFF), CpuSetup().a(0x80),
            ExpectedStepOutcome(cycles = 2, a = 0x80, zero = false, negative = true))
    }
    "AND zero page" - {
        testStep("basic", Instruction(0x25, 0x10), CpuSetup().a(0xFF).mem(0x10, 0x0F),
            ExpectedStepOutcome(cycles = 3, a = 0x0F, zero = false, negative = false))
    }
    "AND zero page,X" - {
        testStep("indexed", Instruction(0x35, 0x10), CpuSetup().a(0xFF).x(0x04).mem(0x14, 0x0F),
            ExpectedStepOutcome(cycles = 4, a = 0x0F, zero = false, negative = false))
    }
    "AND absolute" - {
        testStep("basic", Instruction(0x2D, 0x00, 0x02), CpuSetup().a(0xFF).mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 4, a = 0x0F, zero = false, negative = false))
    }
    "AND absolute,X" - {
        testStep("no page cross", Instruction(0x3D, 0x00, 0x02), CpuSetup().a(0xFF).x(0x01).mem(0x0201, 0x0F),
            ExpectedStepOutcome(cycles = 4, a = 0x0F, zero = false, negative = false))
        testStep("page cross +1 cycle", Instruction(0x3D, 0xFF, 0x01), CpuSetup().a(0xFF).x(0x01).mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 5, a = 0x0F, zero = false, negative = false))
    }
    "AND absolute,Y" - {
        testStep("no page cross", Instruction(0x39, 0x00, 0x02), CpuSetup().a(0xFF).y(0x01).mem(0x0201, 0x0F),
            ExpectedStepOutcome(cycles = 4, a = 0x0F, zero = false, negative = false))
        testStep("page cross +1 cycle", Instruction(0x39, 0xFF, 0x01), CpuSetup().a(0xFF).y(0x01).mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 5, a = 0x0F, zero = false, negative = false))
    }
    "AND (indirect,X)" - {
        testStep("basic", Instruction(0x21, 0x10), CpuSetup().a(0xFF).x(0x04).mem(0x14, 0x00, 0x02).mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 6, a = 0x0F, zero = false, negative = false))
    }
    "AND (indirect),Y" - {
        testStep("no page cross", Instruction(0x31, 0x10), CpuSetup().a(0xFF).y(0x01).mem(0x10, 0x00, 0x02).mem(0x0201, 0x0F),
            ExpectedStepOutcome(cycles = 5, a = 0x0F, zero = false, negative = false))
        testStep("page cross +1 cycle", Instruction(0x31, 0x10), CpuSetup().a(0xFF).y(0x01).mem(0x10, 0xFF, 0x01).mem(0x0200, 0x0F),
            ExpectedStepOutcome(cycles = 6, a = 0x0F, zero = false, negative = false))
    }

    // ── BIT ──────────────────────────────────────────────────────────────
    // BIT: Z = (A & M) == 0, N = M bit 7, V = M bit 6. A is not modified.
    "BIT zero page" - {
        testStep("all flags set", Instruction(0x24, 0x10), CpuSetup().a(0x00).mem(0x10, 0xFF),
            ExpectedStepOutcome(cycles = 3, zero = true, negative = true, overflow = true))
        testStep("Z clear N clear V clear", Instruction(0x24, 0x10), CpuSetup().a(0xFF).mem(0x10, 0x3F),
            ExpectedStepOutcome(cycles = 3, zero = false, negative = false, overflow = true))
    }
    "BIT absolute" - {
        testStep("Z set N set V set", Instruction(0x2C, 0x00, 0x02), CpuSetup().a(0x00).mem(0x0200, 0xFF),
            ExpectedStepOutcome(cycles = 4, zero = true, negative = true, overflow = true))
    }

    // ── LSR ──────────────────────────────────────────────────────────────
    // LSR accumulator: C = old bit 0, N always 0
    "LSR accumulator" - {
        testStep("shifts right, carry set", Instruction(0x4A), CpuSetup().a(0x03),
            ExpectedStepOutcome(cycles = 2, a = 0x01, carry = true, zero = false, negative = false))
        testStep("even value, carry clear", Instruction(0x4A), CpuSetup().a(0x80),
            ExpectedStepOutcome(cycles = 2, a = 0x40, carry = false, zero = false, negative = false))
        testStep("result zero", Instruction(0x4A), CpuSetup().a(0x01),
            ExpectedStepOutcome(cycles = 2, a = 0x00, carry = true, zero = true, negative = false))
    }

    // ── LDA ──────────────────────────────────────────────────────────────
    "LDA immediate" - {
        testStep("positive value", Instruction(0xA9, 0x42), CpuSetup(),
            ExpectedStepOutcome(cycles = 2, a = 0x42, zero = false, negative = false))
        testStep("zero sets Z", Instruction(0xA9, 0x00), CpuSetup(),
            ExpectedStepOutcome(cycles = 2, a = 0x00, zero = true, negative = false))
        testStep("negative sets N", Instruction(0xA9, 0x80), CpuSetup(),
            ExpectedStepOutcome(cycles = 2, a = 0x80, zero = false, negative = true))
    }
    "LDA absolute" - {
        testStep("basic", Instruction(0xAD, 0x00, 0x02), CpuSetup().mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 4, a = 0x42, zero = false, negative = false))
    }
    "LDA absolute,X" - {
        testStep("no page cross", Instruction(0xBD, 0x00, 0x02), CpuSetup().x(0x01).mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 4, a = 0x42, zero = false, negative = false))
        testStep("page cross +1 cycle", Instruction(0xBD, 0xFF, 0x01), CpuSetup().x(0x01).mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 5, a = 0x42, zero = false, negative = false))
    }
    "LDA absolute,Y" - {
        testStep("no page cross", Instruction(0xB9, 0x00, 0x02), CpuSetup().y(0x01).mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 4, a = 0x42, zero = false, negative = false))
        testStep("page cross +1 cycle", Instruction(0xB9, 0xFF, 0x01), CpuSetup().y(0x01).mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 5, a = 0x42, zero = false, negative = false))
    }
    "LDA (indirect,X)" - {
        testStep("basic", Instruction(0xA1, 0x10), CpuSetup().x(0x04).mem(0x14, 0x00, 0x02).mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 6, a = 0x42, zero = false, negative = false))
        testStep("zero page pointer wraps", Instruction(0xA1, 0xFF), CpuSetup().x(0x02).mem(0x01, 0x00, 0x02).mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 6, a = 0x42, zero = false, negative = false))
    }
    "LDA (indirect),Y" - {
        testStep("no page cross", Instruction(0xB1, 0x10), CpuSetup().y(0x01).mem(0x10, 0x00, 0x02).mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 5, a = 0x42, zero = false, negative = false))
        testStep("page cross +1 cycle", Instruction(0xB1, 0x10), CpuSetup().y(0x01).mem(0x10, 0xFF, 0x01).mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 6, a = 0x42, zero = false, negative = false))
    }

    // ── LDX ──────────────────────────────────────────────────────────────
    "LDX immediate" - {
        testStep("positive value", Instruction(0xA2, 0x42), CpuSetup(),
            ExpectedStepOutcome(cycles = 2, x = 0x42, zero = false, negative = false))
        testStep("zero sets Z", Instruction(0xA2, 0x00), CpuSetup(),
            ExpectedStepOutcome(cycles = 2, x = 0x00, zero = true, negative = false))
    }
    "LDX absolute" - {
        testStep("basic", Instruction(0xAE, 0x00, 0x02), CpuSetup().mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 4, x = 0x42, zero = false, negative = false))
    }
    "LDX absolute,Y" - {
        testStep("no page cross", Instruction(0xBE, 0x00, 0x02), CpuSetup().y(0x01).mem(0x0201, 0x42),
            ExpectedStepOutcome(cycles = 4, x = 0x42, zero = false, negative = false))
        testStep("page cross +1 cycle", Instruction(0xBE, 0xFF, 0x01), CpuSetup().y(0x01).mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 5, x = 0x42, zero = false, negative = false))
    }

    // ── LDY ──────────────────────────────────────────────────────────────
    "LDY immediate" - {
        testStep("positive value", Instruction(0xA0, 0x42), CpuSetup(),
            ExpectedStepOutcome(cycles = 2, y = 0x42, zero = false, negative = false))
        testStep("zero sets Z", Instruction(0xA0, 0x00), CpuSetup(),
            ExpectedStepOutcome(cycles = 2, y = 0x00, zero = true, negative = false))
    }
    "LDY absolute" - {
        testStep("basic", Instruction(0xAC, 0x00, 0x02), CpuSetup().mem(0x0200, 0x42),
            ExpectedStepOutcome(cycles = 4, y = 0x42, zero = false, negative = false))
    }

    // ── STA ──────────────────────────────────────────────────────────────
    "STA zero page" - {
        testStep("stores A", Instruction(0x85, 0x10), CpuSetup().a(0x42),
            ExpectedStepOutcome(cycles = 3, mem = mapOf(0x10 to 0x42)))
    }
    "STA zero page,X" - {
        testStep("indexed store", Instruction(0x95, 0x10), CpuSetup().a(0x42).x(0x04),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x14 to 0x42)))
        testStep("zero page wraps", Instruction(0x95, 0xFF), CpuSetup().a(0x42).x(0x02),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x01 to 0x42)))
    }
    "STA absolute" - {
        testStep("stores A", Instruction(0x8D, 0x00, 0x02), CpuSetup().a(0x42),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x0200 to 0x42)))
    }
    "STA absolute,X" - {
        testStep("stores A", Instruction(0x9D, 0x00, 0x02), CpuSetup().a(0x42).x(0x01),
            ExpectedStepOutcome(cycles = 5, mem = mapOf(0x0201 to 0x42)))
    }
    "STA absolute,Y" - {
        testStep("stores A", Instruction(0x99, 0x00, 0x02), CpuSetup().a(0x42).y(0x01),
            ExpectedStepOutcome(cycles = 5, mem = mapOf(0x0201 to 0x42)))
    }
    "STA (indirect,X)" - {
        testStep("stores A", Instruction(0x81, 0x10), CpuSetup().a(0x42).x(0x04).mem(0x14, 0x00, 0x02),
            ExpectedStepOutcome(cycles = 6, mem = mapOf(0x0200 to 0x42)))
    }
    "STA (indirect),Y" - {
        testStep("stores A", Instruction(0x91, 0x10), CpuSetup().a(0x42).y(0x01).mem(0x10, 0x00, 0x02),
            ExpectedStepOutcome(cycles = 6, mem = mapOf(0x0201 to 0x42)))
    }

    // ── STX ──────────────────────────────────────────────────────────────
    "STX zero page" - {
        testStep("stores X", Instruction(0x86, 0x10), CpuSetup().x(0x42),
            ExpectedStepOutcome(cycles = 3, mem = mapOf(0x10 to 0x42)))
    }
    "STX zero page,Y" - {
        testStep("indexed store", Instruction(0x96, 0x10), CpuSetup().x(0x42).y(0x04),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x14 to 0x42)))
    }

    // ── STY ──────────────────────────────────────────────────────────────
    "STY zero page" - {
        testStep("stores Y", Instruction(0x84, 0x10), CpuSetup().y(0x42),
            ExpectedStepOutcome(cycles = 3, mem = mapOf(0x10 to 0x42)))
    }
    "STY zero page,X" - {
        testStep("indexed store", Instruction(0x94, 0x10), CpuSetup().y(0x42).x(0x04),
            ExpectedStepOutcome(cycles = 4, mem = mapOf(0x14 to 0x42)))
    }

    // ── CMP ──────────────────────────────────────────────────────────────
    // CMP: C = A >= M, Z = A == M, N = bit 7 of (A - M)
    "CMP immediate" - {
        testStep("equal sets Z and C", Instruction(0xC9, 0x42), CpuSetup().a(0x42),
            ExpectedStepOutcome(cycles = 2, carry = true, zero = true, negative = false))
        testStep("greater sets C", Instruction(0xC9, 0x01), CpuSetup().a(0x02),
            ExpectedStepOutcome(cycles = 2, carry = true, zero = false, negative = false))
        testStep("less clears C sets N", Instruction(0xC9, 0x02), CpuSetup().a(0x01),
            ExpectedStepOutcome(cycles = 2, carry = false, zero = false, negative = true))
    }

    // ── CPX ──────────────────────────────────────────────────────────────
    "CPX immediate" - {
        testStep("equal sets Z and C", Instruction(0xE0, 0x42), CpuSetup().x(0x42),
            ExpectedStepOutcome(cycles = 2, carry = true, zero = true, negative = false))
        testStep("less clears C sets N", Instruction(0xE0, 0x02), CpuSetup().x(0x01),
            ExpectedStepOutcome(cycles = 2, carry = false, zero = false, negative = true))
    }

    // ── CPY ──────────────────────────────────────────────────────────────
    "CPY immediate" - {
        testStep("equal sets Z and C", Instruction(0xC0, 0x42), CpuSetup().y(0x42),
            ExpectedStepOutcome(cycles = 2, carry = true, zero = true, negative = false))
        testStep("less clears C sets N", Instruction(0xC0, 0x02), CpuSetup().y(0x01),
            ExpectedStepOutcome(cycles = 2, carry = false, zero = false, negative = true))
    }

    // ── INC ──────────────────────────────────────────────────────────────
    // Journal records last write per address (map semantics), so double-write in
    // incrementMemory is transparent — the test sees only the final incremented value.
    "INC zero page" - {
        testStep("increments value", Instruction(0xE6, 0x10), CpuSetup().mem(0x10, 0x41),
            ExpectedStepOutcome(cycles = 5, zero = false, negative = false, mem = mapOf(0x10 to 0x42)))
        testStep("wraps 0xFF to 0x00 sets Z", Instruction(0xE6, 0x10), CpuSetup().mem(0x10, 0xFF),
            ExpectedStepOutcome(cycles = 5, zero = true, negative = false, mem = mapOf(0x10 to 0x00)))
        testStep("result 0x80 sets N", Instruction(0xE6, 0x10), CpuSetup().mem(0x10, 0x7F),
            ExpectedStepOutcome(cycles = 5, zero = false, negative = true, mem = mapOf(0x10 to 0x80)))
    }
    "INC zero page,X" - {
        testStep("indexed increment", Instruction(0xF6, 0x10), CpuSetup().x(0x04).mem(0x14, 0x41),
            ExpectedStepOutcome(cycles = 6, zero = false, negative = false, mem = mapOf(0x14 to 0x42)))
    }
    "INC absolute" - {
        testStep("increments value", Instruction(0xEE, 0x00, 0x02), CpuSetup().mem(0x0200, 0x41),
            ExpectedStepOutcome(cycles = 6, zero = false, negative = false, mem = mapOf(0x0200 to 0x42)))
    }
    "INC absolute,X" - {
        testStep("indexed increment", Instruction(0xFE, 0x00, 0x02), CpuSetup().x(0x01).mem(0x0201, 0x41),
            ExpectedStepOutcome(cycles = 7, zero = false, negative = false, mem = mapOf(0x0201 to 0x42)))
    }

    // ── INX / INY / DEX / DEY ────────────────────────────────────────────
    "INX implied" - {
        testStep("increments X", Instruction(0xE8), CpuSetup().x(0x41),
            ExpectedStepOutcome(cycles = 2, x = 0x42, zero = false, negative = false))
        testStep("wraps 0xFF to 0x00 sets Z", Instruction(0xE8), CpuSetup().x(0xFF),
            ExpectedStepOutcome(cycles = 2, x = 0x00, zero = true, negative = false))
    }
    "INY implied" - {
        testStep("increments Y", Instruction(0xC8), CpuSetup().y(0x41),
            ExpectedStepOutcome(cycles = 2, y = 0x42, zero = false, negative = false))
    }
    "DEX implied" - {
        testStep("decrements X", Instruction(0xCA), CpuSetup().x(0x43),
            ExpectedStepOutcome(cycles = 2, x = 0x42, zero = false, negative = false))
        testStep("wraps 0x00 to 0xFF sets N", Instruction(0xCA), CpuSetup().x(0x00),
            ExpectedStepOutcome(cycles = 2, x = 0xFF, zero = false, negative = true))
        testStep("result 0x00 sets Z", Instruction(0xCA), CpuSetup().x(0x01),
            ExpectedStepOutcome(cycles = 2, x = 0x00, zero = true, negative = false))
    }
    "DEY implied" - {
        testStep("decrements Y", Instruction(0x88), CpuSetup().y(0x43),
            ExpectedStepOutcome(cycles = 2, y = 0x42, zero = false, negative = false))
    }

    // ── Transfers ────────────────────────────────────────────────────────
    "TAX implied" - {
        testStep("transfers A to X", Instruction(0xAA), CpuSetup().a(0x42),
            ExpectedStepOutcome(cycles = 2, x = 0x42, zero = false, negative = false))
        testStep("zero sets Z", Instruction(0xAA), CpuSetup().a(0x00),
            ExpectedStepOutcome(cycles = 2, x = 0x00, zero = true, negative = false))
    }
    "TXA implied" - {
        testStep("transfers X to A", Instruction(0x8A), CpuSetup().x(0x42),
            ExpectedStepOutcome(cycles = 2, a = 0x42, zero = false, negative = false))
    }
    "TYA implied" - {
        testStep("transfers Y to A", Instruction(0x98), CpuSetup().y(0x42),
            ExpectedStepOutcome(cycles = 2, a = 0x42, zero = false, negative = false))
    }
    "TXS implied" - {
        // TXS does NOT set flags
        testStep("transfers X to SP", Instruction(0x9A), CpuSetup().x(0xFD),
            ExpectedStepOutcome(cycles = 2, sp = 0xFD))
    }

    // ── PHA / PLA ─────────────────────────────────────────────────────────
    "PHA implied" - {
        testStep("pushes A, decrements SP", Instruction(0x48), CpuSetup().a(0x42).sp(0xFF),
            ExpectedStepOutcome(cycles = 3, sp = 0xFE, mem = mapOf(0x01FF to 0x42)))
    }
    "PLA implied" - {
        testStep("pulls A, sets Z/N, increments SP",
            Instruction(0x68),
            CpuSetup().sp(0xFE).mem(0x01FF, 0x42),
            ExpectedStepOutcome(cycles = 4, a = 0x42, sp = 0xFF, zero = false, negative = false))
        testStep("pulls zero value sets Z",
            Instruction(0x68),
            CpuSetup().sp(0xFE).mem(0x01FF, 0x00),
            ExpectedStepOutcome(cycles = 4, a = 0x00, sp = 0xFF, zero = true, negative = false))
    }

    // ── JMP ──────────────────────────────────────────────────────────────
    "JMP absolute" - {
        testStep("jumps to address",
            Instruction(0x4C, 0x00, 0xC0),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 3, pc = 0xC000))
    }
    "JMP indirect" - {
        testStep("jumps via pointer",
            Instruction(0x6C, 0x00, 0x02),
            CpuSetup().mem(0x0200, 0x00, 0xC0),
            ExpectedStepOutcome(cycles = 5, pc = 0xC000))
        // Hardware bug: indirect JMP wraps within page when low byte is 0xFF
        testStep("page wrap bug: reads hi from same page",
            Instruction(0x6C, 0xFF, 0x02),
            CpuSetup().mem(0x02FF, 0x34).mem(0x0200, 0x12),
            ExpectedStepOutcome(cycles = 5, pc = 0x1234))
    }

    // ── JSR / RTS ─────────────────────────────────────────────────────────
    "JSR absolute" - {
        // JSR at 0x8000: target = 0xC123
        // reads 3 bytes → PC = 0x8003, pushes 0x8003-1 = 0x8002 (hi=0x80, lo=0x02)
        testStep("pushes return address and jumps",
            Instruction(0x20, 0x23, 0xC1),
            CpuSetup().sp(0xFF),
            ExpectedStepOutcome(cycles = 6, pc = 0xC123, sp = 0xFD, mem = mapOf(0x01FF to 0x80, 0x01FE to 0x02)))
    }
    "RTS implied" - {
        // RTS: pull lo then hi, pc = word(lo, hi) + 1
        testStep("pulls return address and increments PC",
            Instruction(0x60),
            CpuSetup().sp(0xFD).mem(0x01FE, 0x02, 0x80),
            ExpectedStepOutcome(cycles = 6, pc = 0x8003, sp = 0xFF))
    }

    // ── Branches ─────────────────────────────────────────────────────────
    // For each branch: not-taken (2 cycles, pc omitted — fixture checks startAddress+2),
    //                  taken same page (3 cycles, pc = startAddress+2+offset),
    //                  taken cross page (4 cycles, address = 0x80FA so PC after reads
    //                  = 0x80FC, target = 0x80FC + 0x04 = 0x8100).
    // Backward-branch: BNE with offset 0xFE (-2 signed), taken from 0x8000,
    //                  target = 0x8002 + (-2) = 0x8000.

    "BPL relative" - {
        testStep("not taken when N set",
            Instruction(0x10, 0x04),
            CpuSetup().negative(true),
            ExpectedStepOutcome(cycles = 2))
        testStep("taken when N clear",
            Instruction(0x10, 0x04),
            CpuSetup().negative(false),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006))
        testStep("taken cross page",
            Instruction(0x10, 0x04),
            CpuSetup().negative(false),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA)
    }
    "BMI relative" - {
        testStep("not taken when N clear",
            Instruction(0x30, 0x04),
            CpuSetup().negative(false),
            ExpectedStepOutcome(cycles = 2))
        testStep("taken when N set",
            Instruction(0x30, 0x04),
            CpuSetup().negative(true),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006))
        testStep("taken cross page",
            Instruction(0x30, 0x04),
            CpuSetup().negative(true),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA)
    }
    "BVC relative" - {
        testStep("not taken when V set",
            Instruction(0x50, 0x04),
            CpuSetup().overflow(true),
            ExpectedStepOutcome(cycles = 2))
        testStep("taken when V clear",
            Instruction(0x50, 0x04),
            CpuSetup().overflow(false),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006))
        testStep("taken cross page",
            Instruction(0x50, 0x04),
            CpuSetup().overflow(false),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA)
    }
    "BVS relative" - {
        testStep("not taken when V clear",
            Instruction(0x70, 0x04),
            CpuSetup().overflow(false),
            ExpectedStepOutcome(cycles = 2))
        testStep("taken when V set",
            Instruction(0x70, 0x04),
            CpuSetup().overflow(true),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006))
        testStep("taken cross page",
            Instruction(0x70, 0x04),
            CpuSetup().overflow(true),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA)
    }
    "BCC relative" - {
        testStep("not taken when C set",
            Instruction(0x90, 0x04),
            CpuSetup().carry(true),
            ExpectedStepOutcome(cycles = 2))
        testStep("taken when C clear",
            Instruction(0x90, 0x04),
            CpuSetup().carry(false),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006))
        testStep("taken cross page",
            Instruction(0x90, 0x04),
            CpuSetup().carry(false),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA)
    }
    "BCS relative" - {
        testStep("not taken when C clear",
            Instruction(0xB0, 0x04),
            CpuSetup().carry(false),
            ExpectedStepOutcome(cycles = 2))
        testStep("taken when C set",
            Instruction(0xB0, 0x04),
            CpuSetup().carry(true),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006))
        testStep("taken cross page",
            Instruction(0xB0, 0x04),
            CpuSetup().carry(true),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA)
    }
    "BNE relative" - {
        testStep("not taken when Z set",
            Instruction(0xD0, 0x04),
            CpuSetup().zero(true),
            ExpectedStepOutcome(cycles = 2))
        testStep("taken forward when Z clear",
            Instruction(0xD0, 0x04),
            CpuSetup().zero(false),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006))
        testStep("taken backward when Z clear",
            Instruction(0xD0, 0xFE),   // offset = -2 signed → target = 0x8002 + (-2) = 0x8000
            CpuSetup().zero(false),
            ExpectedStepOutcome(cycles = 3, pc = 0x8000))
        testStep("taken cross page",
            Instruction(0xD0, 0x04),
            CpuSetup().zero(false),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA)
    }
    "BEQ relative" - {
        testStep("not taken when Z clear",
            Instruction(0xF0, 0x04),
            CpuSetup().zero(false),
            ExpectedStepOutcome(cycles = 2))
        testStep("taken when Z set",
            Instruction(0xF0, 0x04),
            CpuSetup().zero(true),
            ExpectedStepOutcome(cycles = 3, pc = 0x8006))
        testStep("taken cross page",
            Instruction(0xF0, 0x04),
            CpuSetup().zero(true),
            ExpectedStepOutcome(cycles = 4, pc = 0x8100),
            address = 0x80FA)
    }

    // ── SEI ──────────────────────────────────────────────────────────────
    "SEI implied" - {
        testStep("sets I flag",
            Instruction(0x78),
            CpuSetup().interrupt(false),
            ExpectedStepOutcome(cycles = 2, interrupt = true))
    }

    // ── CLD ──────────────────────────────────────────────────────────────
    "CLD implied" - {
        testStep("clears D flag",
            Instruction(0xD8),
            CpuSetup().decimal(true),
            ExpectedStepOutcome(cycles = 2, decimal = false))
    }

    // ── NOP ──────────────────────────────────────────────────────────────
    "NOP implied" - {
        testStep("does nothing",
            Instruction(0xEA),
            CpuSetup(),
            ExpectedStepOutcome(cycles = 2))
    }
})
```

**Imports for CPUInstructionTest.kt:**
```kotlin
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
```

---

## 4. Required TestUtils.kt imports

```kotlin
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
```

---

