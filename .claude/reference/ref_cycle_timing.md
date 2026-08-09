---
name: NES Cycle Timing Reference
description: CPU/PPU cycle counts per scanline, per frame, master clock speeds, and key timing landmarks
type: reference
---

# NES Cycle Timing Reference

Source: https://www.nesdev.org/wiki/Cycle_reference_chart

## Per-Region Timing

| Region | CPU cycles/scanline | Scanlines/frame | CPU cycles/frame | PPU dots/CPU cycle |
|--------|--------------------|-----------------|-----------------|--------------------|
| NTSC | 113⅔ | 262 | 29,780.5* | 3 |
| PAL | 106⁹⁄₁₆ | 312 | 33,247.5 | 3.2 |
| Dendy | 113⅔ | 312 | 35,464 | 3 |

*NTSC: 29,780.5 cycles if rendering enabled during scanline 20; 29,780⅔ otherwise.

## Master Clock Speeds

| Region | Frequency |
|--------|-----------|
| NTSC | 21.477272 MHz |
| PAL | 26.601712 MHz |

CPU clock = master ÷ 12 (NTSC: ~1.789773 MHz)
PPU clock = master ÷ 4 (NTSC: ~5.369318 MHz)

## Key Timing Landmarks (NTSC)

| Event | Timing |
|-------|--------|
| Visible scanlines | 0–239 |
| Post-render (idle) | Scanline 240 |
| VBlank start / NMI | Scanline 241, dot 1 |
| Pre-render scanline | Scanline 261 (−1) |
| VBlank end | Scanline 261, dot 1 (PPUSTATUS V cleared) |
| Vertical copy (t→v) | Scanline 261, dots 280–304 |
| PPU dots per scanline | 341 |

## Other Key Timings

| Event | Cycles |
|-------|--------|
| OAM DMA | 513 (+1 if starting on odd CPU cycle) |
| NMI to rendering start (NTSC) | ~2,273⅓ CPU cycles |
| HBlank duration (NTSC) | 28⅓ CPU cycles |

## Emulation Notes

- 1 CPU cycle = 3 PPU cycles (NTSC)
- ~29,780 CPU cycles per frame is the standard emulation target
- NMI fires at scanline 241, dot 1 — games use this window (~2273 cycles) for vblank work
- Pre-render scanline (261) reloads scroll Y from t to v during dots 280–304
