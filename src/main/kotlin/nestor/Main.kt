package nestor

import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    val romFile = loadRomFile("goodnes/Europe/Super Mario Bros. (E) (V1.1) [!].nes")
    // val romFile = loadRomFile("goodnes/Europe/Excitebike (E) [!].nes")
    // val romFile = loadRomFile("test-roms/other/nestest.nes")
    val rom = RomReader.read(romFile)

    val screen = ScreenRenderer()
    displayScreen(screen)

    val tracer = NullTracer
    val nes = NES(rom, screen, tracer)
    try {
        nes.run()
    } catch (e: UnknownOpcodeException) {
        println(e.message)
    }
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
