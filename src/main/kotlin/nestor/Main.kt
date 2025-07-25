package nestor

import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    // val rom = loadRomFile("test-roms/instr_test-v3/all_instrs.nes")
    val rom = loadRomFile("goodnes/Europe/Super Mario Bros. (E) (V1.1) [!].nes")
    // val rom = loadRomFile("goodnes/Europe/Legend of Zelda, The (E) (V1.1) [!].nes")
    val inesRom = RomReader.read(rom)
    println(inesRom.header)

    val nametableRam = ByteArray(NAMETABLE_RAM_SIZE)
    val paletteRam = ByteArray(PALETTE_RAM_SIZE)
    initTestPalette(paletteRam)
    fakeNameTables(nametableRam)
    val tiles = TileParser.parseTiles(inesRom.chrData)
    val ppu = PPU(tiles, nametableRam, paletteRam)

    val screen = ScreenRenderer()
    SwingUtilities.invokeLater {
        val frame = JFrame("Nestor NES Emulator")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane.add(screen)
        frame.pack()
        frame.isResizable = false
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    ppu.renderFrame()
    screen.draw(ppu.currentFrame())

    // println("Nestor NES Emulator starting...")
    // Placeholder main loop
}

private fun initTestPalette(paletteRam: ByteArray) {
    // Each 4-byte block is a palette (background or sprite)
    val testPalette = byteArrayOf(
        0x0F, 0x21, 0x31, 0x30,  // Background palette 0: Sky + bricks
        0x0F, 0x1A, 0x2A, 0x27,  // Background palette 1: Bushes + clouds
        0x0F, 0x16, 0x27, 0x18,  // Background palette 2: Ground + shadows
        0x0F, 0x00, 0x10, 0x20   // Background palette 3: Misc/unused
    )

    for (i in testPalette.indices) {
        paletteRam[i] = testPalette[i]
    }
}

private fun fakeNameTables(nametableRam: ByteArray) {
    // Fill tile indices for nametable 0
    val nametable = ByteArray(960) { i ->
        if ((i / TILES_PER_ROW + i % TILES_PER_ROW) % 2 == 0) 0x32 else 0x33
    }
    nametable.copyInto(nametableRam)

    // Fill attribute table for nametable 0, offset 0x03C0–0x03FF
    for (i in 0x03C0 until 0x0400) {
        val quadrant = (i - 0x03C0) % 4
        val value = when (quadrant) {
            0 -> 0b00000000  // All palette 0
            1 -> 0b01010101  // All palette 1
            2 -> 0b10101010  // All palette 2
            else -> 0b11111111  // All palette 3
        }.toByte()
        nametableRam[i] = value
    }
}
