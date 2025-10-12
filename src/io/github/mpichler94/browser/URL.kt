package io.github.mpichler94.browser

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

class URL(url: String) {
    private val logger = KotlinLogging.logger {}

    val scheme = url.substringBefore(":")
    val url: String
    val host: String
    val port: Int
    val path: String

    init {
        require(scheme in listOf("http", "https", "file", "data"))

        when (scheme) {
            "http", "https" -> {
                this.url = url.substringAfter("://")
                val host = this.url.substringBeforeLast("/")
                val hostParts = host.split(":")
                this.host = hostParts.first()
                port = if (hostParts.size == 2) {
                    hostParts[1].toInt()
                } else {
                    when (scheme) {
                        "http" -> 80
                        "https" -> 443
                        else -> 0
                    }
                }
                this.path = "/${this.url.substringAfter("/", "")}"
            }
            "file" -> {
                this.url = url.substringAfter("://")
                this.host = ""
                this.port = 0
                this.path = this.url
            }
            "data" -> {
                this.url = url.substringAfter(":")
                this.host = ""
                this.port = 0
                this.path = this.url
            }
            else -> throw IllegalArgumentException("unknown scheme '$scheme'")
        }
    }


    fun request(headers: Map<String, String> = mapOf()): String = runBlocking {
        val allHeaders = mapOf("Host" to host, "Connection" to "close", "User-Agent" to "Browser from Scratch") + headers
        val request = "GET $path HTTP/1.1\r\n${allHeaders.map { "${it.key}: ${it.value}" }.joinToString("\r\n")}\r\n\r\n"

        if (scheme == "file") {
            File(path).readText()
        } else if (scheme == "data") {
            url.substringAfter(",")
        } else {
            val response = performRequest(request)
            val parts = response.split("\r\n\r\n")
            val lines = parts.first().split("\r\n")
            val statusLine = lines.first()
            val (version, status, explanation) = statusLine.split(" ")
            val headers = lines.drop(1).takeWhile { it.isNotBlank() }.associate {
                val (key, value) = it.split(": ")
                key.lowercase() to value.trim()
            }

            check("transfer-encoding" !in headers)
            check("content-encoding" !in headers)

            parts.last()
        }
    }

    private suspend fun performRequest(request: String) = coroutineScope {
        val socket = if (scheme == "https") SSLSocketFactory.getDefault().createSocket(host, port) else Socket(host, port)
        socket.soTimeout = 5000

        socket.use {
            val sendJob = launch { socket.send(request) }
            val readJob = async { socket.read() }
            sendJob.join()
            readJob.await()
        }
    }

    private suspend fun Socket.send(data: String) = withContext(Dispatchers.IO) {
        logger.debug { "Sending request to '$host': \n$data" }
        getOutputStream().write(data.toByteArray(Charsets.UTF_8))
        getOutputStream().flush()
    }

    private suspend fun Socket.read() = withContext(Dispatchers.IO) {
        val data = getInputStream().readAllBytes().decodeToString()
        logger.debug { "Reading response from '$host': \n$data" }
        data
    }
}

