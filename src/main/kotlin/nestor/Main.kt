package nestor

import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    // val rom = loadRomFile("test-roms/instr_test-v3/all_instrs.nes")
    val rom = loadRomFile("goodnes/Europe/Super Mario Bros. (E) (V1.1) [!].nes")
    // val rom = loadRomFile("goodnes/Europe/Excitebike (E) [!].nes")
    val inesRom = RomReader.read(rom)

    val tiles = TileParser.parseTiles(inesRom.chrData)
    val ppu = PPU(tiles)
    val memoryBus = MemoryBus(ppu, inesRom.prgData)
    //fakePalette(memoryBus)
    //fakeNameTables(memoryBus)

    val screen = ScreenRenderer()
    displayScreen(screen)

    //ppu.renderFrame()
    //screen.draw(ppu.currentFrame())

    val cpu = CPU(memoryBus)
    val emulation = Emulation(cpu, ppu, memoryBus)
    emulation.runAFewTicks()

    ppu.renderFrame()
    screen.draw(ppu.currentFrame())
}

private fun displayScreen(screen: ScreenRenderer) {
    SwingUtilities.invokeLater {
        val frame = JFrame("Nestor NES Emulator")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane.add(screen)
        frame.pack()
        frame.isResizable = false
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

private fun fakePalette(memoryBus: MemoryBus) {
    // NES palette RAM is at $3F00–$3F1F
    setPpuAddr(memoryBus, 0x3F00)

    // Example: fill with a repeating pattern of color indices
    val values = byteArrayOf(
        0x0F,               // Universal background color
        0x21, 0x31, 0x30,   // Background palette 0: Sky + bricks
        0x1A, 0x2A, 0x27,   // Background palette 1: Bushes + clouds
        0x16, 0x27, 0x18,   // Background palette 2: Ground + shadows
        0x00, 0x10, 0x20,   // Background palette 3: Misc/unused
    )

    for (value in values) {
        memoryBus.write(0x2007, value.toInt())
    }
}

private fun fakeNameTables(memoryBus: MemoryBus) {
    // Write tile indices to nametable 0 ($2000–$23BF, 960 bytes)
    setPpuAddr(memoryBus, 0x2000)
    for (i in 0 until 960) {
        val value = if ((i / TILES_PER_ROW + i % TILES_PER_ROW) % 2 == 0) 0x32 else 0x33
        memoryBus.write(0x2007, value)
    }

    // Write attribute table to $23C0–$23FF (64 bytes)
    setPpuAddr(memoryBus, 0x23C0)
    for (i in 0 until 64) {
        val quadrant = i % 4
        val value = when (quadrant) {
            0 -> 0b00000000  // All palette 0
            1 -> 0b01010101  // All palette 1
            2 -> 0b10101010  // All palette 2
            else -> 0b11111111  // All palette 3
        }
        memoryBus.write(0x2007, value)
    }
}

// Helper function to write a 16-bit address to $2006 via MemoryBus
private fun setPpuAddr(memoryBus: MemoryBus, addr: Int) {
    memoryBus.write(0x2006, (addr shr 8) and 0xFF) // High byte
    memoryBus.write(0x2006, addr and 0xFF)        // Low byte
}
