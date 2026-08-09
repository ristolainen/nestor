---
name: NES CPU Memory Map
description: NES CPU address space layout with all regions, mirroring, and interrupt vectors
type: reference
---

# NES CPU Memory Map

Source: https://www.nesdev.org/wiki/CPU_memory_map

## Address Space Layout

| Address Range | Size | Device |
|---|---|---|
| $0000–$07FF | 2 KB | Internal RAM |
| $0800–$1FFF | 6 KB | Mirrors of $0000–$07FF (×3) |
| $2000–$2007 | 8 B | PPU registers |
| $2008–$3FFF | ~8 KB | Mirrors of $2000–$2007 (every 8 bytes) |
| $4000–$4017 | 24 B | APU and I/O registers |
| $4018–$401F | 8 B | Disabled APU/I/O (CPU test mode) |
| $4020–$5FFF | — | Cartridge expansion ROM |
| $6000–$7FFF | 8 KB | PRG-RAM / WRAM (battery-backed save) |
| $8000–$FFFF | 32 KB | PRG-ROM and mapper registers |

## Special RAM Regions

| Range | Purpose |
|-------|---------|
| $0000–$00FF | Zero page — faster addressing (2-byte instructions) |
| $0100–$01FF | Stack (SP counts down from $FF) |
| $0200–$02FF | Commonly used as OAM shadow buffer for OAMDMA |

## Interrupt Vectors (read-only, end of ROM)

| Address | Vector |
|---------|--------|
| $FFFA–$FFFB | NMI handler address |
| $FFFC–$FFFD | Reset handler address |
| $FFFE–$FFFF | IRQ/BRK handler address |

## Mapper 0 (NROM) Specifics

- **16 KB PRG-ROM:** Mapped at $8000–$BFFF and mirrored at $C000–$FFFF
- **32 KB PRG-ROM:** Mapped at $8000–$FFFF (no mirror)
- **No PRG-RAM** in most NROM cartridges

## Notes

- RAM is mirrored: writing to $0800 is the same as writing to $0000
- PPU registers are mirrored: $2008 = $2000, $2009 = $2001, etc.
- APU registers at $4000–$4017 include controller input ($4016, $4017)
- OAMDMA at $4014 (technically APU region, but triggers PPU DMA)
