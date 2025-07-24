# 🎮 Nestor

**Nestor** is a work-in-progress **NES emulator** written in **Kotlin** for the JVM.

It aims to bring the joy and challenge of retro gaming to life with clean, modern code — one scanline at a time.

---

## 🚀 Project Goals

- ✅ Learn systems programming by emulating 8-bit hardware
- ✅ Build an educational and approachable emulator
- ✅ Keep it fun, modular, and focused
- ✅ Eventually play real NES games — with sound, input, and sprites

---

## 🔧 Tech Stack

| Area        | Tool/Library                        |
|-------------|-------------------------------------|
| Language    | Kotlin (JVM)                        |
| Graphics    | Swing + BufferedImage               |
| Audio       | `javax.sound.sampled`               |
| Input       | [JInput](https://github.com/jinput/jinput) |
| Build Tool  | Gradle (Kotlin DSL)                 |

---

## 🧱 Roadmap

The emulator is being built in the following stages:

1. **First Frame**  
   Parse ROMs and render a static background using the PPU.

2. **Intro Playback**  
   Emulate CPU and PPU enough to animate the title screen.

3. **Sprite Rendering**  
   Add support for characters, enemies, and dynamic objects.

4. **Input Handling**  
   Connect a USB gamepad via JInput to control games.

5. **Audio Output**  
   Simulate the APU and stream sound through JVM audio.

---

## 🎮 Features (Eventually!)

- [x] iNES ROM loading
- [ ] 6502 CPU emulation (partial)
- [x] PPU background rendering
- [ ] OAM sprite support
- [ ] Controller input via USB gamepad
- [ ] Audio playback via APU
- [ ] Debugging tools (step mode, breakpoints)
- [ ] Save states and rewind

---

## 📦 Build Instructions

> Prerequisites: JDK 17+, Gradle, a ROM file (`.nes`)

```bash
git clone https://github.com/ristolainen/nestor.git
cd nestor
./gradlew run
