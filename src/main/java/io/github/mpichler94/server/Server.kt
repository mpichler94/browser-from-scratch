package io.github.mpichler94.server

import io.github.mpichler94.util.readHeaders
import io.github.mpichler94.util.readLine
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class Server(private val port: Int = 8000) {
    private val entries = mutableListOf("Pavel was here")

    fun start() {
        val serverSocket = ServerSocket(port)
        println("Server started on port $port")

        while (true) {
            val clientSocket = serverSocket.accept()
            handleRequest(clientSocket)
        }
    }

    private fun handleRequest(socket: Socket) {
        try {
            val requestLine = socket.getInputStream().readLine()
            println("Request: $requestLine")

            val (method, url, version) = requestLine.split(" ", limit = 3)
            require(method in listOf("GET", "POST")) { "Unsupported method: $method" }

            val headerList = socket.readHeaders()
            val headers = headerList
                .associate {
                    it.split(":", limit = 2).let { (header, value) -> header.lowercase().trim() to value.trim() }
                }

            val reqBody = if ("content-length" in headers) {
                val length = headers["content-length"]!!.toInt()
                socket.getInputStream().readNBytes(length)
            } else {
                null
            }

            val (status, body) = doRequest(method, url, headers, reqBody)

            // Simple response
            val response = "HTTP/1.1 $status\r\nContent-Length: ${body.length}\r\n\r\n$body"

            socket.getOutputStream().write(response.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket.close()
        }
    }

    private fun doRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?
    ): Pair<String, String> {
        if (method == "GET" && url == "/") {
            return "200 OK" to showComments()
        } else if (method == "GET" && url == "/comment.js") {
         return "200 OK" to Server::class.java.getResource("/server/comment.js").readText()
        } else if (method == "GET" && url.startsWith("/comment.css")) {
            return "200 OK" to Server::class.java.getResource("/server/comment.css").readText()
        } else if (method == "POST" && url == "/add") {
            val params = formDecode(body)
            return "200 OK" to addEntry(params)
        } else {
            return "404 Not Found" to notFound(url, method)
        }
    }

    private fun showComments(): String {
        return buildString {
            append("<!DOCTYPE html>")
            for (entry in entries) {
                append("<p>$entry</p>")
            }

            append("<form action=add method=post>")
            append("<p><input name=guest></p>")
            append("<p><button>Sign the book!</button></p>")
            append("</form>")
            append("<strong></strong>")
            append("<script src=/comment.js></script>")
            append("<link rel=stylesheet href=/comment.css>")
        }
    }

    private fun formDecode(body: ByteArray?): Map<String, String> {
        if (body == null) return emptyMap()
        val params = mutableMapOf<String, String>()
        for (field in body.decodeToString().split("&", limit = 2)) {
            val (rawName, rawValue) = field.split("=", limit = 2)
            val name = URLDecoder.decode(rawName.replace("%20", "+"), Charsets.UTF_8)
            val value = URLDecoder.decode(rawValue.replace("%20", "+"), Charsets.UTF_8)
            params[name] = value
        }
        return params
    }

    private fun addEntry(params: Map<String, String>): String {
        if ("guest" in params && params["guest"]!!.isNotBlank() && params["guest"]!!.length <= 100) {
            entries.add(params["guest"]!!)
        }
        return showComments()
    }

    private fun notFound(url: String, method: String): String {
        return buildString {
            append("<!DOCTYPE html>")
            append("<h1>$method $url not found</h1>")
        }
    }
}