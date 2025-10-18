package io.github.mpichler94.browser

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream
import java.net.Socket
import java.net.SocketException
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLSocketFactory

class HttpClient {
    private val logger = KotlinLogging.logger {}
    private val socketHandler = SocketHandler()

    fun request(request: Request): Response {
        require(request.url.scheme in listOf("http", "https"))

        return try {
            doHttpRequest(request)
        } catch (e: SocketException) {
            socketHandler.closeSocket()
            doHttpRequest(request)
        }
    }

    private fun doHttpRequest(request: Request, depth: Int = 0): Response {
        val socket = socketHandler.getSocket(request)

        logger.debug { "Sending request to '${request.url.host}': \n$request" }
        socket.getOutputStream().write(request.encode().toByteArray(Charsets.UTF_8))
        socket.getOutputStream().flush()

        val lines = socket.readHeaders()
        val statusLine = lines.first()
        val (version, status, explanation) = statusLine.split(" ", limit = 3)
        val headers = lines.drop(1).takeWhile { it.isNotBlank() }.associate {
            val (key, value) = it.split(": ")
            key.lowercase() to value.trim()
        }

        val useGZip = headers["content-encoding"] == "gzip"
        val body = if (headers["transfer-encoding"] == "chunked") {
            socket.readChunks(useGZip)
        } else {
            val length = headers["content-length"]?.toInt() ?: 0

            if (useGZip) {
                GZIPInputStream(socket.getInputStream()).readNBytes(length)
            } else {
                socket.getInputStream().readNBytes(length)
            }
        }

        logger.debug {
            "Reading response from '${request.url.host}': $statusLine\n${
                headers.map { "${it.key}: ${it.value}" }.joinToString("\n")
            }\n${body.decodeToString()}"
        }

        if (status.startsWith("3") && "location" in headers) {
            val location = headers["location"]!!
            val newUrl = if (location.startsWith("/")) {
                request.url.withPath(location)
            } else {
                URL(location)
            }
            if (depth < 15) {
                return doHttpRequest(Request(newUrl, request.method, request.headers), depth + 1)
            }
        }

        return Response(status.toInt(), headers, body.decodeToString())
    }

    private fun Socket.readHeaders(): List<String> {
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

    private fun Socket.readChunks(useGZip: Boolean): ByteArray {
        var bytes = ByteArray(0)
        while (true) {
            val header = getInputStream().readLine().trim()
            val size = header.toInt(16)
            if (size == 0) {
                // read \r\n
                getInputStream().readNBytes(2)
                return bytes
            }

            bytes += if (useGZip) {
                GZIPInputStream(getInputStream()).readNBytes(size)
            } else {
                getInputStream().readNBytes(size)
            }
            // read \r\n
            getInputStream().readNBytes(2)
        }
    }
}

private class SocketHandler {
    private var socket: Socket? = null
    private var currentOrigin: String? = null

    fun getSocket(request: Request): Socket {
        if (currentOrigin != "${request.url.host}:${request.url.port}" && socket?.isClosed == false) {
            socket?.close()
            socket = null
        }

        if (socket?.isClosed == false) {
            return this.socket!!
        }

        this.currentOrigin = "${request.url.host}:${request.url.port}"
        socket = createSocket(request)
        return socket!!
    }

    fun closeSocket() {
        socket?.close()
        socket = null
        currentOrigin = null
    }

    private fun createSocket(request: Request): Socket = if (request.url.scheme == "https") {
        SSLSocketFactory.getDefault().createSocket(request.url.host, request.url.port)
    } else {
        Socket(request.url.host, request.url.port)
    }.apply {
        soTimeout = 5000
    }
}
