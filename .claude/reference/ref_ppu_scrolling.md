---
name: PPU Scrolling Reference
description: NES PPU scrolling internals — v/t/x/w registers, write sequences, and per-scanline update behavior
type: reference
---

# NES PPU Scrolling Reference

Source: https://www.nesdev.org/wiki/PPU_scrolling

## Internal Registers

| Register | Size | Purpose |
|----------|------|---------|
| **v** | 15 bits | Current VRAM address; also encodes scroll position during rendering |
| **t** | 15 bits | Temporary address; top-left onscreen tile position |
| **x** | 3 bits | Fine X scroll (0–7 pixels within the current tile) |
| **w** | 1 bit | Write toggle: 0 = first write, 1 = second write |

### Bit Layout of v and t

```
yyy NN YYYYY XXXXX
||| || ||||| +++++ coarse X scroll (tile column, 0–31)
||| || +++++ coarse Y scroll (tile row, 0–29)
||| ++ nametable select (bits 10–11)
+++ fine Y scroll (pixel row within tile, 0–7, bits 12–14)
```

---

## Register Write Effects

### $2005 PPUSCROLL — First write (w=0)

Writes X scroll:
- Bits 7–3 of value → t bits 4–0 (coarse X)
- Bits 2–0 of value → x (fine X)
- Sets w = 1

### $2005 PPUSCROLL — Second write (w=1)

Writes Y scroll:
- Bits 7–3 of value → t bits 9–5 (coarse Y)
- Bits 2–0 of value → t bits 14–12 (fine Y)
- Sets w = 0

### $2006 PPUADDR — First write (w=0)

Writes VRAM address high byte:
- Bits 5–0 of value → t bits 13–8
- t bit 14 cleared
- Sets w = 1

### $2006 PPUADDR — Second write (w=1)

Writes VRAM address low byte:
- All 8 bits → t bits 7–0
- **t copied entirely to v** (takes effect immediately)
- Sets w = 0

---

## Per-Scanline Rendering Updates

### Dot 256 of each scanline

PPU increments fine Y in v. If fine Y wraps (was 7), increments coarse Y. If coarse Y reaches 30, wraps to 0 and toggles nametable Y bit (bit 11).

### Dot 257 of each scanline

PPU copies horizontal position from t to v:
- t bits 4–0 (coarse X) → v bits 4–0
- t bit 10 (nametable X) → v bit 10

### Dots 280–304 (pre-render scanline only)

PPU repeatedly copies vertical bits from t to v:
- t bits 14–11 and 9–5 → v bits 14–11 and 9–5
(This is how the Y scroll set during vblank takes effect)

### Dots 328–256 (across scanline, every 8 dots)

PPU increments coarse X in v, toggling nametable X bit on wrap.

---

## Basic Usage Pattern

To set scroll position for a frame (during vblank):

1. Read PPUSTATUS to reset w latch
2. Write X scroll low 8 bits to $2005
3. Write Y scroll low 8 bits to $2005
4. Write nametable + high scroll bits to PPUCTRL ($2000) bits 0–1

Must complete before end of vblank (before dot 0 of scanline 0).

---

## Wrapping Rules

**Coarse X:** When bits 0–4 reach 31 and increment, they wrap to 0 and bit 10 (nametable X) toggles → seamless horizontal scroll across two nametables.

**Coarse Y:** Fine Y increments first (0→7). At 7+1, fine Y resets to 0 and coarse Y increments. At coarse Y = 30 (last row), it resets to 0 and bit 11 (nametable Y) toggles.

> Note: Coarse Y can be forced to values 30–31 via $2006 writes. The PPU does not wrap at row 30 in that case, causing it to read attribute data instead of nametable tiles.

---

## Mid-Frame Tricks

**Split X scroll:** Write to $2005 after dot 257 to change X scroll for subsequent scanlines. Fine X updates immediately; coarse X updates at end of scanline.

**Full mid-frame scroll reset:** Four writes during HBlank (dot 256+):
1. Nametable selector → $2006
2. Y position → $2005
3. X position → $2005
4. Low address byte → $2006

Last two writes should occur at cycle 256 or later.
