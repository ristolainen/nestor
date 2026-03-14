# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Nestor** is a NES (Nintendo Entertainment System) emulator written in Kotlin for the JVM. It targets Mapper 0 (NROM) and aims to eventually run games like Super Mario Bros.

Current focus: **Phase 2** — completing remaining 6502 instructions (ADC, SBC, ROL, ROR and addressing mode gaps), PPU scroll handling, and 60 FPS frame timing.

## Commands

```bash
./gradlew build         # Compile and build
./gradlew test          # Run full test suite
./gradlew run           # Run emulator (loads Super Mario Bros)
```

To run a single test class:
```bash
./gradlew test --tests "nestor.CPUTest"
```

To run a single test case:
```bash
./gradlew test --tests "nestor.CPUTest" -Dkotest.filter.tests="should set the interrupt disable flag"
```

## Architecture

The emulator has three core subsystems that interact via a clock-driven loop:

**`Emulation.kt`** — orchestrates the loop: 1 CPU cycle = 3 PPU cycles, ~29,780 CPU cycles per frame. Handles reset and NMI polling.

**`CPU.kt`** — 6502 processor. Fetch-decode-execute cycle with registers (A, X, Y, PC, SP), status flags, and ~40+ instructions. Reads/writes memory via `MemoryBus`.

**`PPU.kt`** — Picture Processing Unit. Generates 256×240 frames from CHR-ROM tile data, nametable RAM, and palette RAM. Triggers NMI on VBlank. Exposes PPU registers at `0x2000–0x2007`.

**`MemoryBus.kt`** — CPU address space:
- `0x0000–0x1FFF`: 2KB RAM (mirrored)
- `0x2000–0x3FFF`: PPU registers (mirrored every 8 bytes)
- `0x8000–0xFFFF`: PRG-ROM

**`RomReader.kt`** / **`INESRom.kt`** — Parse iNES format, extract PRG-ROM and CHR-ROM banks.

**`ScreenRenderer.kt`** — Swing `JPanel` that displays PPU frames at 3× scale (768×720).

## Git

Do not add Claude as a co-author in commit messages.

## Implementation Plans

Plans for larger changes live in `.claude/plans/` — check here for context on planned or in-progress work before starting an implementation.

## Reference Material

Saved reference docs live in `.claude/reference/` — read these when implementing or debugging CPU/PPU behaviour:

- `ref_6502_instructions.md` — all 56 instructions: opcodes, addressing modes, cycle counts, flags
- `ref_ppu_registers.md` — $2000–$2007 bit layouts, read/write behaviour, internal v/t/x/w registers
- `ref_ppu_scrolling.md` — scroll register internals, write sequences, per-scanline t→v copy rules
- `ref_cpu_memory_map.md` — full address space, mirroring, interrupt vectors, Mapper 0 specifics
- `ref_cycle_timing.md` — CPU/PPU cycles per scanline/frame, master clock, key timing landmarks

## Coding Style

- Idiomatic Kotlin: prefer `when` expressions over `if-else`, prefer expression bodies over block bodies
- Tests use **Kotest FreeSpec** layout with `forAll`/`row` for parameterized cases and **mockk** for mocking
- Reference: [NESdev wiki](https://www.nesdev.org/wiki/Nesdev_Wiki) is the authoritative source for 6502 and PPU behaviour

## Testing

Tests live in `src/test/kotlin/nestor/`. `CPUTest.kt` is the most extensive — uses parameterized `forAll`/`row` tables to test instruction behaviour across addressing modes and edge cases. `TestUtils.kt` provides shared setup helpers.

## Architecture

The emulator has three core subsystems that interact via a clock-driven loop:

**`Emulation.kt`** — orchestrates the loop: 1 CPU cycle = 3 PPU cycles, ~29,780 CPU cycles per frame. Handles reset and NMI polling.

**`CPU.kt`** — 6502 processor. Fetch-decode-execute cycle with registers (A, X, Y, PC, SP), status flags, and ~40+ instructions. Reads/writes memory via `MemoryBus`.

**`PPU.kt`** — Picture Processing Unit. Generates 256×240 frames from CHR-ROM tile data, nametable RAM, and palette RAM. Triggers NMI on VBlank. Exposes PPU registers at `0x2000–0x2007`.

**`MemoryBus.kt`** — CPU address space:
- `0x0000–0x1FFF`: 2KB RAM (mirrored)
- `0x2000–0x3FFF`: PPU registers (mirrored every 8 bytes)
- `0x8000–0xFFFF`: PRG-ROM

**`RomReader.kt`** / **`INESRom.kt`** — Parse iNES format, extract PRG-ROM and CHR-ROM banks.

**`ScreenRenderer.kt`** — Swing `JPanel` that displays PPU frames at 3× scale (768×720).

## Coding Style

Follow `PROJECT_CONTEXT.md`:
- Idiomatic Kotlin: prefer `when` expressions over `if-else`, prefer expression bodies over block bodies
- Tests use **Kotest FreeSpec** layout with `forAll`/`row` for parameterized cases and **mockk** for mocking

## Testing

Tests live in `src/test/kotlin/nestor/`. `CPUTest.kt` is the most extensive — uses parameterized `forAll`/`row` tables to test instruction behavior across addressing modes and edge cases. `TestUtils.kt` provides shared setup helpers.
