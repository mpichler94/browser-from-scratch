package io.github.mpichler94.browser

import java.io.File
import java.time.Instant

class Browser {
    val client = HttpClient()

    val cache: LinkedHashMap<URL, CachedResponse> = LinkedHashMap(1000, 0.75f)

    fun load(url: String) {
        val showSource = url.startsWith("view-source:")
        val parsedUrl = if (showSource) URL(url.substringAfter("view-source:")) else URL(url)

        val response = if (parsedUrl.scheme == "data") {
            Response(body = url.substringAfter(","))
        } else if (parsedUrl.scheme == "file") {
            Response(body = File(parsedUrl.path).readText())
        } else {
            if (parsedUrl in cache && cache[parsedUrl]!!.validUntil > Instant.now()) {
                cache[parsedUrl]!!.response
            } else {
                val response = client.request(parsedUrl.createRequest())
                if (response.headers["cache-control"]?.contains("max-age") == true) {
                    val maxAge = response.headers["cache-control"]!!.substringAfter("max-age=").toInt()
                    cache[parsedUrl] = CachedResponse(
                        Instant.now().plusSeconds(maxAge.toLong()),
                        response
                    )

                    // TODO: more sophisticated cache eviction
                    if (cache.size > 740) {
                        cache.remove(cache.keys.first())
                    }
                }
                response
            }
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

data class CachedResponse(val validUntil: Instant, val response: Response)
