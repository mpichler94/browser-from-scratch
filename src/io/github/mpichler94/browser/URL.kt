package io.github.mpichler94.browser

import io.github.oshai.kotlinlogging.KotlinLogging

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
                val host = this.url.substringBefore("/")
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

    fun resolve(url: String): URL {
        if (url.isBlank()) {
            return this
        }

        var tmpUrl = url
        if ("://" in tmpUrl) {
            return URL(tmpUrl)
        }
        if (!tmpUrl.startsWith("/")) {
            var dir = path.substringBeforeLast('/')
            while (tmpUrl.startsWith("../")) {
                tmpUrl = tmpUrl.substringAfter("/")
                if ("/" in dir) {
                    dir = dir.substringBeforeLast("/")
                }
            }
            tmpUrl = "$dir/$tmpUrl"
        }
        if (tmpUrl.startsWith("//")) {
            return URL("$scheme:$tmpUrl")
        } else {
            return URL("$scheme://$host:$port$tmpUrl")
        }
    }

    fun withPath(path: String): URL = URL("$scheme://$host:$port$path")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as URL

        if (port != other.port) return false
        if (scheme != other.scheme) return false
        if (url != other.url) return false
        if (host != other.host) return false
        if (path != other.path) return false

        return true
    }

    override fun hashCode(): Int {
        var result = scheme.hashCode()
        result = 31 * result + url.hashCode()
        return result
    }

    override fun toString(): String {
        var portPart = ":$port"
        if (scheme == "https" && port == 443) {
            portPart = ""
        } else if (scheme == "http" && port == 80) {
            portPart = ""
        }

        return "$scheme://$host$portPart$path"
    }
}

