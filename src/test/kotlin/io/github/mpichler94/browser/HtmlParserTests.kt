package io.github.mpichler94.browser

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldBeTypeOf

class HtmlParserTests :
    DescribeSpec({
        describe("valid") {
            it("parses simple HTML structure") {
                val html = "<html><body><div>Hello</div></body></html>"
                val parser = HtmlParser(html)
                val root = parser.parse()

                root.apply {
                    shouldBeTypeOf<Element>()
                    tag shouldBe "html"
                    children shouldHaveSize 1
                    children[0].apply {
                        shouldBeTypeOf<Element>()
                        tag shouldBe "body"
                        children shouldHaveSize 1
                        children[0].apply {
                            shouldBeTypeOf<Element>()
                            tag shouldBe "div"
                            children shouldHaveSize 1
                            children[0].apply {
                                shouldBeTypeOf<Text>()
                                text shouldBe "Hello"
                            }
                        }
                    }
                }
            }

            it("handles self-closing tags") {
                val html = "<html><body><img src=\"test.jpg\"/><br/></body></html>"
                val parser = HtmlParser(html)
                val root = parser.parse()

                root.children[0].apply {
                    children shouldHaveSize 2
                    children[0].apply {
                        shouldBeTypeOf<Element>()
                        tag shouldBe "img"
                        attributes shouldContainExactly mapOf("src" to "test.jpg")
                    }
                    children[1].apply {
                        shouldBeTypeOf<Element>()
                        tag shouldBe "br"
                    }
                }
            }

            it("handles self-closing tags with slash at end") {
                val html = "<html><body><img src=\"test.jpg\" /><br /></body></html>"
                val parser = HtmlParser(html)
                val root = parser.parse()

                root.children[0].apply {
                    children shouldHaveSize 2
                    children[0].apply {
                        shouldBeTypeOf<Element>()
                        tag shouldBe "img"
                        attributes shouldContainExactly mapOf("src" to "test.jpg")
                    }
                    children[1].apply {
                        shouldBeTypeOf<Element>()
                        tag shouldBe "br"
                    }
                }
            }

            it("parses attributes correctly") {
                val html = "<html><div id=\"main\" class=\"container\" data-value=\"123\"></div></html>"
                val parser = HtmlParser(html)
                val root = parser.parse()

                root.children[0].children[0].apply {
                    shouldBeTypeOf<Element>()
                    tag shouldBe "div"
                    attributes shouldContainExactly mapOf("id" to "main", "class" to "container", "data-value" to "123")
                }
            }

            it("decodes HTML entities") {
                val html = "<html><div>&lt;foo&gt;</div></html>"
                val parser = HtmlParser(html)
                val root = parser.parse()

                root.children[0].children[0].children[0].apply {
                    shouldBeTypeOf<Text>()
                    text shouldBe "<foo>"
                }
            }

            it("adds implicit html tag") {
                val html = "<body><div>Hello</div></body>"
                val parser = HtmlParser(html)
                val root = parser.parse()

                root.apply {
                    shouldBeTypeOf<Element>()
                    tag shouldBe "html"
                }
            }

            it("adds implicit head and body tags") {
                val html = "<title>Test</title><div>Hello</div>"
                val parser = HtmlParser(html)
                val root = parser.parse()

                root.children shouldHaveSize 2
                root.children[0].apply {
                    shouldBeTypeOf<Element>()
                    tag shouldBe "head"
                    children shouldHaveSize 1

                    children[0].apply {
                        shouldBeTypeOf<Element>()
                        tag shouldBe "title"
                    }
                }
                root.children[1].apply {
                    shouldBeTypeOf<Element>()
                    tag shouldBe "body"
                    children shouldHaveSize 1
                    children[0].apply {
                        shouldBeTypeOf<Element>()
                        tag shouldBe "div"
                    }
                }
            }
        }

        it("handles malformed HTML with implicit tags") {
            val html = "<title>Example 3</title><div>We just omit the html tags</div>"
            val parser = HtmlParser(html)
            val root = parser.parse()

            (root as Element).apply {
                tag shouldBe "html"
                children shouldHaveSize 2
                (children[0] as Element).tag shouldBe "head"
                (children[1] as Element).tag shouldBe "body"
            }
        }

        it("parses nested elements correctly") {
            val html = "<html><div><p><span>Text</span></p></div></html>"
            val parser = HtmlParser(html)
            val root = parser.parse()

            root.children[0].children[0].apply {
                children shouldHaveSize 1
                children[0].apply {
                    children shouldHaveSize 1
                    children[0].apply {
                        children shouldHaveSize 1
                        (children[0] as Text).text shouldBe "Text"
                    }
                }
            }
        }

        it("handles text nodes between elements") {
            val html = "<html><body><div>Before<span>Middle</span>After</div></body></html>"
            val parser = HtmlParser(html)
            val root = parser.parse()

            root.children[0].children[0].apply {
                children shouldHaveSize 3
                (children[0] as Text).text shouldBe "Before"
                (children[1] as Element).tag shouldBe "span"
                (children[2] as Text).text shouldBe "After"
            }
        }

        it("ignores comments and doctype") {
            val html = "<!DOCTYPE html><html><!-- comment --><body><div>Hello</div></body></html>"
            val parser = HtmlParser(html)
            val root = parser.parse()

            root.children shouldHaveSize 1
            (root.children[0] as Element).tag shouldBe "body"
        }

        it("printTree outputs correct structure") {
            val html = "<html><body><div>Hello</div></body></html>"
            val parser = HtmlParser(html)
            val root = parser.parse()

            // Just check it doesn't throw
            root.printTree()
        }
    })
