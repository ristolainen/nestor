# ✅ Nestor Emulator Development TODO

A detailed, trackable checklist for building the Nestor NES emulator in Kotlin.

---

## 📦 Phase 1: Output the First Frame

- [x] Parse iNES ROM headers
- [x] Load PRG-ROM into memory
- [x] Load CHR-ROM into memory (or allocate CHR-RAM if missing)
- [ ] Implement memory mapping (RAM mirroring, PRG-ROM banks)
- [ ] Implement PPU nametable layout
- [ ] Decode CHR tiles into pixel arrays
- [ ] Render background using nametable + tile data
- [ ] Display frame using `BufferedImage` and Swing
- [ ] Build first `PPU` class and tile renderer

---

## 🧠 Phase 2: Run Game Intro (CPU + PPU)

- [ ] Implement CPU instruction fetch/decode loop
- [ ] Support basic 6502 opcodes:
    - [ ] NOP, LDA, STA, JMP, JSR, RTS
    - [ ] INC/DEC, Branches, Arithmetic ops
    - [ ] Full addressing modes
- [ ] Hook up memory bus (read/write logic)
- [ ] Implement I/O register stubs (PPU, input)
- [ ] Tie CPU and PPU timing (3 PPU cycles per CPU cycle)
- [ ] Implement PPU scroll handling and VBlank
- [ ] Trigger NMI interrupt on VBlank
- [ ] Frame timing: simulate 60 FPS

---

## 🎮 Phase 3: Sprite Rendering

- [ ] Add OAM (Object Attribute Memory) support
- [ ] Emulate `$4014` DMA transfer from CPU RAM to OAM
- [ ] Parse sprite data (Y, tile ID, attributes, X)
- [ ] Render sprite tiles over background
- [ ] Add support for:
    - [ ] Horizontal/vertical flipping
    - [ ] Palette selection
    - [ ] Priority bit (optional)
    - [ ] 8x16 mode (optional)
- [ ] Composite sprites and background into final framebuffer

---

## 🕹️ Phase 4: Input Handling

- [ ] Implement `$4016`/`$4017` controller registers
- [ ] Build `ControllerState` data class
- [ ] Integrate JInput for USB gamepad polling
- [ ] Map physical gamepad buttons to NES input
- [ ] Write polled state to controller registers
- [ ] Allow pressing "Start" to enter gameplay

---

## 🔊 Phase 5: Audio Output

- [ ] Create APU stub class and frame sequencer
- [ ] Implement:
    - [ ] Pulse channel 1
    - [ ] Pulse channel 2
    - [ ] Triangle channel (optional at first)
    - [ ] Noise channel (optional)
- [ ] Generate raw PCM samples (44100 Hz)
- [ ] Mix channels into an output buffer
- [ ] Output sound using `javax.sound.sampled.SourceDataLine`
- [ ] Tune buffer size for smooth playback

---

## 🧪 Testing & Debugging

- [ ] Support loading test ROMs (e.g. `NESTest.nes`)
- [ ] Add CPU logging / trace output
- [ ] Frame stepping mode for debugging
- [ ] Add simple hex memory viewer
- [ ] Log input events to console

---

## 🎁 Optional Features / Future Work

- [ ] GUI ROM loader
- [ ] Save states (RAM + CPU + PPU snapshot)
- [ ] Rewind / replay system
- [ ] NES palette viewer
- [ ] Sprite viewer / debugger
- [ ] Full scanline-accurate PPU emulation
- [ ] Mapper support (start with NROM, then MMC1)

---

_Track progress, stay motivated, and enjoy the journey!_
