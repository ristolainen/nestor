---
name: CpuFixture test DSL
description: Plan for a new CPU test DSL and test class based on reference material
type: project
---

# Plan: CpuFixture test DSL

## Context
`CPUTest.kt` is noisy and tests give no guarantee that an instruction didn't silently modify something it shouldn't. The goal is a new test DSL and a new test class written against the reference files — not the existing implementation. The original `CPUTest.kt` is kept untouched until the new class is verified and the old one can be deleted.

---

## Type system

### `CpuState` — full non-nullable snapshot
Used internally by the fixture to capture CPU state before and after `step()`.

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

### `CpuDelta` — computed actual diff
Derived by the fixture by comparing two `CpuState` instances plus memory writes. Represents what actually changed. Does **not** include `pc` — PC is handled separately in `assertDelta`.

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

### `ExpectedStepOutcome` — test-authored declaration of expected changes
Written by the test author. `cycles` is required. All other fields nullable — `null` means "I expect this didn't change." Contains the `verify()` comparison logic.

```kotlin
data class ExpectedStepOutcome(
    val cycles: Int,              // required
    val a: Int? = null,
    val x: Int? = null,
    val y: Int? = null,
    val sp: Int? = null,
    val pc: Int? = null,          // see PC rule below
    val carry: Boolean? = null,
    val zero: Boolean? = null,
    val negative: Boolean? = null,
    val overflow: Boolean? = null,
    val interrupt: Boolean? = null,
    val decimal: Boolean? = null,
    val mem: Map<Int, Int> = emptyMap(),
) {
    fun verify(actual: CpuDelta, after: CpuState) {
        // Rule 1: For each non-null field in this (except pc, which is checked in assertDelta),
        //         assert it matches the corresponding field in `after`.
        //         This handles both "flag was changed to X" and "flag stayed X" cases.
        // Rule 2: For each non-null field in `actual` not covered by a non-null field in this,
        //         fail with "unexpected change to <field>"
        // Rule 3: Assert actual.mem == this.mem (unexpected writes fail; missing expected writes fail)
    }
}
```

**Why `verify()` takes both `CpuDelta` and `CpuState`:**
Rule 1 checks need the final state (so `expected.zero = false` passes when zero was already false and stayed false). Rule 2 checks need the delta (to detect anything that changed unexpectedly). Both are required.

### `CpuSetup` — fluent builder for initial CPU state and memory
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

Note: no `pc` field — initial PC is controlled by the `address` parameter on `cpu()`.

### `Instruction` — typed instruction bytes
```kotlin
class Instruction(val opcode: Int, vararg val operands: Int) {
    val bytes = intArrayOf(opcode) + operands
    val size = 1 + operands.size
}
```

---

## PC rule
- **`pc` omitted from `ExpectedStepOutcome`** → `assertDelta` asserts `cpu.pc == startAddress + instruction.size`. Free check for all normal instructions.
- **`pc` specified** → `assertDelta` asserts `cpu.pc == expected.pc`. Required for JMP, JSR, RTS, and taken branches.

PC is intentionally **not** part of `CpuDelta` — it is always checked explicitly in `assertDelta` using this rule.

---

## MemoryBus journals
`MemoryBus` gets a journal list. Any number of listeners can attach; each is called on every write.

```kotlin
class MemoryBus(...) {
    private val journals = mutableListOf<(addr: Int, value: Int) -> Unit>()

    fun addJournal(fn: (addr: Int, value: Int) -> Unit) { journals.add(fn) }

    fun write(addr: Int, value: Int) {
        // ... existing logic ...
        journals.forEach { it(addr, value) }
    }
}
```

No `removeJournal` is needed — each test creates a fresh `CPU`/`MemoryBus` via `cpu()`, so journals from one test never affect another.

**Watch out:** `incrementMemory` in `CPU.kt` currently does two writes to the same address (`memory.write(addr, v)` then `memory.write(addr, nv)`). The journal will capture both. The test expectation for `INC` should only assert the final value — the verify logic matches `expected.mem` against the *last* write per address, so use `writes[addr] = v` (map semantics naturally keep only the last write, which is correct).

The first write of `v` is there to trigger flag side-effects in some implementations; on this CPU it has no observable effect on flags (flags are set by `setZN(nv)` afterward). So the double-write is invisible from outside and INC tests work normally.

---

## CpuFixture

```kotlin
class CpuFixture(val cpu: CPU, private val instruction: Instruction, private val startAddress: Int) {

    fun withState(setup: CpuSetup) = apply {
        // apply non-null register fields to cpu
        // apply non-null flag fields to cpu.status
        // write setup.mem entries via cpu.memory.write()
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

    private fun computeDelta(before: CpuState, after: CpuState, memWrites: Map<Int, Int>): CpuDelta {
        // diff before vs after for each field; set to new value if changed, null if unchanged
        // mem = memWrites (last write per address)
    }
}
```

## `cpu()` factory

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

## `testStep` — registers an independent named test case

```kotlin
suspend fun FreeSpecContainerScope.testStep(
    label: String,
    instruction: Instruction,
    setup: CpuSetup,
    expected: ExpectedStepOutcome
) = label {
    cpu(instruction).withState(setup).assertDelta(expected)
}
```

---

## Test shape

```kotlin
"LDA immediate" - {
    testStep(
        "non-zero",
        Instruction(0xA9, 0x42),
        CpuSetup(),
        ExpectedStepOutcome(cycles = 2, a = 0x42, zero = false, negative = false),
    )
    testStep(
        "zero",
        Instruction(0xA9, 0x00),
        CpuSetup(),
        ExpectedStepOutcome(cycles = 2, a = 0x00, zero = true, negative = false),
    )
    testStep(
        "negative",
        Instruction(0xA9, 0x80),
        CpuSetup(),
        ExpectedStepOutcome(cycles = 2, a = 0x80, zero = false, negative = true),
    )
}

"LDA (zp,X) indexed-indirect" - {
    testStep(
        "basic",
        Instruction(0xA1, 0x10),
        CpuSetup().x(0x04).mem(0x14, 0x00, 0x10).mem(0x1000, 0x80),
        ExpectedStepOutcome(cycles = 6, a = 0x80, zero = false, negative = true),
    )
    testStep(
        "zp wrap",
        Instruction(0xA1, 0xFF),
        CpuSetup().x(0x02).mem(0x01, 0x00, 0x10).mem(0x1000, 0x2A),
        ExpectedStepOutcome(cycles = 6, a = 0x2A, zero = false, negative = false),
    )
}

"JSR absolute" - {
    testStep(
        "jump to \$C123",
        Instruction(0x20, 0x23, 0xC1),
        CpuSetup().sp(0xFF),
        ExpectedStepOutcome(cycles = 6, pc = 0xC123, sp = 0xFD, mem = mapOf(0x01FF to 0x80, 0x01FE to 0x02)),
    )
}
```

---

## Files to create / modify

| File | Change |
|------|--------|
| `src/main/kotlin/nestor/MemoryBus.kt` | Add journal list + `addJournal` |
| `src/test/kotlin/nestor/TestUtils.kt` | Add `CpuState`, `CpuDelta`, `ExpectedStepOutcome`, `CpuSetup`, `Instruction`, `CpuFixture`, `cpu()`, `testStep()` |
| `src/test/kotlin/nestor/CPUInstructionTest.kt` | **New file** — new test class |
| `src/test/kotlin/nestor/CPUTest.kt` | **Do not touch** |

---

## New test class: CPUInstructionTest.kt

- Based on `.claude/reference/ref_6502_instructions.md` — not derived from the existing `CPUTest.kt`
- Covers only opcodes currently implemented in `CPU.kt`:

```
0x01 0x05 0x09 0x0D 0x10 0x11 0x15 0x19 0x1D 0x20 0x21 0x24 0x25 0x29 0x2C 0x2D
0x30 0x31 0x35 0x39 0x3D 0x48 0x4A 0x4C 0x50 0x60 0x68 0x6C 0x70 0x78 0x81 0x84
0x85 0x86 0x88 0x8A 0x8D 0x90 0x91 0x94 0x95 0x96 0x98 0x99 0x9A 0x9D 0xA0 0xA1
0xA2 0xA9 0xAA 0xAC 0xAD 0xAE 0xB0 0xB1 0xB9 0xBD 0xBE 0xC0 0xC8 0xC9 0xCA 0xD0
0xD8 0xE0 0xE6 0xE8 0xEA 0xEE 0xF0 0xF6 0xFE
```

- Each instruction group has one `"<mnemonic> <addressing mode>" -` container
- Every case within it is a `testStep(...)` call — one argument per line
- Test cases are derived from reference behaviour (flags, cycle counts, addressing) not from reading the current implementation

---

## Verification
```bash
./gradlew test --tests "nestor.CPUInstructionTest"
./gradlew test --tests "nestor.CPUTest"   # must still pass untouched
```
