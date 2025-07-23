package nestor

import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    // val rom = loadRomFile("test-roms/instr_test-v3/all_instrs.nes")
    val rom = loadRomFile("goodnes/Europe/Super Mario Bros. (E) (V1.1) [!].nes")
    // val rom = loadRomFile("goodnes/Europe/Legend of Zelda, The (E) (V1.1) [!].nes")
    val inesRom = RomReader.read(rom)
    println(inesRom.header)

    val vram = ByteArray(VRAM_SIZE)
    initTestPalette(vram)
    fakeNameTables(vram)
    val ppu = PPU(inesRom, vram)

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

private fun initTestPalette(vram: ByteArray) {
    // Each 4-byte block is a palette (background or sprite)
    val testPalette = byteArrayOf(
        0x0F, 0x21, 0x31, 0x30,  // Background palette 0: Sky + bricks
        0x0F, 0x1A, 0x2A, 0x27,  // Background palette 1: Bushes + clouds
        0x0F, 0x16, 0x27, 0x18,  // Background palette 2: Ground + shadows
        0x0F, 0x00, 0x10, 0x20   // Background palette 3: Misc/unused
    )

    for (i in testPalette.indices) {
        vram[PALETTE_BASE + i] = testPalette[i]
    }
}

private fun fakeNameTables(vram: ByteArray) {
    val nametable = ByteArray(960) { i ->
        if ((i / 32 + i % 32) % 2 == 0) 0x05 else 0x06
    }
    nametable.copyInto(vram, destinationOffset = 0x2000)

    // Cycle through palettes
    for (i in 0x23C0 until 0x2400) {
        val quadrant = (i - 0x23C0) % 4
        val value = when (quadrant) {
            0 -> 0b00000000  // All palette 0
            1 -> 0b01010101  // All palette 1
            2 -> 0b10101010  // All palette 2
            else -> 0b11111111  // All palette 3
        }.toByte()
        vram[i] = value
    }
}
