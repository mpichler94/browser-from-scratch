package io.github.mpichler94.server

import io.github.mpichler94.util.readHeaders
import io.github.mpichler94.util.readLine
import org.apache.commons.text.StringEscapeUtils
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class Server(private val port: Int = 8000) {
    private val entries = mutableListOf("No names. We are nameless!" to "cerealkiller", "HACK THE PLANET!" to "crashoverride")
    private val sessions = mutableMapOf<String, Session>()
    private val logins = mapOf("crashoverride" to "0cool", "cerealkiller" to "emmanuel")
    private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME

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

            val token = if ("cookie" in headers) {
                headers["cookie"]!!.substringAfter("token=")
            } else {
                Uuid.random().toString()
            }
            val session = sessions.getOrPut(token) { Session() }

            val reqBody = if ("content-length" in headers) {
                val length = headers["content-length"]!!.toInt()
                socket.getInputStream().readNBytes(length)
            } else {
                null
            }

            val (status, body) = doRequest(session, method, url, headers, reqBody)

            val responseHeaders = mutableMapOf("Content-Length" to body.length.toString())
            if ("cookie" !in headers) {
                val expires = formatter.format(ZonedDateTime.now().plusSeconds(120))
                responseHeaders["Set-Cookie"] = "token=$token; SameSite=Lax; HttpOnly; expires=$expires"
            }
            responseHeaders["Content-Security-Policy"] = "default-src http://localhost:8000"

            val response = "HTTP/1.1 $status\r\n${responseHeaders.map{ "${it.key}: ${it.value}" }.joinToString("\r\n")}\r\n\r\n$body"

            socket.getOutputStream().write(response.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket.close()
        }
    }

    private fun doRequest(
        session: Session,
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?
    ): Pair<String, String> {
        if (method == "GET") {
            when {
                url == "/" -> return "200 OK" to showComments(session)
                url == "/comment.js" ->
                    return "200 OK" to Server::class.java.getResource("/server/comment.js").readText()
                url.startsWith("/comment.css") ->
                    return "200 OK" to Server::class.java.getResource("/server/comment.css").readText()
                url == "/login" -> return "200 OK" to loginForm(session)
            }
        } else if (method == "POST") {
            if (url == "/") {
                val params = formDecode(body)
                return doLogin(session, params)
            } else if (url == "/add") {
                val params = formDecode(body)
                return "200 OK" to addEntry(session, params)
            }
        }

        return "404 Not Found" to notFound(url, method)
    }

    private fun showComments(session: Session): String {
        return buildString {
            append("<!DOCTYPE html>")
            for ((entry, who) in entries) {
                append("<p>${StringEscapeUtils.escapeHtml4(entry)}\n")
                append("<i>by ${StringEscapeUtils.escapeHtml4(who)}</i</p>")
            }

            if (session.user != null) {
                val nonce = Uuid.random().toString()
                session.nonce = nonce
                append("<form action=add method=post>")
                append("<p><input name=guest></p>")
                append("<p><button>Sign the book!</button></p>")
                append("<input type=hidden name=nonce value=$nonce>")
                append("</form>")
                append("<strong></strong>")
            } else {
                append("<p><a href=/login>Sign in to write in the guest book</a></p>")
            }

            append("<script src=/comment.js></script>")
            append("<link rel=stylesheet href=/comment.css>")
            append("<script src=https://example.com/evil.js></script>")
        }
    }

    private fun formDecode(body: ByteArray?): Map<String, String> {
        if (body == null) return emptyMap()
        val params = mutableMapOf<String, String>()
        for (field in body.decodeToString().split("&")) {
            val (rawName, rawValue) = field.split("=", limit = 2)
            val name = URLDecoder.decode(rawName.replace("%20", "+"), Charsets.UTF_8)
            val value = URLDecoder.decode(rawValue.replace("%20", "+"), Charsets.UTF_8)
            params[name] = value
        }
        return params
    }

    private fun addEntry(session: Session, params: Map<String, String>): String {
        if (session.user == null) {
            return ""
        }
        if (session.nonce == null || session.nonce != params["nonce"]) {
            return ""
        }
        if ("guest" in params && params["guest"]!!.isNotBlank() && params["guest"]!!.length <= 100) {
            entries.add(params["guest"]!! to session.user!!)
        }
        return showComments(session)
    }

    private fun notFound(url: String, method: String): String {
        return buildString {
            append("<!DOCTYPE html>")
            append("<h1>$method $url not found</h1>")
        }
    }

    private fun loginForm(session: Session): String {
        return buildString {
            val nonce = Uuid.random().toString()
            session.nonce = nonce
            append("<!DOCTYPE html>")
            append("<form action=/ method=post>")
            append("<p>Username: <input name=username></p>")
            append("<p>Password: <input name=password type=password></p>")
            append("<p><button>Log in</button></p>")
            append("<input type=hidden name=nonce value=$nonce>")
            append("</form>")
        }
    }

    private fun doLogin(session: Session, params: Map<String, String>): Pair<String, String> {
        val username = params["username"]
        val password = params["password"]
        if (session.nonce == null || session.nonce != params["nonce"]) {
            return "400 Bad Request" to "Invalid CSRF token"
        } else if (username in logins && logins[username] == password) {
            session.user = username
            return "200 OK" to showComments(session)
        } else {
            return "401 Unauthorized" to buildString {
                append("<!DOCTYPE html>")
                append("<h1>Invalid password for $username</h1>")
            }
        }
    }
}

private class Session {
    var user: String? = null
    var nonce: String? = null
}