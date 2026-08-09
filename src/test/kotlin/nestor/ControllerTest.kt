package nestor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe

/** Write 1 then 0 to $4016: latches the current button state and enters shift mode. */
private fun Controller.strobe() {
    write(1)
    write(0)
}

private fun Controller.read(times: Int) = List(times) { read() }

class ControllerTest : FreeSpec({

    "shift order" - {
        "reports buttons in the order A, B, Select, Start, Up, Down, Left, Right" {
            forAll(
                row(Button.A, 0),
                row(Button.B, 1),
                row(Button.SELECT, 2),
                row(Button.START, 3),
                row(Button.UP, 4),
                row(Button.DOWN, 5),
                row(Button.LEFT, 6),
                row(Button.RIGHT, 7),
            ) { button, position ->
                val controller = Controller()
                controller.press(button)
                controller.strobe()

                withClue("$button should be read #$position") {
                    controller.read(8) shouldBe List(8) { if (it == position) 1 else 0 }
                }
            }
        }

        "reports all zeroes when nothing is pressed" {
            val controller = Controller()
            controller.strobe()

            controller.read(8) shouldBe List(8) { 0 }
        }

        "reports all ones when everything is pressed" {
            val controller = Controller()
            Button.entries.forEach { controller.press(it) }
            controller.strobe()

            controller.read(8) shouldBe List(8) { 1 }
        }

        "reports several pressed buttons in one sequence" {
            val controller = Controller()
            controller.press(Button.A)
            controller.press(Button.START)
            controller.press(Button.RIGHT)
            controller.strobe()

            //                                  A  B  Se St Up Dn Lf Rt
            controller.read(8) shouldBe listOf(1, 0, 0, 1, 0, 0, 0, 1)
        }

        "stops reporting a released button" {
            val controller = Controller()
            controller.press(Button.A)
            controller.press(Button.B)
            controller.release(Button.A)
            controller.strobe()

            controller.read(8) shouldBe listOf(0, 1, 0, 0, 0, 0, 0, 0)
        }

        "releasing a button that was never pressed is a no-op" {
            val controller = Controller()
            controller.press(Button.UP)
            controller.release(Button.DOWN)
            controller.strobe()

            controller.read(8) shouldBe listOf(0, 0, 0, 0, 1, 0, 0, 0)
        }

        "pressing the same button twice does not shift the report" {
            val controller = Controller()
            controller.press(Button.SELECT)
            controller.press(Button.SELECT)
            controller.strobe()

            controller.read(8) shouldBe listOf(0, 0, 1, 0, 0, 0, 0, 0)
        }
    }

    "trailing ones" - {
        "reads after the 8th report 1 forever" {
            val controller = Controller()
            controller.strobe()
            controller.read(8)

            controller.read(4) shouldBe listOf(1, 1, 1, 1)
        }

        "trailing ones follow the button data with no gap" {
            val controller = Controller()
            controller.press(Button.RIGHT)
            controller.strobe()

            // Right is the last real bit; everything after it is the shifted-in 1s.
            controller.read(10) shouldBe listOf(0, 0, 0, 0, 0, 0, 0, 1, 1, 1)
        }
    }

    "latching" - {
        "freezes the button state at the 1 to 0 transition" {
            val controller = Controller()
            controller.press(Button.A)
            controller.strobe()

            // Player lets go of A and hits B mid-sequence: the report must not change.
            controller.release(Button.A)
            controller.press(Button.B)

            controller.read(8) shouldBe listOf(1, 0, 0, 0, 0, 0, 0, 0)
        }

        "a button pressed after the latch is only seen by the next poll" {
            val controller = Controller()
            controller.strobe()
            controller.press(Button.START)
            controller.read(8) shouldBe List(8) { 0 }

            controller.strobe()
            controller.read(8) shouldBe listOf(0, 0, 0, 1, 0, 0, 0, 0)
        }

        "re-strobing mid-sequence restarts the report from A" {
            val controller = Controller()
            controller.press(Button.SELECT)
            controller.strobe()
            controller.read(2) shouldBe listOf(0, 0)   // consumed A and B

            controller.strobe()
            controller.read(8) shouldBe listOf(0, 0, 1, 0, 0, 0, 0, 0)
        }

        "only bit 0 of the written value is the strobe" {
            val controller = Controller()
            controller.press(Button.A)

            // $FF has bit 0 set (strobe high), $FE has it clear (strobe low).
            controller.write(0xFF)
            controller.write(0xFE)

            controller.read(8) shouldBe listOf(1, 0, 0, 0, 0, 0, 0, 0)
        }

        "writing 0 without a preceding 1 does not latch" {
            val controller = Controller()
            controller.strobe()
            controller.read(8)          // drain to trailing 1s
            controller.press(Button.A)

            // Strobe is already low; another 0 is not a transition, so no reload happens.
            controller.write(0)
            controller.read(1) shouldBe listOf(1)
        }
    }

    "strobe held high" - {
        "reads return the live A button and never advance" {
            val controller = Controller()
            controller.write(1)

            controller.read() shouldBe 0
            controller.press(Button.A)
            controller.read() shouldBe 1
            controller.read() shouldBe 1
            controller.release(Button.A)
            controller.read() shouldBe 0
        }

        "other buttons are invisible while strobe is high" {
            val controller = Controller()
            controller.press(Button.B)
            controller.press(Button.RIGHT)
            controller.write(1)

            controller.read(8) shouldBe List(8) { 0 }
        }

        "reads while high do not consume the register once strobe drops" {
            val controller = Controller()
            controller.press(Button.B)
            controller.write(1)
            controller.read(3)          // reads while high must not shift anything away
            controller.write(0)

            controller.read(8) shouldBe listOf(0, 1, 0, 0, 0, 0, 0, 0)
        }
    }

    "peek" - {
        "returns the next bit without shifting" {
            val controller = Controller()
            controller.press(Button.B)
            controller.strobe()

            controller.peek() shouldBe 0
            controller.peek() shouldBe 0
            controller.read() shouldBe 0    // A
            controller.peek() shouldBe 1
            controller.read() shouldBe 1    // B
        }

        "never disturbs a full report sequence" {
            val controller = Controller()
            controller.press(Button.DOWN)
            controller.strobe()

            val bits = List(8) {
                controller.peek()
                controller.read()
            }

            bits shouldBe listOf(0, 0, 0, 0, 0, 1, 0, 0)
        }

        "returns the live A button while strobe is high" {
            val controller = Controller()
            controller.press(Button.A)
            controller.write(1)

            controller.peek() shouldBe 1
            controller.release(Button.A)
            controller.peek() shouldBe 0
        }
    }
})
