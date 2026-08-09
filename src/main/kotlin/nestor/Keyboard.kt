package nestor

import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.KeyEvent.VK_X
import java.awt.event.KeyEvent.VK_Z

private val keyMap = mapOf(
    VK_UP to Button.UP,
    VK_DOWN to Button.DOWN,
    VK_LEFT to Button.LEFT,
    VK_RIGHT to Button.RIGHT,
    VK_X to Button.A,
    VK_Z to Button.B,
    VK_ENTER to Button.START,
    VK_SHIFT to Button.SELECT,
)

fun connectKeyboard(controller: Controller) {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { event ->
        keyMap[event.keyCode]?.let { button ->
            when (event.id) {
                KeyEvent.KEY_PRESSED -> controller.press(button)
                KeyEvent.KEY_RELEASED -> controller.release(button)
                else -> Unit
            }
        }
        false
    }
}
