package io.github.mpichler94.browser

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import kotlin.test.Test

class URLTest {

    @Test
    fun `throw exception for non http scheme`() {
        assertThatThrownBy {
            val url = URL("file://test/index.html")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `extracts parts from valid url`() {
        val url = URL("http://www.example.com/index.html")

        assertThat(url.scheme).isEqualTo("http")
        assertThat(url.host).isEqualTo("www.example.com")
        assertThat(url.path).isEqualTo("/index.html")
    }

    // Yes, I know that this test is requesting a resource from a web server.
    // For me it is fine for now. Later I will replace it with a mock server.
    @Test
    @Disabled
    fun `requests a resource from a web server`() {
        val url = URL("http://www.example.com/index.html")
        val content = url.request()

        assertThat(content)
            .startsWith("<!doctype html>")
            .contains("<title>Example Domain</title>")
            .contains("</body></html>")
    }
}