package nestor

enum class Button(val bit: Int) {
    A(0),
    B(1),
    SELECT(2),
    START(3),
    UP(4),
    DOWN(5),
    LEFT(6),
    RIGHT(7),
}

class Controller {
    @Volatile
    private var buttons = 0

    private var strobe = false

    private var shift = 0

    fun press(button: Button) {
        buttons = buttons or (1 shl button.bit)
    }

    fun release(button: Button) {
        buttons = buttons and (1 shl button.bit).inv()
    }

    fun write(value: Int) {
        strobe = (value and 1) != 0
        if (strobe) {
            shift = buttons
        }
    }

    fun read(): Int {
        if (strobe) {
            shift = buttons
        }
        val bit = shift and 1
        if (!strobe) {
            shift = (shift ushr 1) or 0x80
        }
        return bit
    }

    fun peek(): Int = if (strobe) {
        buttons and 1
    } else {
        shift and 1
    }
}
