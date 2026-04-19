package io.github.mpichler94.browser

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainAll

class CssParserTests :
    DescribeSpec({
        describe("parser") {
            it("parses CSS rules") {
                val css =
                    """
                    html { font-size: 24px; line-height: 1.2; padding: 1em; }
                    
                    body {
                        max-width: 60ch; margin: 0 auto; font-family: 'Crimson Pro', 'Times', serif;
                        font-weight: normal; text-align: justify; hyphens: auto; -webkit-hyphens: auto;
                    }
                    pre, code { hyphens: none; -webkit-hyphens: none; font-family: 'Inconsolata', monospace; }
                    header h1 { margin: 0; font-size: 200%; font-weight: normal; text-align: center; letter-spacing: -0.08ex }
                    
                    .main header h1 { font-size: 200%; }
                    header .author { font-style: italic; }
                    header .author:before { content: "By "; }
                    nav.links {
                        line-height: 40px; text-align: center;
                        border-radius: 10px; background: #eee;
                        padding: 0 40px; position: relative; margin-bottom: 3em;
                    }
                    .highlight-region label {
                        position: absolute; font: bold 80% sans-serif; 
                    }
                    a { color: black !important; }
                    
                    """.trimIndent()

                val parser = CssParser(css)
                val rules = parser.parse()

                rules shouldContainAll
                    mapOf(
                        TagSelector("html") to mapOf("font-size" to "24px", "line-height" to "1.2", "padding" to "1em"),
                        ListSelector(TagSelector("pre"), TagSelector("code")) to
                            mapOf("hyphens" to "none", "-webkit-hyphens" to "none", "font-family" to "'Inconsolata', monospace"),
                        DescendantSelector(TagSelector("header"), TagSelector("h1")) to
                            mapOf(
                                "margin" to "0",
                                "font-size" to "200%",
                                "font-weight" to "normal",
                                "text-align" to "center",
                                "letter-spacing" to "-0.08ex",
                            ),
                        DescendantSelector(TagSelector("header"), ClassSelector(".author")) to mapOf("font-style" to "italic"),
                    )
            }
        }
    })
