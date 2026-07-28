package nestor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

class NestestTest : FreeSpec({
    "headless nestest run matches nestest.log through the official opcodes" {
        val rom = RomReader.read(resourceBytes("/nestest/nestest.nes"))
        val golden = resourceLines("/nestest/nestest.golden.txt")

        val traceFile = File.createTempFile("nestest", ".txt").apply { deleteOnExit() }
        val nes = NES(rom, ScreenRenderer(), FileTracer(traceFile))

        var hitUnknownOpcode = false
        try {
            nes.run(entryPoint = 0xC000, until = { nes.cpu.cycles >= 20_000 })
        } catch (e: UnknownOpcodeException) {
            hitUnknownOpcode = true
        }
        withClue("nestest should halt at its first unofficial opcode") { hitUnknownOpcode shouldBe true }

        val lines = traceFile.readLines()
        withClue("last traced line is the unimplemented opcode we halted on") {
            lines.last().contains("???") shouldBe true
        }
        val produced = lines.dropLast(1)

        produced.forEachIndexed { i, line ->
            withClue("golden line ${i + 1}") { line shouldBe golden[i] }
        }
        produced.size shouldBeGreaterThan 5000
        withClue("should cover exactly the official section") { golden[produced.size].contains('*') shouldBe true }
    }
})

private fun resourceBytes(path: String) =
    checkNotNull(NestestTest::class.java.getResourceAsStream(path)) { "missing resource $path" }.readBytes()

private fun resourceLines(path: String) =
    checkNotNull(NestestTest::class.java.getResourceAsStream(path)) { "missing resource $path" }
        .bufferedReader().readLines()
