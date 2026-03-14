# ✅ Nestor Emulator Development TODO

A detailed, trackable checklist for building the Nestor NES emulator in Kotlin.

---

## 📦 Phase 1: Output a static Frame

- [x] Parse iNES ROM headers
- [x] Load PRG-ROM into memory
- [x] Load CHR-ROM into memory (or allocate CHR-RAM if missing)
- [x] Implement memory mapping (RAM mirroring, PRG-ROM banks)
- [x] Implement PPU nametable layout
- [x] Decode CHR tiles into pixel arrays
- [x] Render background using nametable + tile data
- [x] Display frame using `BufferedImage` and Swing
- [x] Build first `PPU` class and tile renderer

---

## 🧠 Phase 2: Run Game Intro (CPU + PPU)

- [x] Implement CPU instruction fetch/decode loop
- [x] Support basic 6502 opcodes:
    - [x] NOP, LDA, STA, JMP, JSR, RTS
    - [x] INC/DEC (INC, DEC, INX, INY, DEX, DEY)
    - [x] All branch instructions (BEQ, BNE, BCS, BCC, BVS, BVC, BMI, BPL)
    - [x] All bitwise OR / AND / BIT instructions
    - [x] Transfer instructions (TAX, TAY, TXA, TYA, TXS, TSX)
    - [x] Compare instructions (CMP, CPX, CPY)
    - [x] Stack instructions (PHA, PLA)
    - [x] LSR accumulator
    - [ ] Arithmetic ops (ADC, SBC)
    - [ ] Shift/rotate (ROL, ROR, ASL, LSR memory modes)
    - [ ] Remaining addressing mode gaps
- [x] Hook up memory bus (read/write logic)
- [ ] Implement I/O register stubs (PPU, input)
- [x] Tie CPU and PPU timing (3 PPU cycles per CPU cycle)
- [x] Trigger NMI interrupt on VBlank
- [ ] Implement PPU scroll handling and VBlank timing
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

## 🏗️ Architecture / Tech Debt

- [ ] Scanline-accurate PPU rendering — replace batch `renderFrame()` with per-scanline rendering driven by `tick()`, so mid-frame scroll writes (e.g. SMB HUD split) work correctly
- [ ] Log unknown opcodes — `else` branch in CPU decode should throw/log instead of silently corrupting state
- [ ] Give PPU its own address bus — replace pre-parsed tile list with a PPU bus ($0000–$3FFF); required for CHR-RAM and any mapper beyond NROM
- [ ] Replace reflection in `Trace.kt` — use `CpuState`/`PpuState` data class snapshots instead of Java reflection, so tracing doesn't break silently on renames
- [ ] Implement IRQ — wire IRQ into CPU interrupt poll alongside NMI; needed for DMC audio, MMC3 timing, and `BRK` (requires `PHP`/`PLP`/`RTI` first)

---

_Track progress, stay motivated, and enjoy the journey!_
