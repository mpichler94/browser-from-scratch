package io.github.mpichler94.browser

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

class HttpClient {
    private val logger = KotlinLogging.logger {}

    private var socket: Socket? = null
    private var currentOrigin: String? = null

    fun request(request: Request): Response {
        require(request.url.scheme in listOf("http", "https"))

        return doHttpRequest(request)
    }

    private fun doHttpRequest(request: Request): Response {
        if (currentOrigin != "${request.url.host}:${request.url.port}" && socket?.isClosed == false) {
            socket?.close()
            socket = null
        }

        val socket = if (socket?.isClosed == false) {
            this.socket!!
        } else {
            if (request.url.scheme == "https") {
                SSLSocketFactory.getDefault().createSocket(request.url.host, request.url.port)
            } else {
                Socket(request.url.host, request.url.port)
            }
        }
        this.socket = socket
        this.currentOrigin = "${request.url.host}:${request.url.port}"
        socket.soTimeout = 5000

        logger.debug { "Sending request to '${request.url.host}': \n$request" }
        socket.getOutputStream().write(request.encode().toByteArray(Charsets.UTF_8))
        socket.getOutputStream().flush()

        val lines = mutableListOf<String>()
        while (true) {
            val line = socket.getInputStream().readLine()
            if (line.isBlank()) {
                break
            }
            lines.add(line)
        }

        val statusLine = lines.first()
        val (version, status, explanation) = statusLine.split(" ")
        val headers = lines.drop(1).takeWhile { it.isNotBlank() }.associate {
            val (key, value) = it.split(": ")
            key.lowercase() to value.trim()
        }

        check("transfer-encoding" !in headers)
        check("content-encoding" !in headers)

        val length = headers["content-length"]?.toInt() ?: 0
        val body = socket.getInputStream().readNBytes(length)

        logger.debug {
            "Reading response from '${request.url.host}': $statusLine\n${
                headers.map { "${it.key}: ${it.value}" }.joinToString("\n")
            }\n${body.decodeToString()}"
        }

        return Response(status.toInt(), headers, body.decodeToString())
    }


    private fun InputStream.readLine(): String {
        var index = 0
        var buffer = ByteArray(1024)

        while (true) {
            val b = read()
            if (b == -1) {
                return buffer.decodeToString()
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
}