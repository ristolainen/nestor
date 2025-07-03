# 🧠 Nestor Project Context

**Nestor** is a NES emulator written in **Kotlin** targeting the **JVM**. It aims to emulate the NES system accurately enough to load and partially play iNES ROMs.

## Goals

- Build an educational, modular emulator from scratch
- Focus on understandability and clean design
- Use JVM-native tools and APIs

## Technologies

| Subsystem | Tool/Library                     |
|----------|----------------------------------|
| Graphics | `Swing` + `BufferedImage`        |
| Audio    | `javax.sound.sampled`            |
| Input    | `JInput` for USB gamepad support |
| Build    | Kotlin + Gradle                  |

## Emulator Phases

1. **Background Rendering** – Static frame from CHR + nametable
2. **Intro Playback** – CPU + PPU simulate title screen
3. **Sprite Rendering** – Characters, enemies, HUD
4. **Input** – USB gamepad input via JInput
5. **Audio** – APU + PCM output

## Current Status

- Project is in **Phase 1**
- Focus is on CHR decoding and tile-based background rendering
- Core classes scaffolded: `RomLoader`, `Cpu`, `Ppu`, `ScreenRenderer`
