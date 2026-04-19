package io.github.mpichler94.browser

import io.github.mpichler94.browser.io.URL
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class URLTests :
    DescribeSpec({
        it("throw exception for non http scheme") {
            shouldThrow<IllegalArgumentException> {
                URL("foo://test/index.html")
            }
        }

        it("extracts parts from valid http url") {
            val url = URL("http://www.example.com/index.html")

            url.apply {
                scheme shouldBe "http"
                host shouldBe "www.example.com"
                port shouldBe 80
                path shouldBe "/index.html"
            }
        }

        it("extracts parts from valid https url") {
            val url = URL("https://www.example.org/dir/something.html")

            url.apply {
                scheme shouldBe "https"
                host shouldBe "www.example.org"
                port shouldBe 443
                path shouldBe "/dir/something.html"
            }
        }

        it("extracts parts from valid file url") {
            val url = URL("file://C:/test/foo.txt")

            url.apply {
                scheme shouldBe "file"
                host shouldBe ""
                port shouldBe 0
                path shouldBe "C:/test/foo.txt"
            }
        }

        it("extracts parts from valid data url") {
            val url = URL("data:text/html,<html>Hello world!</html>")

            url.apply {
                scheme shouldBe "data"
                host shouldBe ""
                port shouldBe 0
                path shouldBe "text/html,<html>Hello world!</html>"
            }
        }

        it("extracts parts from http url with custom port") {
            val url = URL("http://www.example.com:8080/index.html")

            url.apply {
                scheme shouldBe "http"
                host shouldBe "www.example.com"
                port shouldBe 8080
                path shouldBe "/index.html"
            }
        }

        it("extracts parts from https url with custom port") {
            val url = URL("https://www.example.org:8443/dir/something.html")

            url.apply {
                scheme shouldBe "https"
                host shouldBe "www.example.org"
                port shouldBe 8443
                path shouldBe "/dir/something.html"
            }
        }

        it("extracts parts from http url without path") {
            val url = URL("http://www.example.com")

            url.apply {
                scheme shouldBe "http"
                host shouldBe "www.example.com"
                port shouldBe 80
                path shouldBe "/"
            }
        }

        it("extracts parts from https url without path") {
            val url = URL("https://www.example.org")

            url.apply {
                scheme shouldBe "https"
                host shouldBe "www.example.org"
                port shouldBe 443
                path shouldBe "/"
            }
        }

        it("extracts parts from http url with query parameters") {
            val url = URL("http://www.example.com/path?query=value")

            url.apply {
                scheme shouldBe "http"
                host shouldBe "www.example.com"
                port shouldBe 80
                path shouldBe "/path?query=value"
            }
        }

        it("withPath creates new URL with updated path") {
            val original = URL("http://www.example.com/old/path")
            val updated = original.withPath("/new/path")

            updated.apply {
                scheme shouldBe "http"
                host shouldBe "www.example.com"
                port shouldBe 80
                path shouldBe "/new/path"
            }
        }

        it("withPath works with custom port") {
            val original = URL("https://www.example.com:8443/old")
            val updated = original.withPath("/new")

            updated.apply {
                scheme shouldBe "https"
                host shouldBe "www.example.com"
                port shouldBe 8443
                path shouldBe "/new"
            }
        }

        it("equals returns true for identical URLs") {
            val url1 = URL("http://www.example.com/path")
            val url2 = URL("http://www.example.com/path")

            url1 shouldBe url2
        }

        it("equals returns false for different URLs") {
            val url1 = URL("http://www.example.com/path1")
            val url2 = URL("http://www.example.com/path2")

            url1 shouldNotBe url2
        }

        it("equals returns false for different schemes") {
            val url1 = URL("http://www.example.com/path")
            val url2 = URL("https://www.example.com/path")

            url1 shouldNotBe url2
        }

        it("equals returns false for different hosts") {
            val url1 = URL("http://www.example.com/path")
            val url2 = URL("http://www.other.com/path")

            url1 shouldNotBe url2
        }

        it("equals returns false for different ports") {
            val url1 = URL("http://www.example.com:80/path")
            val url2 = URL("http://www.example.com:8080/path")

            url1 shouldNotBe url2
        }

        it("hashCode is consistent with equals for identical URLs") {
            val url1 = URL("http://www.example.com/path")
            val url2 = URL("http://www.example.com/path")

            url1.hashCode() shouldBe url2.hashCode()
            url1 shouldBe url2
        }

        it("throws exception for invalid port number") {
            shouldThrow<NumberFormatException> {
                URL("http://www.example.com:abc/path")
            }
        }
    })
