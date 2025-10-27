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
import java.awt.font.LineMetrics

private val fontCache = mutableMapOf<FontKey, Font>()

internal class InlineLayout(
    private val nodes: List<Token>,
    private val parent: Layout,
    private val previous: Layout?,
) : Layout {
    private val frc = FontRenderContext(null, true, true)

    override val children = mutableListOf<LineLayout>()
    private var cursorX = 0
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
    override val node: Token get() = nodes.first()

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

        newLine(nodes.first())
        nodes.forEach { recurse(it) }

        children.forEach { it.layout() }

        width = children.lastOrNull()?.width ?: parent.width
        height = children.sumOf { it.height }
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
        if (pre) {
            return word(node, node.text, space, font)
        }
        for (word in node.text.split(' ')) {
            word(node, if (abbr) word.uppercase() else word, space, font)
        }
    }

    private fun word(node: Text, word: String, space: Int, font: Font) {
        if (word.isBlank()) {
            return
        }

        val bounds = font.getStringBounds(word, frc)
        val w = bounds.width.toInt()
        if (cursorX + w > width) {
            newLine(node)
        }

        cursorX += w + space

        val line = children.last()
        line.addText(node, word)
    }

    private fun newLine(node: Token) {
        cursorX = 0
        val lastLine = children.lastOrNull()
        val newLine = LineLayout(node, this, lastLine)
        children.add(newLine)
    }

    private fun openTag(tag: Element) {
        when (tag.tag) {
            "br" -> newLine(tag)
            "h1" -> center = true
            "sup" -> sup = true
            "abbr" -> abbr = true
            "pre" -> pre = true
            "li" -> cursorX += 15
        }
    }

    private fun closeTag(tag: Element) {
        when (tag.tag) {
            "p" -> newLine(tag)

            "h1" -> {
                newLine(tag)
                center = false
            }

            "sup" -> sup = false
            "abbr" -> abbr = false
            "pre" -> pre = false
        }
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

        return cmds
    }

    override fun toString(): String {
        return "Inline { x=$x, y=$y, width=$width, height=$height }"
    }
}

internal class LineLayout(override val node: Token, val parent: InlineLayout, val previous: LineLayout?) : Layout {
    override var x: Int = 0
        private set
    override var y: Int = 0
        private set
    override var width: Int = 0
        private set
    override var height: Int = 0
        private set
    override val children: MutableList<TextLayout> = mutableListOf()

    override fun layout() {
        x = parent.x
        y = previous?.let { it.y + it.height } ?: parent.y

        children.forEach { it.layout() }

        val maxAscend = children.maxOfOrNull { it.lineMetrics.ascent.toInt() } ?: 0
        val maxDescend = children.maxOfOrNull { it.lineMetrics.descent.toInt() } ?: 0
        val baseline = y + 1.25 * maxAscend

        for (word in children) {
            if (node is Element && node.tag == "li") {
                word.x = word.x + 15
            }
            word.y = (baseline - word.lineMetrics.ascent).toInt()
        }

        width = children.sumOf { it.width }
        height = (1.25 * (maxAscend + maxDescend)).toInt()
    }

    fun addText(node: Text, word: String) {
        val text = TextLayout(node, word, this, children.lastOrNull())
        children.add(text)
    }

    override fun paint(): List<Drawable> {
        if (node is Element && node.tag == "li") {
            return listOf(DrawRect(x, y + height / 2 - 4, x + 8, y + height / 2 + 4, Color.black))
        }
        return emptyList()
    }

    override fun toString(): String {
        return "Line { x=$x, y=$y, width=$width, height=$height }"
    }
}

internal class TextLayout(
    override val node: Text,
    val word: String,
    val parent: LineLayout,
    val previous: TextLayout?
) :
    Layout {
    private val frc = FontRenderContext(null, true, true)
    private lateinit var font: Font
    override var x: Int = 0
    override var y: Int = 0
    override var width: Int = 0
        private set
    override var height: Int = 0
        private set
    override val children: List<Layout> = emptyList()

    lateinit var lineMetrics: LineMetrics
        private set


    override fun layout() {
        font = getFont(node.style)
        width = font.getStringBounds(word, frc).width.toInt()

        if (previous != null) {
            val space = font.getStringBounds(" ", frc).width.toInt()
            x = previous.x + previous.width + space
        } else {
            x = parent.x
        }

        lineMetrics = font.getLineMetrics(word, frc)
        height = lineMetrics.height.toInt()
    }

    override fun paint(): List<Drawable> {
        val color = node.style["color"]?.let { getColor(it) } ?: Color.BLACK
        return listOf(DrawText(x, y, word, font, color))
    }

    override fun toString(): String {
        return "Text { x=$x, y=$y, width=$width, height=$height }"
    }
}

private data class FontKey(val font: String, val size: Int, val weight: Int, val style: Int)

internal fun getFont(cssStyle: Map<String, String>): Font {
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