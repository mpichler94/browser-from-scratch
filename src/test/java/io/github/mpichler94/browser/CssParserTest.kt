package io.github.mpichler94.browser

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import kotlin.test.Test

class CssParserTest {


    @Test
    fun `parses CSS rules`() {
        val css = """
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

        assertThat(rules).contains(
            entry(
                TagSelector("html"),
                mapOf("font-size" to "24px", "line-height" to "1.2", "padding" to "1em")
            ),
            entry(
                ListSelector(TagSelector("pre"), TagSelector("code")),
                mapOf("hyphens" to "none", "-webkit-hyphens" to "none", "font-family" to "'Inconsolata', monospace")
            ),
            entry(
                DescendantSelector(TagSelector("header"), TagSelector("h1")),
                mapOf(
                    "margin" to "0",
                    "font-size" to "200%",
                    "font-weight" to "normal",
                    "text-align" to "center",
                    "letter-spacing" to "-0.08ex"
                )
            ),
            entry(DescendantSelector(TagSelector("header"), ClassSelector("author")), mapOf("font-style" to "italic"))

        )
    }

}