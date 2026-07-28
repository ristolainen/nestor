package nestor

import java.io.File
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun interface Tracer {
    fun trace(lineSupplier: () -> String)
    fun close() {}
}

class FileTracer(file: File = defaultTraceFile()) : Tracer {
    private val writer = PrintWriter(file)

    override fun trace(lineSupplier: () -> String) {
        writer.println(lineSupplier())
    }

    override fun close() {
        writer.close()
    }
}

private fun defaultTraceFile(): File {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return File("traces/$timestamp.txt").also { it.parentFile.mkdirs() }
}

object NullTracer : Tracer {
    override fun trace(lineSupplier: () -> String) {}
}
