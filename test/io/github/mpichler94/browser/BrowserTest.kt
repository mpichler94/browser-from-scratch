package io.github.mpichler94.browser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.junit.jupiter.MockServerExtension
import org.mockserver.junit.jupiter.MockServerSettings
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import kotlin.test.Test

@ExtendWith(MockServerExtension::class)
@MockServerSettings(ports = [8080])
class BrowserTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup(client: ClientAndServer) {
            client.`when`(
                request()
                    .withPath("/example1-simple.html")
                    .withMethod("GET")
            ).respond(
                response().withBody("""
                <html>
                  <body>
                    <div>This is a simple</div>
                    <div>web page with some</div>
                    <span>text in it.</span>
                  </body>
                </html>
            """.trimIndent())
            )


        }
    }

    @Test
    @Disabled
    fun `should load example page`() {
        load("http://www.example.com/index.html")
    }

    @Test
    fun `should load simple sample`() {
        val url = "http://localhost:8080/example1-simple.html"
        load(url)

        assertThat(URL(url).request())
            .startsWith("<html>")
            .contains("This is a simple")
    }

    @Test
    fun `should load simple sample from file`() {
        val url = "file://testResources/example1-simple.html"
        load(url)

        assertThat(URL(url).request())
            .startsWith("<html>")
            .contains("This is a simple")
    }

    @Test
    fun `should load data url`() {
        val url = "data:text/html,Hello world!"
        load(url)

        assertThat(URL(url).request())
            .isEqualTo("Hello world!")
    }

    @Test
    fun `should show source with view-source`() {
        val url = "view-source:file://testResources/example1-simple.html"
        load(url)
    }

}