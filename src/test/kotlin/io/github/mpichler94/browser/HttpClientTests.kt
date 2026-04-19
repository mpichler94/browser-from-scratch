package io.github.mpichler94.browser

import io.github.mpichler94.browser.io.HttpClient
import io.github.mpichler94.browser.io.Request
import io.github.mpichler94.browser.io.URL
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.mockserver.MockServerExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.ConnectionOptions
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class HttpClientTests :
    DescribeSpec({
        install(MockServerExtension(8080))

        beforeSpec {
            val client = MockServerClient("localhost", 8080)
            client
                .`when`(
                    request("/example1-simple.html").withMethod("GET"),
                ).respond(
                    response(
                        """
                        <html>
                          <body>
                            <div>This is a simple</div>
                            <div>web page with some</div>
                            <span>text in it.</span>
                          </body>
                        </html>
                        """.trimIndent(),
                    ),
                )

            client
                .`when`(
                    request("/example9-chunked.html").withMethod("GET"),
                ).respond(
                    response(
                        """
                        <html>
                          <body>
                            <div>This is a simple</div>
                            <div>web page with some</div>
                            <span>text in it.</span>
                          </body>
                        </html>
                        """.trimIndent(),
                    ).withConnectionOptions(ConnectionOptions().withChunkSize(10)),
                )

            client
                .`when`(
                    request("/index.html").withMethod("GET"),
                ).respond(
                    response(
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
                        """.trimIndent(),
                    ),
                )

            client
                .`when`(
                    request("/redirect")
                        .withMethod("GET"),
                ).respond(
                    response()
                        .withStatusCode(302)
                        .withHeader("Location", "/redirect-target"),
                )

            client
                .`when`(
                    request("/redirect2")
                        .withMethod("GET"),
                ).respond(
                    response()
                        .withStatusCode(302)
                        .withHeader("Location", "/redirect-target"),
                )

            client
                .`when`(
                    request("/redirect3")
                        .withMethod("GET"),
                ).respond(
                    response()
                        .withStatusCode(302)
                        .withHeader("Location", "/redirect-target"),
                )

            client
                .`when`(
                    request("/redirect-target")
                        .withMethod("GET"),
                ).respond(
                    response(
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
                        """.trimIndent(),
                    ),
                )

            val out = ByteArrayOutputStream(1000)
            val stream = GZIPOutputStream(out, 1000, true)
            stream.write("This is gzip compressed content".toByteArray())
            stream.finish()
            val data = out.toByteArray()
            client
                .`when`(
                    request("/gzip-test")
                        .withMethod("GET"),
                ).respond(
                    response()
                        .withBody(data)
                        .withHeader("Content-Encoding", "gzip"),
                )
        }

        it("requests a resource from mock server") {
            val url = URL("http://localhost:8080/index.html")
            val client = HttpClient.instance

            val response = client.request(Request(url, "GET", mapOf("connection" to "keep-alive", "accept-encoding" to "gzip")))

            response.body.apply {
                shouldStartWith("<!doctype html>")
                shouldContain("<title>Example Domain</title>")
                shouldContain("</html>")
            }
        }

        it("should load simple sample") {
            val url = "http://localhost:8080/example1-simple.html"
            val client = HttpClient.instance

            val response = client.request(Request(URL(url), "GET", mapOf("connection" to "keep-alive")))

            response.body.apply {
                shouldStartWith("<html>")
                shouldContain("This is a simple")
            }
        }

        it("should follow redirects") {
            val url = "http://localhost:8080/redirect"
            val client = HttpClient.instance

            val response = client.request(Request(URL(url), "GET", mapOf("connection" to "keep-alive")))

            response.body.apply {
                shouldStartWith("<!DOCTYPE html>")
                shouldContain("Downloading Web Pages")
            }
        }

        it("should follow redirects 2") {
            val url = "http://localhost:8080/redirect2"
            val client = HttpClient.instance

            val response = client.request(Request(URL(url), "GET", mapOf("connection" to "keep-alive")))

            response.body.apply {
                shouldStartWith("<!DOCTYPE html>")
                shouldContain("Downloading Web Pages")
            }
        }

        it("should follow redirects 3") {
            val url = "http://localhost:8080/redirect3"
            val client = HttpClient.instance

            val response = client.request(Request(URL(url), "GET", mapOf("connection" to "keep-alive")))

            response.body.apply {
                shouldStartWith("<!DOCTYPE html>")
                shouldContain("Downloading Web Pages")
            }
        }

        it("should load chunked response") {
            val url = "http://localhost:8080/example9-chunked.html"
            val client = HttpClient.instance

            val response = client.request(Request(URL(url), "GET", mapOf("connection" to "keep-alive")))

            response.body.apply {
                shouldStartWith("<html>")
                shouldContain("This is a simple")
            }
        }

        it("should handle gzip encoding") {
            val url = "http://localhost:8080/gzip-test"
            val client = HttpClient.instance

            val response = client.request(Request(URL(url), "GET", mapOf("accept-encoding" to "gzip")))

            response.body shouldBe "This is gzip compressed content"
        }

        it("should throw exception for invalid scheme") {
            val url = URL("file://example.com")
            val client = HttpClient.instance

            shouldThrow<IllegalArgumentException> {
                client.request(Request(url, "GET"))
            }
        }

        it("should reuse socket for same origin") {
            val url1 = "http://localhost:8080/example1-simple.html"
            val url2 = "http://localhost:8080/index.html"
            val client = HttpClient.instance

            val response1 = client.request(Request(URL(url1), "GET"))
            val response2 = client.request(Request(URL(url2), "GET"))

            response1.body shouldStartWith "<html>"
            response2.body shouldStartWith "<!doctype html>"
        }
    })
