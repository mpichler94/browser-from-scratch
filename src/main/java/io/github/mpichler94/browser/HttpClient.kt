package io.github.mpichler94.browser

import io.github.mpichler94.util.readHeaders
import io.github.mpichler94.util.readLine
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.Socket
import java.net.SocketException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLSocketFactory
import kotlin.text.contains
import kotlin.text.get

class HttpClient private constructor() {
    companion object {
        private val COOKIE_JAR = mutableMapOf<String, Cookie>()

        val instance by lazy { HttpClient() }
    }
    private val logger = KotlinLogging.logger {}
    private val socketHandler = SocketHandler()
    private val cache: LinkedHashMap<URL, CachedResponse> = LinkedHashMap(1000, 0.75f)
    private val maxAgePattern = Regex("""max-age=(\d+)""")

    fun request(request: Request): Response {
        require(request.url.scheme in listOf("http", "https"))

        if (request.body == null && request.url in cache && cache[request.url]!!.validUntil > Instant.now()) {
            return cache[request.url]!!.response
        }

        val response = try {
            doHttpRequest(request)
        } catch (e: SocketException) {
            socketHandler.closeSocket()
            doHttpRequest(request)
        }

        if (response.headers["cache-control"]?.contains("max-age") == true) {
            val maxAge = maxAgePattern.find(response.headers["cache-control"]!!)?.groupValues[1]?.toInt() ?: 0
            cache[request.url] = CachedResponse(
                Instant.now().plusSeconds(maxAge.toLong()),
                response
            )

            // TODO: more sophisticated cache eviction
            if (cache.size > 740) {
                cache.remove(cache.keys.first())
            }
        }

        if ("set-cookie" in response.headers) {
            val cookie = response.headers["set-cookie"]!!
            COOKIE_JAR[request.url.host] = Cookie.parse(cookie)
        }

        return response
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

    internal fun getCookie(url: URL): Cookie? {
        if (url.host in COOKIE_JAR) {
            val cookie = COOKIE_JAR[url.host]!!
            if (cookie.isExpired) {
                COOKIE_JAR.remove(url.host)
                return null
            }
            return cookie
        }
        return null
    }

    internal fun setCookie(url: URL, cookie: Cookie) {
        COOKIE_JAR[url.host] = cookie
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

private data class CachedResponse(val validUntil: Instant, val response: Response)

internal data class Cookie(val value: String, val parameters: Map<String, String> = mapOf(), val expires: Instant = Instant.MAX) {
    companion object {
        private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME

        fun parse(cookie: String): Cookie {
            val params = mutableMapOf<String, String>()
            if (cookie.contains(";")) {
                val (value, rest) = cookie.split(";", limit = 2)
                for (param in rest.split("; ")) {
                    if (param.contains("=")) {
                        val (key, value) = param.split("=", limit = 2)
                        params[key.lowercase().trim()] = value.trim()
                    } else {
                        params[param.lowercase().trim()] = "true"
                    }
                }

                var expires = Instant.MAX
                if ("max-age" in params) {
                    val maxAge = params["max-age"]!!.toLong()
                   expires =  Instant.now().plusSeconds(maxAge)
                }
                if ("expires" in params) {
                    expires = formatter.parse(params["expires"]!!, Instant::from)
                }

                return Cookie(value, params, expires)
            } else {
                return Cookie(cookie)
            }
        }
    }

    val isExpired: Boolean get() = expires.isBefore(Instant.now())

    override fun toString(): String {
        return "$value;${parameters.entries.joinToString(";") { "${it.key}=${it.value}" }}"
    }
}