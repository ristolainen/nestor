---
name: PPU Registers Reference
description: NES PPU registers $2000-$2007 with bit layouts, read/write behavior, and internal registers
type: reference
---

# NES PPU Registers Reference

Source: https://www.nesdev.org/wiki/PPU_registers

The PPU exposes eight memory-mapped registers at CPU addresses **$2000–$2007**, mirrored every 8 bytes through $3FFF. After power-on/reset, writes to most registers are ignored until the pre-render scanline of the next frame.

## Register Summary

| Register | Address | Bits | R/W | Function |
|----------|---------|------|-----|----------|
| PPUCTRL | $2000 | VPHB SINN | W | NMI enable, master/slave, sprite height, tile select, increment mode, nametable select |
| PPUMASK | $2001 | BGRs bMmG | W | Color emphasis, sprite/background rendering, left column visibility, greyscale |
| PPUSTATUS | $2002 | VSO- ---- | R | Vblank flag, sprite 0 hit, sprite overflow |
| OAMADDR | $2003 | AAAA AAAA | W | OAM read/write address |
| OAMDATA | $2004 | DDDD DDDD | RW | OAM data (increments address on write) |
| PPUSCROLL | $2005 | XXXX XXXX | Wx2 | X and Y scroll (two writes) |
| PPUADDR | $2006 | ..AA AAAA | Wx2 | VRAM address (two writes: high then low) |
| PPUDATA | $2007 | DDDD DDDD | RW | VRAM data (auto-increments address) |
| OAMDMA | $4014 | AAAA AAAA | W | OAM DMA (high byte of source CPU address) |

---

## PPUCTRL ($2000) — Write only

```
7  bit  0
VPHB SINN
```

| Bit | Name | Description |
|-----|------|-------------|
| 0–1 | NN | Nametable select: 0=$2000, 1=$2400, 2=$2800, 3=$2C00; also scroll bit 8 for X (bit 0) and Y (bit 1) |
| 2 | I | VRAM increment: 0=+1 (across), 1=+32 (down) |
| 3 | S | Sprite pattern table: 0=$0000, 1=$1000 (ignored in 8×16 mode) |
| 4 | B | Background pattern table: 0=$0000, 1=$1000 |
| 5 | H | Sprite size: 0=8×8, 1=8×16 |
| 6 | P | PPU master/slave (avoid on stock NES) |
| 7 | V | NMI enable: 0=off, 1=generate NMI on vblank |

**Key:** Enabling NMI while vblank flag is already set immediately triggers an NMI.

---

## PPUMASK ($2001) — Write only

```
7  bit  0
BGRs bMmG
```

| Bit | Name | Description |
|-----|------|-------------|
| 0 | G | Greyscale: AND palette with $30 |
| 1 | m | Show background in leftmost 8 pixels |
| 2 | M | Show sprites in leftmost 8 pixels |
| 3 | b | Enable background rendering |
| 4 | s | Enable sprite rendering |
| 5–7 | BGR | Color emphasis (B=blue, G=green, R=red) |

Most games: $00 during data transfer, $1E during gameplay.

---

## PPUSTATUS ($2002) — Read only

```
7  bit  0
VSO- ----
```

| Bit | Name | Description |
|-----|------|-------------|
| 7 | V | Vblank flag: set at scanline 241 dot 1; **cleared by read** or at prerender dot 1 |
| 6 | S | Sprite 0 hit |
| 5 | O | Sprite overflow (buggy detection) |
| 4–0 | — | Open bus |

**Critical:** Reading PPUSTATUS clears the internal write latch (w register). Use NMI rather than polling the vblank flag.

---

## OAMADDR ($2003) — Write only

Sets OAM address for OAMDATA reads/writes. Most games write $00 and use OAMDMA.

---

## OAMDATA ($2004) — Read/Write

Reads/writes OAM. Writes increment OAMADDR. **Do not write during rendering** — use OAMDMA instead.

---

## PPUSCROLL ($2005) — Write twice

- **1st write (w=0):** X scroll position (pixel column of leftmost visible pixel)
- **2nd write (w=1):** Y scroll position (0–239; values 240–255 cause attribute corruption)

Reset latch by reading PPUSTATUS before writing.

---

## PPUADDR ($2006) — Write twice

- **1st write (w=0):** High byte of VRAM address (bit 14 forced to 0)
- **2nd write (w=1):** Low byte; full address immediately copied to v register

Accesses 14-bit PPU address space ($0000–$3FFF).

**Palette corruption:** After writing palette data, set address to $3F00 before switching to non-palette addresses.

---

## PPUDATA ($2007) — Read/Write

Reads/writes VRAM at current v address, then auto-increments by 1 or 32 per PPUCTRL bit 2.

**Read buffer:** Reading returns the previous buffer contents, not immediate data. Do a dummy read after setting PPUADDR.
**Palette exception:** Reading palette addresses returns data immediately (most revisions).

---

## OAMDMA ($4014) — Write only

Write high byte of CPU source address. Copies 256 bytes to OAM. Takes 513–514 CPU cycles. Write $00 to OAMADDR first. Use only during vblank.

---

## Internal Registers

| Register | Size | Description |
|----------|------|-------------|
| v | 15 bits | Current VRAM address / scroll position during rendering |
| t | 15 bits | Temporary address / top-left onscreen tile position |
| x | 3 bits | Fine X scroll (0–7 pixels within tile) |
| w | 1 bit | Write latch: 0=first write, 1=second write; cleared by PPUSTATUS read |

Both v and t layout:
```
yyy NN YYYYY XXXXX
||| || ||||| +++++ coarse X (tile column 0–31)
||| || +++++ coarse Y (tile row 0–29)
||| ++ nametable select
+++ fine Y (pixel row within tile 0–7)
```
