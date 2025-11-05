package io.github.mpichler94.browser

data class Request(
    val url: URL,
    val method: String = "GET",
    private val _headers: Map<String, String> = mapOf(),
    private val rawBody: String? = null
) {
    val headers: Map<String, String>
    val body: ByteArray?

    init {
        val tmp = mutableMapOf("Host" to url.host, "Connection" to "keep-alive", "User-Agent" to "Browser from Scratch")
        if (rawBody != null) {
            body = rawBody.toByteArray()
            tmp["Content-Length"] = body.size.toString()
        } else {
            body = null
        }
        headers = tmp + _headers
    }

    fun encode(): String = "$method ${url.path} HTTP/1.1\r\n${
        headers.map { "${it.key}: ${it.value}" }.joinToString("\r\n", postfix = "\r\n")
    }\r\n${body?.decodeToString() ?: ""}"
}
