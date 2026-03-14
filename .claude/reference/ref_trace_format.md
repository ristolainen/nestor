# Nestor Trace Format

## CPU Trace Line

Produced by `CPU.traceLine()` in `Trace.kt`. Call **before** `cpu.step()`.
Format matches nestest.log for easy diffing.

```
C000  4C F5 C5  JMP $C5F5                       A:00 X:00 Y:00 P:24 SP:FD PPU:  0, 21 CYC:7
│     │         │                               │    │    │    │    │     │              │
PC    raw bytes  disassembly (padded to 31 ch)  A    X    Y    P    SP    PPU scanline,dot  total CPU cycles
```

### Fields

| Field | Width | Notes |
|-------|-------|-------|
| PC    | 4 hex digits | Program counter before execution |
| bytes | up to 8 chars | Opcode + 0–2 operand bytes, space-separated, padded |
| disasm | 31 chars (padded) | Mnemonic + formatted operand |
| A/X/Y | 2 hex digits | Register values at time of trace (before step) |
| P     | 2 hex digits | Status register byte |
| SP    | 2 hex digits | Stack pointer |
| PPU   | `SL, DOT` (3 chars each, right-aligned) | PPU scanline and dot at time of trace |
| CYC   | decimal | Cumulative CPU cycles elapsed |

### Operand formatting by addressing mode

| Mode | Example |
|------|---------|
| IMP/ACC | _(blank)_ |
| IMM  | `#$80` |
| ZP   | `$F0` |
| ZPX  | `$F0,X` |
| ZPY  | `$F0,Y` |
| REL  | `$C123` _(resolved absolute target address)_ |
| ABS  | `$2000` |
| ABX  | `$2000,X` |
| ABY  | `$2000,Y` |
| IND  | `($FFFE)` |
| INX  | `($20,X)` |
| INY  | `($20),Y` |

---

## PPU Event Line

Produced by `PPU.traceEvent(label, cpuCycles)` in `Trace.kt`. Call from `cpuWrite`/`cpuRead` with a descriptive label.

```
[PPU CYC=1240] WRITE $2006=3F | SL=  0 DOT=  0 v=0000 t=3F00 w=0 ctrl=80 mask=00 status=00
│              │                  │       │       │      │      │   │        │       │
CPU cycle      label (16 ch pad)  scanline dot     v reg  t reg  w  PPUCTRL  PPUMASK PPUSTATUS
```

---

## Diagnosing traces

### Cycle count check

Each instruction's CYC should increase by the expected base cycles (see `ref_6502_instructions.md`). Page-cross penalties add 1 extra cycle for ABX/ABY/INY reads and branches taken across pages.

```
8000  A9 80     LDA #$80   ... CYC:10
8002  8D 00 20  STA $2000  ... CYC:12   ← +2 (LDA IMM = 2 cycles ✓)
8005  4C 00 80  JMP $8000  ... CYC:16   ← +4 (STA ABS = 4 cycles ✓)
                                         ← +3 (JMP ABS = 3 cycles ✓)
```

### Flag check (P byte)

P is a bitmask: `N V - B D I Z C` (bit 7 → bit 0).

Common values:
- `24` = `0010 0100` = interrupt disable + unused bit set (reset state)
- `A4` = `1010 0100` = negative + interrupt disable + unused
- `27` = `0010 0111` = interrupt disable + zero + carry + unused

### First divergence from nestest.log

When comparing to the reference log:
1. Find the first line where PC, bytes, or CYC differ.
2. The instruction **before** that line is usually the one that misbehaved (wrong cycles, wrong flag write, wrong memory write).
3. Check A/X/Y/P/SP on that line — if registers are already wrong, the bug is earlier still.

### PPU dot/scanline sanity

- Each CPU cycle = 3 PPU dots.
- Dots 0–340 per scanline (341 total), scanlines 0–261 (262 total).
- VBlank NMI fires at scanline 241, dot 1.
- If dots jump by more/less than `cpuCycles * 3`, the PPU tick call is wrong.
