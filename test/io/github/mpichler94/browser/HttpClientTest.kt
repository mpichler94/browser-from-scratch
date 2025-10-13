package io.github.mpichler94.browser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.junit.jupiter.MockServerExtension
import org.mockserver.junit.jupiter.MockServerSettings
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
        }
    }

    // Yes, I know that this test is requesting a resource from a web server.
    // For me it is fine for now. Later I will replace it with a mock server.
    @Test
    fun `requests a resource from a web server`() {
        val url = URL("http://www.example.com/index.html")
        val client = HttpClient()
        val response = client.request(Request(url, "GET", mapOf("connection" to "keep-alive")))

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
}