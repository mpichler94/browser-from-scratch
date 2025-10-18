package io.github.mpichler94.browser

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class URLTest {

    @Test
    fun `throw exception for non http scheme`() {
        assertThatThrownBy {
            val url = URL("foo://test/index.html")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `extracts parts from valid http url`() {
        val url = URL("http://www.example.com/index.html")

        assertThat(url.scheme).isEqualTo("http")
        assertThat(url.host).isEqualTo("www.example.com")
        assertThat(url.port).isEqualTo(80)
        assertThat(url.path).isEqualTo("/index.html")
    }

    @Test
    fun `extracts parts from valid https url`() {
        val url = URL("https://www.example.org/dir/something.html")

        assertThat(url.scheme).isEqualTo("https")
        assertThat(url.host).isEqualTo("www.example.org")
        assertThat(url.port).isEqualTo(443)
        assertThat(url.path).isEqualTo("/dir/something.html")
    }

    @Test
    fun `extracts parts from valid file url`() {
        val url = URL("file://C:/test/foo.txt")

        assertThat(url.scheme).isEqualTo("file")
        assertThat(url.host).isEqualTo("")
        assertThat(url.port).isEqualTo(0)
        assertThat(url.path).isEqualTo("C:/test/foo.txt")
    }

    @Test
    fun `extracts parts from valid data url`() {
        val url = URL("data:text/html,<html>Hello world!</html>")

        assertThat(url.scheme).isEqualTo("data")
        assertThat(url.host).isEqualTo("")
        assertThat(url.port).isEqualTo(0)
        assertThat(url.path).isEqualTo("text/html,<html>Hello world!</html>")
    }

    @Test
    fun `extracts parts from http url with custom port`() {
        val url = URL("http://www.example.com:8080/index.html")

        assertThat(url.scheme).isEqualTo("http")
        assertThat(url.host).isEqualTo("www.example.com")
        assertThat(url.port).isEqualTo(8080)
        assertThat(url.path).isEqualTo("/index.html")
    }

    @Test
    fun `extracts parts from https url with custom port`() {
        val url = URL("https://www.example.org:8443/dir/something.html")

        assertThat(url.scheme).isEqualTo("https")
        assertThat(url.host).isEqualTo("www.example.org")
        assertThat(url.port).isEqualTo(8443)
        assertThat(url.path).isEqualTo("/dir/something.html")
    }

    @Test
    fun `extracts parts from http url without path`() {
        val url = URL("http://www.example.com")

        assertThat(url.scheme).isEqualTo("http")
        assertThat(url.host).isEqualTo("www.example.com")
        assertThat(url.port).isEqualTo(80)
        assertThat(url.path).isEqualTo("/")
    }

    @Test
    fun `extracts parts from https url without path`() {
        val url = URL("https://www.example.org")

        assertThat(url.scheme).isEqualTo("https")
        assertThat(url.host).isEqualTo("www.example.org")
        assertThat(url.port).isEqualTo(443)
        assertThat(url.path).isEqualTo("/")
    }

    @Test
    fun `extracts parts from http url with query parameters`() {
        val url = URL("http://www.example.com/path?query=value")

        assertThat(url.scheme).isEqualTo("http")
        assertThat(url.host).isEqualTo("www.example.com")
        assertThat(url.port).isEqualTo(80)
        assertThat(url.path).isEqualTo("/path?query=value")
    }

    @Test
    fun `withPath creates new URL with updated path`() {
        val original = URL("http://www.example.com/old/path")
        val updated = original.withPath("/new/path")

        assertThat(updated.scheme).isEqualTo("http")
        assertThat(updated.host).isEqualTo("www.example.com")
        assertThat(updated.port).isEqualTo(80)
        assertThat(updated.path).isEqualTo("/new/path")
    }

    @Test
    fun `withPath works with custom port`() {
        val original = URL("https://www.example.com:8443/old")
        val updated = original.withPath("/new")

        assertThat(updated.scheme).isEqualTo("https")
        assertThat(updated.host).isEqualTo("www.example.com")
        assertThat(updated.port).isEqualTo(8443)
        assertThat(updated.path).isEqualTo("/new")
    }

    @Test
    fun `equals returns true for identical URLs`() {
        val url1 = URL("http://www.example.com/path")
        val url2 = URL("http://www.example.com/path")

        assertThat(url1).isEqualTo(url2)
    }

    @Test
    fun `equals returns false for different URLs`() {
        val url1 = URL("http://www.example.com/path1")
        val url2 = URL("http://www.example.com/path2")

        assertThat(url1).isNotEqualTo(url2)
    }

    @Test
    fun `equals returns false for different schemes`() {
        val url1 = URL("http://www.example.com/path")
        val url2 = URL("https://www.example.com/path")

        assertThat(url1).isNotEqualTo(url2)
    }

    @Test
    fun `equals returns false for different hosts`() {
        val url1 = URL("http://www.example.com/path")
        val url2 = URL("http://www.other.com/path")

        assertThat(url1).isNotEqualTo(url2)
    }

    @Test
    fun `equals returns false for different ports`() {
        val url1 = URL("http://www.example.com:80/path")
        val url2 = URL("http://www.example.com:8080/path")

        assertThat(url1).isNotEqualTo(url2)
    }

    @Test
    fun `hashCode is consistent with equals for identical URLs`() {
        val url1 = URL("http://www.example.com/path")
        val url2 = URL("http://www.example.com/path")

        assertThat(url1.hashCode()).isEqualTo(url2.hashCode())
        assertThat(url1).isEqualTo(url2)
    }

    @Test
    fun `throws exception for invalid port number`() {
        assertThatThrownBy {
            URL("http://www.example.com:abc/path")
        }.isInstanceOf(NumberFormatException::class.java)
    }
}
