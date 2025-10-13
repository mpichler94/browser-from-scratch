package io.github.mpichler94.browser

import java.io.File

class Browser {
    val client = HttpClient()

    fun load(url: String) {
        val showSource = url.startsWith("view-source:")
        val parsedUrl = if (showSource) URL(url.substringAfter("view-source:")) else URL(url)

        val response = if (parsedUrl.scheme == "data") {
            Response(body = url.substringAfter(","))
        } else if (parsedUrl.scheme == "file") {
            Response(body = File(parsedUrl.path).readText())
        } else {
            client.request(parsedUrl.createRequest())
        }

        if (showSource) {
            showSource(response.body)
        } else {
            show(response.body)
        }
    }

    private fun URL.createRequest(headers: Map<String, String> = mapOf()): Request {
        val allHeaders =
            mapOf("Host" to host, "Connection" to "keep-alive", "User-Agent" to "Browser from Scratch") + headers
        return Request(this, "GET", allHeaders)
    }

    private fun show(body: String) {
        var inTag = false
        var inEntity = false
        var entity = ""
        for (c in body) {
            when (c) {
                '<' -> inTag = true
                '>' -> inTag = false
                '&' -> inEntity = true
                ';' -> {
                    inEntity = false
                    print(entities[entity] ?: "")
                    entity = ""
                }

                else if (inEntity) -> entity += c
                else if (!inTag) -> print(c)
            }
        }
    }

    private fun showSource(body: String) {
        println(body)
    }

    private val entities = mapOf("lt" to "<", "gt" to ">")
}

