package nestor

import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    // val rom = loadRomFile("test-roms/instr_test-v3/all_instrs.nes")
    val rom = loadRomFile("goodnes/Europe/Super Mario Bros. (E) (V1.1) [!].nes")
    // val rom = loadRomFile("goodnes/Europe/Legend of Zelda, The (E) (V1.1) [!].nes")
    val inesRom = RomReader.read(rom)
    println(inesRom.header)

    val tiles = TileParser.parseTiles(inesRom.chrData)
    println("Tiles size: " + tiles.size)
    // println("Tiles: " + tiles.take(3))

    val ppu = PPU(inesRom)

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

    val pixels = ppu.renderFrame()
    screen.draw(pixels)

    // println("Nestor NES Emulator starting...")
    // Placeholder main loop
}
