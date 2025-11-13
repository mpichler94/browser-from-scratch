package io.github.mpichler94.util

import java.io.InputStream
import java.net.Socket

internal fun InputStream.readLine(): String {
    var index = 0
    var buffer = ByteArray(1024)

    while (true) {
        val b = read()
        if (b == -1) {
            return buffer.decodeToString(endIndex = index)
        }
        if (index >= buffer.size) {
            buffer = buffer.copyOf(buffer.size * 2)
        }
        buffer[index] = b.toByte()
        index++
        if (index > 1 && buffer[index - 2] == 0x0D.toByte() && buffer[index - 1] == 0x0A.toByte()) {
            return buffer.decodeToString(endIndex = index)
        }
    }
}

internal fun Socket.readHeaders(): List<String> {
    val lines = mutableListOf<String>()
    while (true) {
        val line = getInputStream().readLine()
        if (line.isBlank()) {
            break
        }
        lines.add(line)
    }
    return lines
}