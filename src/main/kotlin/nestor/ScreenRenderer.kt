package nestor

import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints.KEY_INTERPOLATION
import java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
import java.awt.image.BufferedImage
import javax.swing.JPanel

const val SCALE = 3

class ScreenRenderer : JPanel() {
    private val frameBuffer = BufferedImage(
        FRAME_WIDTH,
        FRAME_HEIGHT,
        BufferedImage.TYPE_INT_RGB,
    )

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        with (g as Graphics2D) {
            scale(SCALE.toDouble(), SCALE.toDouble())
            setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        }
        g.drawImage(frameBuffer, 0, 0, this)
    }

    override fun getPreferredSize() = Dimension(
        FRAME_WIDTH * SCALE,
        FRAME_HEIGHT * SCALE,
    )

    fun draw(pixels: IntArray) {
        frameBuffer.setRGB(
            0,
            0,
            FRAME_WIDTH,
            FRAME_HEIGHT,
            pixels,
            0,
            FRAME_WIDTH
        )
        repaint()
    }
}
