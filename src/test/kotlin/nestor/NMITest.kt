package nestor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class NMITest : FreeSpec({

    "invokes NMI routine on VBlank when NMI-on-VBlank is enabled" {
        // --- Build a minimal PRG-ROM (16KB NROM-128) ------------------------------------------
        // Layout:
        // $8000:  LDA #$80       ; enable NMI on VBlank (PPUCTRL bit7)
        // $8002:  STA $2000
        // $8005:  JMP $8000      ; spin forever; PPU will advance, then NMI fires
        //
        // $9000:  LDA #$99       ; NMI handler writes sentinel to $0002
        // $9002:  STA $0002
        // $9004:  JMP $9000      ; loop so we don't need RTI implemented
        //
        // Vectors:
        // $FFFA/$FFFB -> $9000 (NMI)
        // $FFFC/$FFFD -> $8000 (RESET)

        val prg = ByteArray(0x4000) { 0xEA.toByte() } // fill with NOP

        // Reset routine at $8000 (ROM index 0x0000)
        var i = 0x0000
        prg[i++] = 0xA9.toByte(); prg[i++] = 0x80.toByte()          // LDA #$80
        prg[i++] = 0x8D.toByte(); prg[i++] = 0x00.toByte(); prg[i++] = 0x20.toByte() // STA $2000
        prg[i++] = 0x4C.toByte(); prg[i++] = 0x00.toByte(); prg[i++] = 0x80.toByte() // JMP $8000

        // NMI routine at $9000 (ROM index 0x1000)
        i = 0x1000
        prg[i++] = 0xA9.toByte(); prg[i++] = 0x99.toByte()          // LDA #$99
        prg[i++] = 0x85.toByte(); prg[i++] = 0x02.toByte()          // STA $0002
        prg[i++] = 0x4C.toByte(); prg[i++] = 0x00.toByte(); prg[i++] = 0x90.toByte() // JMP $9000

        // Vectors live at the top of the CPU address space; for a 16KB PRG they mirror into
        // ROM indexes 0x3FFA..0x3FFF (see MemoryBus mapping)
        prg[0x3FFA] = 0x00; prg[0x3FFB] = 0x90.toByte() // NMI vector -> $9000
        prg[0x3FFC] = 0x00; prg[0x3FFD] = 0x80.toByte() // RESET vector -> $8000

        // --- Minimal PPU tiles (we never render, but constructor needs them) -------------------
        // 256 blank tiles is fine
        fun blankTile() = Array(8) { IntArray(8) }
        val tiles: List<Array<IntArray>> = List(256) { blankTile() }
        val ppu = PPU(tiles)

        // --- CPU + MemoryBus -------------------------------------------------------------------
        val mem = MemoryBus(ppu, prg)
        val cpu = CPU(mem)
        cpu.reset()

        // --- Emulation wrapper -----------------------------------------------------------------
        // Emulation.step() executes exactly 1 CPU instruction, advances the PPU
        // by cycles*3, and polls/dispatches NMI between instructions.
        val emu = Emulation(cpu, ppu, mem)

        // --- Run until the NMI handler stores the sentinel to $0002 ---------------------------
        // Hitting VBlank: 241 scanlines * 341 PPU dots ≈ 82,281 PPU cycles.
        // Our loop at $8000 is 3 instructions (2+4+3 = 9 CPU cycles → 27 PPU cycles each trip).
        // A generous guard keeps the test robust even if timings differ slightly.
        val SENTINEL_ADDR = 0x0002
        val SENTINEL_VALUE = 0x99
        var guard = 20_000 // ~enough single-instruction steps to reach first VBlank

        while (mem.read(SENTINEL_ADDR) != SENTINEL_VALUE && guard-- > 0) {
            emu.step() // ADJUST HERE if your stepping method is named differently
        }

        // The NMI handler should have run and stored the sentinel byte.
        mem.read(SENTINEL_ADDR) shouldBe SENTINEL_VALUE

        // (Optional) sanity: after NMI, PC should be executing in the $9000 page (our handler)
        // Depending on where your loop lands, PC could be $9000, $9002, or $9004.
        cpu.pc ushr 8 shouldBe 0x90
    }
})
