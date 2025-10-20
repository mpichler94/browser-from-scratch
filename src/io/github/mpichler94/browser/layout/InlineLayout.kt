package io.github.mpichler94.browser.layout

import io.github.mpichler94.browser.DrawRect
import io.github.mpichler94.browser.DrawText
import io.github.mpichler94.browser.Drawable
import io.github.mpichler94.browser.Element
import io.github.mpichler94.browser.Text
import io.github.mpichler94.browser.Token
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.font.FontRenderContext

internal class InlineLayout(
    private val nodes: List<Token>,
    private val parent: Layout,
    private val previous: Layout?,
) : Layout {
    private val vStep = 50
    private val frc = FontRenderContext(null, true, true)

    private val fontCache = mutableMapOf<FontKey, Font>()
    override val children = mutableListOf<Layout>()
    private var cursorX = 0
    private var cursorY = 0
    private var line = mutableListOf<DrawText>()
    private var center = false
    private var sup = false
    private var abbr = false
    private var pre = false
    override var x = 0
        private set
    override var y = 0
        private set
    override var width = 0
        private set
    override var height = 0
        private set

    private val displayList = mutableListOf<DrawText>()

    override fun layout() {
        if (previous is InlineLayout) {
            x = previous.x + previous.width
            y = previous.y
        } else {
            x = parent.x
            y = previous?.let { it.y + it.height } ?: parent.y
        }

        width = parent.width
        cursorX = 0
        cursorY = 0
        line = mutableListOf()

        nodes.forEach { recurse(it) }
        flush()

        children.forEach { it.layout() }

        height = cursorY
    }

    private fun recurse(tree: Token) {
        if (tree is Text) {
            text(tree)
        } else if (tree is Element) {
            openTag(tree)
            tree.children.forEach { recurse(it) }
            closeTag(tree)
        }
    }

    private fun text(node: Text) {
        val font = getFont(node.style)
        val space = font.getStringBounds(" ", frc).width.toInt()
        val color = node.style["color"]?.let { getColor(it) } ?: Color.BLACK
        if (pre) {
            word(node.text, space, font, color)
            return
        }
        for (word in node.text.split(' ')) {
            word(if (abbr) word.uppercase() else word, space, font, color)
        }
    }

    private fun word(word: String, space: Int, font: Font, color: Color) {
        if (word.isBlank()) {
            return
        }

        val bounds = font.getStringBounds(word, frc)
        val w = bounds.width.toInt()
        if (cursorX + w > width) {
            flush()
        }

        val y = if (sup) cursorY - bounds.height.toInt() / 2 else cursorY
        line.add(DrawText(cursorX, y, word.trim(), font, color))
        cursorX += w + space
    }

    private fun openTag(tag: Element) {
        when (tag.tag) {
            "br" -> flush()
            "h1" -> center = true
            "sup" -> sup = true
            "abbr" -> abbr = true
            "pre" -> pre = true
            "li" -> cursorX += 15
        }
    }

    private fun closeTag(tag: Element) {
        when (tag.tag) {
            "p" -> {
                flush()
                cursorY += vStep
            }

            "h1" -> {
                flush()
                center = false
            }

            "sup" -> sup = false
            "abbr" -> abbr = false
            "pre" -> pre = false
        }
    }

    private fun flush() {
        val maxAscend = line.maxOfOrNull { it.font.getLineMetrics(it.text, frc).ascent.toInt() } ?: 0
        val baseline = cursorY + 1.25 * maxAscend
        val maxDescend = line.maxOfOrNull { it.font.getLineMetrics(it.text, frc).descent.toInt() } ?: 0

        cursorY = (baseline + 1.25 * maxDescend).toInt()
        width = cursorX
        cursorX = 0

        for (element in line) {
            val x = this.x + element.left
            val y = this.y + baseline.toInt() - element.font.getLineMetrics(element.text, frc).ascent.toInt()
            displayList.add(DrawText(x, y, element.text, element.font, element.color))
        }

        line.clear()
    }

    private fun getFont(cssStyle: Map<String, String>): Font {
        val weight = when (cssStyle["font-weight"]) {
            "bold" -> Font.BOLD
            else -> Font.PLAIN
        }
        val style = when (cssStyle["font-style"]) {
            "italic" -> Font.ITALIC
            else -> Font.PLAIN
        }
        val size = ((cssStyle["font-size"]?.dropLast(2)?.toIntOrNull() ?: 16).toFloat() * 0.75).toInt()

        val fontFamilies =
            (cssStyle["font-family"]?.split(',') ?: listOf("sans-serif")).map { it.trim().trim('\'', '"') }
        var fontFamily = fontFamilies.last()
        for (family in fontFamilies) {
            if (family in GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames) {
                fontFamily = family
                break
            }
        }

        when (fontFamily.lowercase()) {
            "serif" -> fontFamily = Font.SERIF
            "sans-serif" -> fontFamily = Font.SANS_SERIF
            "monospace" -> fontFamily = Font.MONOSPACED
        }

        val key = FontKey(fontFamily, size, weight, style)

        if (key !in fontCache) {
            val font = Font(key.font, key.weight + key.style, key.size)
            fontCache[key] = font
            return font
        }
        return fontCache[key]!!
    }

    override fun paint(): List<Drawable> {
        return nodes.flatMap { paintNode(it) }
    }

    private fun paintNode(node: Token): List<Drawable> {
        val cmds = mutableListOf<Drawable>()

        val bgColor = node.style["background-color"]?.let { getColor(it) }
        if (bgColor != null) {
            cmds.add(DrawRect(x, y, x + width, y + height, bgColor))
        }

        if (node is Element) {
            when {
                node.tag == "li" -> cmds.add(DrawRect(x, y - 4 + height / 2, x + 8, y + 4 + height / 2, Color.black))
            }
        }

        cmds.addAll(displayList)

        return cmds
    }

    override fun toString(): String {
        return "Block { x=$x, y=$y, width=$width, height=$height }"
    }
}

