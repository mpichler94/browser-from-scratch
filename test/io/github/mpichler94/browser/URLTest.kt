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
}