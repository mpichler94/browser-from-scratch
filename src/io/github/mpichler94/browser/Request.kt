package io.github.mpichler94.browser

data class Request(val url: URL, val method: String = "GET", private val _headers: Map<String, String> = mapOf()) {
    val headers =
        mapOf("Host" to url.host, "Connection" to "keep-alive", "User-Agent" to "Browser from Scratch") + _headers

    fun encode(): String {
        return "$method ${url.path} HTTP/1.1\r\n${
            headers.map { "${it.key}: ${it.value}" }.joinToString("\r\n")
        }\r\n\r\n"
    }
}