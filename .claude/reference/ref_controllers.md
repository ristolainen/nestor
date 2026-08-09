---
name: Controller Reading Reference
description: NES controller ports $4016/$4017 — strobe/latch sequence, shift register behavior, button order, open bus
type: reference
---

# NES Controller Reading Reference

Sources:
- https://www.nesdev.org/wiki/Controller_reading
- https://www.nesdev.org/wiki/Standard_controller
- https://www.nesdev.org/wiki/Controller_port_registers

Controllers are not memory. They are **8-bit parallel-to-serial shift registers** (a 4021 chip inside the controller) that the CPU clocks one bit at a time through two registers in the APU/IO address range.

| Address | Write | Read |
|---------|-------|------|
| $4016 | Controller strobe (bit 0) + Famicom expansion latch (bits 1–2) | Controller port 1 serial data |
| $4017 | **APU frame counter** (not controllers) | Controller port 2 serial data |

The asymmetry matters: **one write to $4016 strobes both controllers**, but each port is read from its own address. Writing $4017 does *not* touch the controllers — it configures the APU frame counter, so an emulator must not route $4017 writes to controller 2.

---

## Register layouts

### $4016 write

```
7  bit  0
---- ----
xxxx xEES
      |||
      ||+- Controller port latch (strobe)
      ++-- Expansion port latch bits (Famicom)
```

Only the low 3 bits are latched. For a bare NES only bit 0 matters — the rest are ignored.

### $4016 / $4017 read

```
7  bit  0
---- ----
xxxD DDDD
|||+-++++- Input data lines D4 D3 D2 D1 D0
+++------- Open bus
```

| Bit | Device |
|-----|--------|
| D0 | Standard controller / Famicom controller |
| D1 | Famicom expansion port controller |
| D2 | Famicom microphone (port 2 only) |
| D3 | Zapper light sense |
| D4 | Zapper trigger |

A standard controller only drives **D0**. D1–D4 read 0 with nothing attached.

---

## The strobe / latch sequence

```
LDA #$01
STA $4016     ; strobe high — shift registers continuously reload from the buttons
LDA #$00
STA $4016     ; strobe low — reloading stops, serial shift-out begins
```

- **Strobe high (bit 0 = 1)**: the shift register is *continuously* reloaded from whatever buttons are held right now. Reads during this window keep returning the A button state, live. This is a level, not an edge — it stays reloading until strobe goes low.
- **Strobe low (bit 0 = 0)**: reloading stops. The latched snapshot shifts out one bit per read.

So button state is sampled at the **1→0 transition**, and everything read afterwards reflects that instant, not the present.

## The read sequence

Each read of $4016/$4017 clocks the shift register: it returns the current bit on D0, then shifts.

Report order for the standard controller — **1 = pressed, 0 = not pressed**:

| Read # | Button |
|--------|--------|
| 0 | A |
| 1 | B |
| 2 | Select |
| 3 | Start |
| 4 | Up |
| 5 | Down |
| 6 | Left |
| 7 | Right |

The hardware signal is inverted (an unpressed button is high on the wire, and the NES inverts it), so from the CPU's point of view pressed simply reads as 1.

### After the 8th read

On a stock NES, **all subsequent reads return 1** — a trailing 1 shifts in behind the button data. (Third-party Famicom controllers may report other values.) Emulating this as "shift in 1s" is both simplest and correct.

### Typical polling loop

```
    LDA #$01
    STA $4016
    LDA #$00
    STA $4016
    LDX #$08
loop:
    LDA $4016     ; button bit in D0
    LSR A         ; D0 → carry
    ROL buttons   ; carry → buttons, shifting left
    DEX
    BNE loop
```

This ends with `buttons` holding `A B Select Start Up Down Left Right` from bit 7 down to bit 0 — note the bit order in the packed byte is the *reverse* of the shift-out order.

---

## Open bus (bits 5–7)

The upper three bits are **not driven** by the controller port, so they retain the last value on the CPU data bus. Since the read instruction's operand high byte was `$40`, reads from $4016/$4017 return **$40 or $41** on real hardware.

Some games — **Paperboy** is the classic example — test the whole byte and require exactly $40/$41. Returning a bare 0/1 breaks them. Most games mask with `AND #$01` or use `LSR` and don't care.

An emulator can return `0x40 or bit` as a cheap approximation without modelling a real open-bus latch.

---

## The DPCM conflict

If the APU's DPCM sample playback is running, its DMA can steal a cycle mid-read and cause the controller read to be **clocked twice**, deleting one bit of the report. The classic symptom is **spurious Right presses**, because the lost bit lets a trailing 1 slide into the Right position.

Games work around this by polling in a loop until two consecutive reads agree. This only matters once DPCM/DMC is emulated; it is not a controller bug to reproduce.

---

## Emulation notes

- Model each controller as: a live `buttons` byte (set by the host keyboard), a `strobe` flag, and an 8-bit `shift` register.
- On write to $4016: `strobe = value and 1 != 0`; if strobe is high, reload `shift` from `buttons`.
- On read: if strobe is high, reload first and return bit 0 of `buttons` (live A button). Otherwise return `shift and 1`, then `shift = (shift ushr 1) or 0x80` so trailing 1s shift in.
- Reads are **destructive**. Any non-side-effecting `peek` path (e.g. the tracer) must not shift the register.
- Keyboard input arrives on the Swing EDT while the emulator thread reads — the `buttons` field needs to be volatile or otherwise safely published.
- Up+Down and Left+Right simultaneously are physically impossible on a real D-pad and some games glitch on them; filtering them out is optional.
