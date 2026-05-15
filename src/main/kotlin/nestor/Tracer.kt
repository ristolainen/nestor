package nestor

import java.io.File
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun interface Tracer {
    fun trace(line: String)
    fun close() {}
}

class FileTracer : Tracer {
    private val writer: PrintWriter

    init {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File("traces/$timestamp.txt")
        file.parentFile.mkdirs()
        writer = PrintWriter(file)
    }

    override fun trace(line: String) {
        writer.println(line)
    }

    override fun close() {
        writer.close()
    }
}

object NullTracer : Tracer {
    override fun trace(line: String) {}
}
