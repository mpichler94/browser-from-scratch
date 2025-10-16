package io.github.mpichler94.browser

data class Response(val status: Int = 200, val headers: Map<String, String> = mapOf(), val body: String)
