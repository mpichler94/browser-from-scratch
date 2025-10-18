package io.github.mpichler94.browser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.junit.jupiter.MockServerExtension
import org.mockserver.junit.jupiter.MockServerSettings
import org.mockserver.model.ConnectionOptions
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import kotlin.test.Test

@ExtendWith(MockServerExtension::class)
@MockServerSettings(ports = [8080])
class HttpClientTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup(client: ClientAndServer) {
            client.`when`(
                request()
                    .withPath("/example1-simple.html")
                    .withMethod("GET")
            ).respond(
                response().withBody(
                    """
                <html>
                  <body>
                    <div>This is a simple</div>
                    <div>web page with some</div>
                    <span>text in it.</span>
                  </body>
                </html>
            """.trimIndent()
                )
            )

            client.`when`(
                request()
                    .withPath("/example9-chunked.html")
                    .withMethod("GET")
            ).respond(
                response().withBody(
                    """
                <html>
                  <body>
                    <div>This is a simple</div>
                    <div>web page with some</div>
                    <span>text in it.</span>
                  </body>
                </html>
            """.trimIndent()
                ).withConnectionOptions(ConnectionOptions().withChunkSize(10))
            )

            client.`when`(
                request()
                    .withPath("/index.html")
                    .withMethod("GET")
            ).respond(
                response().withBody(
                    """
                <!doctype html>
                <html>
                <head>
                    <title>Example Domain</title>
                </head>
                <body>
                    <div>
                        <h1>Example Domain</h1>
                        <p>This domain is for use in illustrative examples in documents. You may use this
                        domain in literature without prior coordination or asking for permission.</p>
                        <p><a href="https://www.iana.org/domains/example">More information...</a></p>
                    </div>
                </body>
                </html>
            """.trimIndent()
                )
            )

            client.`when`(
                request()
                    .withPath("/redirect")
                    .withMethod("GET")
            ).respond(
                response()
                    .withStatusCode(302)
                    .withHeader("Location", "/redirect-target")
            )

            client.`when`(
                request()
                    .withPath("/redirect2")
                    .withMethod("GET")
            ).respond(
                response()
                    .withStatusCode(302)
                    .withHeader("Location", "/redirect-target")
            )

            client.`when`(
                request()
                    .withPath("/redirect3")
                    .withMethod("GET")
            ).respond(
                response()
                    .withStatusCode(302)
                    .withHeader("Location", "/redirect-target")
            )

            client.`when`(
                request()
                    .withPath("/redirect-target")
                    .withMethod("GET")
            ).respond(
                response().withBody(
                    """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Downloading Web Pages</title>
                </head>
                <body>
                    <h1>Downloading Web Pages</h1>
                    <p>This is the content for Downloading Web Pages.</p>
                </body>
                </html>
            """.trimIndent()
                )
            )

            client.`when`(
                request()
                    .withPath("/gzip-test")
                    .withMethod("GET")
            ).respond(
                response()
                    .withBody("This is gzip compressed content".toByteArray())
                    .withHeader("Content-Encoding", "gzip")
            )
        }
    }

    @Test
    fun `requests a resource from mock server`() {
        val url = URL("http://localhost:8080/index.html")
        val client = HttpClient()
        val response =
            client.request(Request(url, "GET", mapOf("connection" to "keep-alive", "accept-encoding" to "gzip")))

        assertThat(response.body)
            .startsWith("<!doctype html>")
            .contains("<title>Example Domain</title>")
            .contains("</body></html>")
    }

    @Test
    fun `should load simple sample`() {
        val url = "http://localhost:8080/example1-simple.html"
        val client = HttpClient()

        assertThat(client.request(Request(URL(url), "GET", mapOf("connection" to "keep-alive"))).body)
            .startsWith("<html>")
            .contains("This is a simple")
    }

    @Test
    fun `should follow redirects`() {
        val url = "http://localhost:8080/redirect"
        val client = HttpClient()

        assertThat(client.request(Request(URL(url), "GET", mapOf("connection" to "keep-alive"))).body)
            .startsWith("<!DOCTYPE html>")
            .contains("Downloading Web Pages")
    }

    @Test
    fun `should follow redirects 2`() {
        val url = "http://localhost:8080/redirect2"
        val client = HttpClient()

        assertThat(client.request(Request(URL(url), "GET", mapOf("connection" to "keep-alive"))).body)
            .startsWith("<!DOCTYPE html>")
            .contains("Downloading Web Pages")
    }

    @Test
    fun `should follow redirects 3`() {
        val url = "http://localhost:8080/redirect3"
        val client = HttpClient()

        assertThat(client.request(Request(URL(url), "GET", mapOf("connection" to "keep-alive"))).body)
            .startsWith("<!DOCTYPE html>")
            .contains("Downloading Web Pages")
    }

    @Test
    fun `should load chunked response`() {
        val url = "http://localhost:8080/example9-chunked.html"
        val client = HttpClient()

        assertThat(client.request(Request(URL(url), "GET", mapOf("connection" to "keep-alive"))).body)
            .startsWith("<html>")
            .contains("This is a simple")
    }

    @Test
    fun `should handle gzip encoding`() {
        val url = "http://localhost:8080/gzip-test"
        val client = HttpClient()

        val response = client.request(Request(URL(url), "GET", mapOf("accept-encoding" to "gzip")))

        assertThat(response.body).isEqualTo("This is gzip compressed content")
    }

    @Test
    fun `should throw exception for invalid scheme`() {
        val url = URL("ftp://example.com")
        val client = HttpClient()

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            client.request(Request(url, "GET"))
        }
    }

    @Test
    fun `should reuse socket for same origin`() {
        val url1 = "http://localhost:8080/example1-simple.html"
        val url2 = "http://localhost:8080/index.html"
        val client = HttpClient()

        val response1 = client.request(Request(URL(url1), "GET"))
        val response2 = client.request(Request(URL(url2), "GET"))

        assertThat(response1.body).startsWith("<html>")
        assertThat(response2.body).startsWith("<!doctype html>")
    }
}
