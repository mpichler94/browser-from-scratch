package io.github.mpichler94.browser.io

data class Response(
    val status: Int = 200,
    val headers: Map<String, String> = mapOf(),
    val body: String,
)
