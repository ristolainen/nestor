# Sprite 0 Hit & OAM DMA

## Problem

The CPU is stuck in an infinite loop at `$8150` (visible from trace after ~CYC:953874):

```
8150  AD 02 20  LDA $2002     ; read PPUSTATUS
8153  29 40     AND #$40      ; check bit 6 (sprite 0 hit)
8155  F0 F9     BEQ $8150     ; loop until hit
```

SMB uses sprite 0 hit as a scanline timer to split the screen between the status bar and the play area. It never exits because bit 6 of `$2002` is never set.

Two root causes:

---

## Root Cause 1: OAM DMA ($4014) is ignored

In `MemoryBus.write()`, writes to `$4014` fall through to the `else` branch and are silently discarded. OAM RAM stays all-zeros.

The game writes:
```
80AE  8D 03 20  STA $2003   ; A=00 → set OAMADDR to 0
80B1  A9 02     LDA #$02
80B3  8D 14 40  STA $4014   ; A=02 → DMA copy $0200–$02FF → OAM
```

### Fix

Add a case for `0x4014` in `MemoryBus.write()` before the `in 0x8000..0xFFFF` branch:

```kotlin
0x4014 -> {
    // OAM DMA: copy 256 bytes from CPU page $XX00–$XXFF to OAM RAM
    val srcPage = value shl 8
    for (i in 0 until 256) {
        ppu.oamRam[i] = read(srcPage + i).toByte()
    }
}
```

---

## Root Cause 2: Sprite 0 hit is never detected

`PPU.tick()` never sets bit 6 of `status`. There is no sprite rendering and no overlap detection.

### What sprite 0 hit is

- OAM bytes 0–3 = sprite 0: `[Y, tileId, attributes, X]`
- Sprite is rendered at scanlines `Y+1` through `Y+8`
- Hit fires when any non-transparent sprite 0 pixel (color index ≠ 0) overlaps a non-transparent background pixel (color index ≠ 0) on the same screen position
- Hit flag (bit 6) is set and stays set until pre-render scanline 261 clears it
- Reading `$2002` does **not** clear the hit flag (only VBlank bit 7 is cleared by reads)

### Fix

Add a constant:
```kotlin
const val STATUS_SPRITE0_HIT = 0b01000000
```

Clear it on pre-render in `tick()`:
```kotlin
261 -> {
    clearStatusFlag(STATUS_VBLANK)
    clearStatusFlag(STATUS_SPRITE0_HIT)
    writeToggle = false
    nmiOccurred = false
}
```

Add hit detection per PPU cycle in `tick()`:
```kotlin
private fun checkSprite0Hit() {
    if ((status and STATUS_SPRITE0_HIT) != 0) return  // already set this frame
    if (scanline !in 1..239) return
    val spriteEnabled = (mask and 0x10) != 0
    val bgEnabled = (mask and 0x08) != 0
    if (!spriteEnabled || !bgEnabled) return

    val sprite0Y = oamRam[0].toUByte().toInt()
    val sprite0X = oamRam[3].toUByte().toInt()
    if (sprite0X == 255) return  // x=255 never triggers hit

    if (scanline == sprite0Y + 1 && cycle == sprite0X + 2) {
        setStatusFlag(STATUS_SPRITE0_HIT)
    }
}
```

Call it from within the `repeat(cycles)` loop in `tick()`, after the cycle/scanline update.

### Notes on accuracy

- This implementation is not pixel-accurate (it doesn't check whether the sprite tile and background tile actually have overlapping non-transparent pixels at that position)
- It will be sufficient to unblock SMB — sprite 0 in SMB has visible pixels, and the background at that position has visible pixels
- A more accurate future implementation would check tile pixel data for both sprite 0 and the background tile at every candidate pixel

---

## Expected outcome

After both fixes, the game exits the spin loop on the correct scanline (~32 in SMB), reprograms scroll registers, and game rendering progresses beyond the status bar split.
