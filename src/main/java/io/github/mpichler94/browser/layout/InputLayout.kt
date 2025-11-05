package io.github.mpichler94.browser.layout

import io.github.mpichler94.browser.DrawLine
import io.github.mpichler94.browser.DrawRect
import io.github.mpichler94.browser.DrawText
import io.github.mpichler94.browser.Drawable
import io.github.mpichler94.browser.Element
import io.github.mpichler94.browser.Text
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Color
import java.awt.Font
import java.awt.font.FontRenderContext

internal class InputLayout(
    override val node: Element,
    val parent: Layout,
    val previous: Layout?
) : Layout {
    private val logger = KotlinLogging.logger {}
    private val frc = FontRenderContext(null, true, true)
    private lateinit var font: Font

    override var x: Int = 0
        private set
    override var y: Int = 0
    override val width: Int = 200
    override var height: Int = 0
        private set
    override val children: List<Layout> = emptyList()

    override fun layout() {
        font = getFont(node.style)

        if (previous != null) {
            val space = font.getStringBounds(" ", frc).width.toInt()
            x = previous.x + previous.width + space
            y = previous.y
        } else {
            x = parent.x
            y = parent.y
        }

        val lineMetrics = font.getLineMetrics("1", frc)
        height = lineMetrics.height.toInt()
    }

    override fun paint(): List<Drawable> {
        val cmds = mutableListOf<Drawable>()

        val bgColor = node.style["background-color"]?.let { getColor(it) }
        if (bgColor != null) {
            cmds.add(DrawRect(x, y, x + width, y + height, bgColor))
        }

        val text = if (node.tag == "input") {
            node.attributes["value"] ?: ""
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
            val cx = x + font.getStringBounds(text, frc).width.toInt()
            cmds.add(DrawLine(cx, y, cx, y + height, Color.BLACK, 1))
        }

        return cmds
    }

    override fun toString(): String {
        return "Input { x=$x, y=$y, width=$width, height=$height }"
    }
}