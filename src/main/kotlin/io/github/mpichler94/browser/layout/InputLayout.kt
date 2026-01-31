package io.github.mpichler94.browser.layout

import io.github.humbleui.skija.Font
import io.github.humbleui.types.IRect
import io.github.humbleui.types.Rect
import io.github.mpichler94.browser.Element
import io.github.mpichler94.browser.Text
import io.github.mpichler94.browser.render.Color
import io.github.mpichler94.browser.render.DrawLine
import io.github.mpichler94.browser.render.DrawRRect
import io.github.mpichler94.browser.render.DrawText
import io.github.mpichler94.browser.render.Drawable
import io.github.oshai.kotlinlogging.KotlinLogging

internal class InputLayout(
    override val node: Element,
    val parent: Layout,
    val previous: Layout?,
) : Layout {
    private val logger = KotlinLogging.logger {}
    private lateinit var font: Font

    override var x = 0f
        private set
    override var y = 0f
    override var width = 200f
        private set
    override var height = 0f
        private set
    override val children: List<Layout> = emptyList()

    override fun layout() {
        font = getFont(node.style)

        if (previous != null) {
            val space = font.measureTextWidth(" ")
            x = previous.x + previous.width + space
            y = previous.y
        } else {
            x = parent.x
            y = parent.y
        }

        if (node.attributes["type"] == "hidden") {
            width = 0f
            height = 0f
        } else {
            height = font.metrics.height
        }
    }

    override fun paint(): List<Drawable> {
        if (node.attributes["type"] == "hidden") {
            return emptyList()
        }

        val cmds = mutableListOf<Drawable>()

        val bgColor = node.style["background-color"]?.let { getColor(it) }
        if (bgColor != null) {
            val radius = node.style["border-radius"]?.removeSuffix("px")?.toIntOrNull() ?: 0
            cmds.add(DrawRRect(rect, radius.toFloat(), bgColor))
        }

        val text = if (node.tag == "input") {
            val txt = node.attributes["value"] ?: ""
            if (node.attributes["type"] == "password") {
                "*".repeat(txt.length)
            } else {
                txt
            }
        } else if (node.tag == "button") {
            if (node.children.size == 1 && node.children.first() is Text) {
                (node.children.first() as Text).text
            } else {
                logger.warn { "Ignoring HTML contents inside button" }
                ""
            }
        } else {
            logger.warn { "Unsupported HTML element: ${node.tag}" }
            ""
        }

        val color = node.style["color"]?.let { getColor(it) } ?: Color.BLACK
        cmds.add(DrawText(x, y, text, font, color))

        if (node.isFocused) {
            val cx = x + font.measureTextWidth(text)
            cmds.add(DrawLine(cx, y, cx, y + height, Color.BLACK, 1f))
        }

        return cmds
    }

    override fun toString(): String = "Input { x=$x, y=$y, width=$width, height=$height }"
}
