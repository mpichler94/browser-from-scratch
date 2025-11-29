package io.github.mpichler94.browser

import java.io.BufferedWriter
import java.io.File
import java.nio.Buffer

class MeasureTime {
    private val file: File = File("browser.trace")
    private val writer: BufferedWriter

    init {
        file.delete()
        file.createNewFile()
        writer = file.bufferedWriter()
        writer.write("""{"traceEvents": [""")

        val ts = System.nanoTime() / 1000.0
        writer.write(
            """{ "name": "process_name",
            "ph": "M",
            "ts": $ts,
            "pid": 1, "cat": "__metadata",
            "args": {"name": "Browser"}}
            """.trimMargin(),
        )
        writer.flush()
    }

    fun time(name: String) {
        val ts = System.nanoTime() / 1000.0
        writer.write(
            """
            , { "ph": "B", "cat": "_",
            "name": "$name",
            "ts": $ts,
             "pid": 1, "tid": ${Thread.currentThread().threadId()}}
            """.trimIndent(),
        )
        writer.flush()
    }

    fun stop(name: String) {
        val ts = System.nanoTime() / 1000.0
        writer.write(
            """
            , { "ph": "E", "cat": "_",
            "name": "$name",
            "ts": $ts,
            "pid": 1, "tid": ${Thread.currentThread().threadId()}}
        """,
        )
        writer.flush()
    }

    fun finish() {
        val threads = Thread.getAllStackTraces().keys
        threads.forEach {
            writer.write(
                """
            , { "ph": "M", "name": "thread_name",
             "pid": 1, "tid": ${it.threadId()},
             "args": { "name": "${it.name}" }}
                """.trimIndent(),
            )
        }

        writer.write("]}")
        writer.close()
    }
}
