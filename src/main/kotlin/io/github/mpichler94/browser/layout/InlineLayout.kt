package io.github.mpichler94.browser.layout

import io.github.humbleui.skija.Font
import io.github.humbleui.skija.FontMetrics
import io.github.humbleui.skija.FontMgr
import io.github.humbleui.skija.FontSlant
import io.github.humbleui.skija.FontStyle
import io.github.humbleui.skija.FontWeight
import io.github.humbleui.skija.FontWidth
import io.github.humbleui.skija.Typeface
import io.github.humbleui.types.Rect
import io.github.mpichler94.browser.Element
import io.github.mpichler94.browser.Text
import io.github.mpichler94.browser.Token
import io.github.mpichler94.browser.render.Color
import io.github.mpichler94.browser.render.DrawRRect
import io.github.mpichler94.browser.render.DrawRect
import io.github.mpichler94.browser.render.DrawText
import io.github.mpichler94.browser.render.Drawable

private val fontCache = mutableMapOf<FontKey, Typeface>()

internal class InlineLayout(
    private val nodes: List<Token>,
    private val parent: Layout,
    private val previous: Layout?,
) : Layout {
    override val children = mutableListOf<LineLayout>()
    private var cursorX = 0f
    private var center = false
    private var sup = false
    private var abbr = false

    override var x = 0f
        private set
    override var y = 0f
        private set
    override var width = 0f
        private set
    override var height = 0f
        private set
    override val node: Token get() = nodes.first()

    override val pre: Boolean get() {
        val node = nodes.first()
        return (node is Element && node.tag == "pre") || parent.pre
    }

    override fun layout() {
        if (previous is InlineLayout) {
            x = previous.x + previous.width
            y = previous.y
        } else {
            x = parent.x
            y = previous?.let { it.y + it.height } ?: parent.y
        }

        width = parent.width
        cursorX = 0f

        newLine(nodes.first())
        nodes.forEach { recurse(it) }

        children.forEach { it.layout() }

        width = children.lastOrNull()?.width ?: parent.width
        height = children.map { it.height }.sum()
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
        val space = font.measureTextWidth(" ")
        if (pre) {
            node.text.lines().forEachIndexed { i, line ->
                if (i > 0) {
                    newLine(node)
                }
                word(node, line, space, font)
            }
            if (node.text.endsWith('\n')) {
                newLine(node)
            }
            return
        }
        for (word in node.text.split(' ')) {
            word(node, if (abbr) word.uppercase().trim() else word.trim(), space, font)
        }
    }

    private fun word(node: Text, word: String, space: Float, font: Font) {
        if (word.isBlank()) {
            return
        }

        val w = font.measureTextWidth(word)
        if (cursorX + w > width) {
            newLine(node)
        }

        cursorX += w + space

        val line = children.last()
        line.addText(node, word)
    }

    private fun newLine(node: Token) {
        cursorX = 0f
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
        }
    }

    override fun paint(): List<Drawable> = nodes.flatMap { paintNode(it) }

    private fun paintNode(node: Token): List<Drawable> =
        buildList {
            val bgColor = node.style["background-color"]?.let { getColor(it) }
            if (bgColor != null) {
                val radius = node.style["border-radius"]?.removeSuffix("px")?.toIntOrNull() ?: 0
                add(DrawRRect(rect, radius.toFloat(), bgColor))
            }
        }

    override fun toString(): String = "Inline { x=$x, y=$y, width=$width, height=$height }"
}

internal class LineLayout(
    override val node: Token,
    val parent: InlineLayout,
    val previous: LineLayout?,
) : Layout {
    override var x = 0f
        private set
    override var y = 0f
        private set
    override var width = 0f
        private set
    override var height = 0f
        private set
    override val children: MutableList<TextLayout> = mutableListOf()

    override fun layout() {
        x = parent.x
        y = previous?.let { it.y + it.height } ?: parent.y

        children.forEach { it.layout() }

        val maxAscend = children.maxOfOrNull { -it.fontMetrics.ascent } ?: 0f
        val maxDescend = children.maxOfOrNull { it.fontMetrics.descent } ?: 0f
        val baseline = y + 1.25f * maxAscend

        for (word in children) {
            if (node is Element && node.tag == "li") {
                word.x += 15
            }
            word.y = baseline + word.fontMetrics.ascent
        }

        width = children.map { it.width }.sum()
        height = 1.25f * (maxAscend + maxDescend)
    }

    fun addText(node: Text, word: String) {
        val text = TextLayout(node, word, this, children.lastOrNull())
        children.add(text)
    }

    override fun paint(): List<Drawable> {
        if (node is Element && node.tag == "li") {
            return listOf(DrawRect(Rect.makeXYWH(x, y + height / 2f - 4f, 8f, 8f), Color.BLACK))
        }
        return emptyList()
    }

    override fun toString(): String = "Line { x=$x, y=$y, width=$width, height=$height }"
}

internal class TextLayout(
    override val node: Text,
    val word: String,
    val parent: LineLayout,
    val previous: TextLayout?,
) : Layout {
    private lateinit var font: Font
    override var x = 0f
    override var y = 0f
    override var width = 0f
        private set
    override var height = 0f
        private set
    override val children: List<Layout> = emptyList()

    lateinit var fontMetrics: FontMetrics
        private set

    override fun layout() {
        font = getFont(node.style)
        width = font.measureTextWidth(word)

        if (previous != null) {
            val space = font.measureTextWidth(" ")
            x = previous.x + previous.width + space
        } else {
            x = parent.x
        }

        fontMetrics = font.metrics
        height = fontMetrics.height
    }

    override fun paint(): List<Drawable> {
        val color = node.style["color"]?.let { getColor(it) } ?: Color.BLACK
        return listOf(DrawText(x, y, word, font, color))
    }

    override fun toString(): String = "Text { x=$x, y=$y, width=$width, height=$height }"
}

private data class FontKey(
    val families: List<String>,
    val size: Float,
    val weight: Int,
    val slant: FontSlant,
)

internal fun getFont(cssStyle: Map<String, String>): Font {
    val weight = when (cssStyle["font-weight"]) {
        "bold" -> FontWeight.BOLD
        else -> FontWeight.NORMAL
    }
    val slant = when (cssStyle["font-style"]) {
        "italic" -> FontSlant.ITALIC
        else -> FontSlant.UPRIGHT
    }
    val size = (cssStyle["font-size"]?.dropLast(2)?.toIntOrNull() ?: 16).toFloat() * 0.75f

    val fontFamilies = (cssStyle["font-family"]?.split(',') ?: listOf("sans-serif"))
        .map { it.trim().trim('\'', '"') }
        .map {
            when (it.lowercase()) {
                "serif" -> "Times New Roman"
                "sans-serif" -> "Segoe UI"
                "monospace" -> "Consolas"
                else -> it
            }
        }

    val key = FontKey(fontFamilies, size, weight, slant)

    if (key !in fontCache) {
        val style = FontStyle(weight, FontWidth.NORMAL, slant)
        val font = FontMgr.getDefault().matchFamiliesStyle(fontFamilies.toTypedArray(), style)
            ?: FontMgr.getDefault().matchFamilyStyle("Segoe UI", style)!!
        fontCache[key] = font
        return Font(font, size)
    }
    return Font(fontCache[key]!!, size)
}
