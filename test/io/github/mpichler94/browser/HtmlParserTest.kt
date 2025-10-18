package io.github.mpichler94.browser

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class HtmlParserTest {

    @Test
    fun `parses simple HTML structure`() {
        val html = "<html><body><div>Hello</div></body></html>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        assertThat(root).isInstanceOf(Element::class.java)
        val element = root as Element
        assertThat(element.tag).isEqualTo("html")
        assertThat(element.children).hasSize(1)

        val body = element.children[0] as Element
        assertThat(body.tag).isEqualTo("body")
        assertThat(body.children).hasSize(1)

        val div = body.children[0] as Element
        assertThat(div.tag).isEqualTo("div")
        assertThat(div.children).hasSize(1)

        val text = div.children[0] as Text
        assertThat(text.text).isEqualTo("Hello")
    }

    @Test
    fun `handles self-closing tags`() {
        val html = "<html><body><img src=\"test.jpg\"/><br/></body></html>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        val body = (root as Element).children[0] as Element
        assertThat(body.children).hasSize(2)

        val img = body.children[0] as Element
        assertThat(img.tag).isEqualTo("img")
        assertThat(img.attributes).containsEntry("src", "test.jpg")

        val br = body.children[1] as Element
        assertThat(br.tag).isEqualTo("br")
    }

    @Test
    fun `handles self-closing tags with slash at end`() {
        val html = "<html><body><img src=\"test.jpg\" /><br /></body></html>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        val body = (root as Element).children[0] as Element
        assertThat(body.children).hasSize(2)

        val img = body.children[0] as Element
        assertThat(img.tag).isEqualTo("img")
        assertThat(img.attributes).containsEntry("src", "test.jpg")

        val br = body.children[1] as Element
        assertThat(br.tag).isEqualTo("br")
    }

    @Test
    fun `parses attributes correctly`() {
        val html = "<html><div id=\"main\" class=\"container\" data-value=\"123\"></div></html>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        val htmlElement = root as Element
        val body = htmlElement.children[0] as Element
        val div = body.children[0] as Element
        assertThat(div.tag).isEqualTo("div")
        assertThat(div.attributes).hasSize(3)
        assertThat(div.attributes).containsEntry("id", "main")
        assertThat(div.attributes).containsEntry("class", "container")
        assertThat(div.attributes).containsEntry("data-value", "123")
    }

    @Test
    fun `decodes HTML entities`() {
        val html = "<html><div>&lt;foo&gt;</div></html>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        val htmlElement = root as Element
        val body = htmlElement.children[0] as Element
        val div = body.children[0] as Element
        val text = div.children[0] as Text
        assertThat(text.text).isEqualTo("<foo>")
    }

    @Test
    fun `adds implicit html tag`() {
        val html = "<body><div>Hello</div></body>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        assertThat(root).isInstanceOf(Element::class.java)
        val element = root as Element
        assertThat(element.tag).isEqualTo("html")
    }

    @Test
    fun `adds implicit head and body tags`() {
        val html = "<title>Test</title><div>Hello</div>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        val htmlElement = root as Element
        assertThat(htmlElement.children).hasSize(2)

        val head = htmlElement.children[0] as Element
        assertThat(head.tag).isEqualTo("head")
        assertThat(head.children).hasSize(1)

        val title = head.children[0] as Element
        assertThat(title.tag).isEqualTo("title")

        val body = htmlElement.children[1] as Element
        assertThat(body.tag).isEqualTo("body")
        assertThat(body.children).hasSize(1)

        val div = body.children[0] as Element
        assertThat(div.tag).isEqualTo("div")
    }

    @Test
    fun `handles malformed HTML with implicit tags`() {
        val html = "<title>Example 3</title><div>We just omit the html tags</div>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        val htmlElement = root as Element
        assertThat(htmlElement.tag).isEqualTo("html")
        assertThat(htmlElement.children).hasSize(2)

        val head = htmlElement.children[0] as Element
        assertThat(head.tag).isEqualTo("head")

        val body = htmlElement.children[1] as Element
        assertThat(body.tag).isEqualTo("body")
    }

    @Test
    fun `parses nested elements correctly`() {
        val html = "<html><div><p><span>Text</span></p></div></html>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        val htmlElement = root as Element
        val body = htmlElement.children[0] as Element
        val div = body.children[0] as Element
        assertThat(div.children).hasSize(1)

        val p = div.children[0] as Element
        assertThat(p.children).hasSize(1)

        val span = p.children[0] as Element
        assertThat(span.children).hasSize(1)

        val text = span.children[0] as Text
        assertThat(text.text).isEqualTo("Text")
    }

    @Test
    fun `handles text nodes between elements`() {
        val html = "<html><body><div>Before<span>Middle</span>After</div></body></html>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        val htmlElement = root as Element
        val body = htmlElement.children[0] as Element
        val div = body.children[0] as Element
        assertThat(div.children).hasSize(3)

        val beforeText = div.children[0] as Text
        assertThat(beforeText.text).isEqualTo("Before")

        val span = div.children[1] as Element
        assertThat(span.tag).isEqualTo("span")

        val afterText = div.children[2] as Text
        assertThat(afterText.text).isEqualTo("After")
    }

    @Test
    fun `ignores comments and doctype`() {
        val html = "<!DOCTYPE html><html><!-- comment --><body><div>Hello</div></body></html>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        val htmlElement = root as Element
        assertThat(htmlElement.children).hasSize(1)

        val body = htmlElement.children[0] as Element
        assertThat(body.tag).isEqualTo("body")
    }

    @Test
    fun `printTree outputs correct structure`() {
        val html = "<html><body><div>Hello</div></body></html>"
        val parser = HtmlParser(html)
        val root = parser.parse()

        // Just check it doesn't throw
        root.printTree()
    }
}