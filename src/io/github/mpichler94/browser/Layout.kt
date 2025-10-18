package io.github.mpichler94.browser

import java.awt.Font
import java.awt.font.FontRenderContext

class Layout(tree: Token, private val width: Int) {

    private val hStep = 13
    private val vStep = 50
    private val frc = FontRenderContext(null, true, true)

    private val fontCache = mutableMapOf<FontKey, Font>()
    private var cursorX = hStep
    private var cursorY = vStep
    private var weight = Font.PLAIN
    private var style = Font.PLAIN
    private var size = 16
    private var line = mutableListOf<DrawableWord>()
    private var center = false
    private var sup = false
    private var abbr = false
    private var pre = false

    private var _displayList = mutableListOf<DrawableWord>()
    val displayList: List<DrawableWord> get() = _displayList
    var height = 0
        private set

    init {
        recurse(tree)
        flush()
        height = cursorY + vStep
    }

    private fun recurse(tree: Token) {
        if (tree is Text) {
            val font = getFont()
            val space = font.getStringBounds(" ", frc).width.toInt()
            if (pre) {
                word(tree.text, space, font)
                return
            }
            for (word in tree.text.split(' ')) {
                word(if (abbr) word.uppercase() else word, space, font)
            }
        } else if (tree is Element) {
            openTag(tree)
            for (child in tree.children) {
                recurse(child)
            }
            closeTag(tree)
        }
    }

    private fun word(word: String, space: Int, font: Font) {
        if (word.isBlank()) {
            return
        }
        val bounds = font.getStringBounds(word, frc)
        val w = bounds.width.toInt()
        if (cursorX + w > width - hStep) {
            flush()
        }

        val y = if (sup) cursorY - bounds.height.toInt() / 2 else cursorY
        line.add(DrawableWord(cursorX, y, word.trim(), font))
        cursorX += w + space
    }

    private fun openTag(tag: Element) {
        when (tag.tag) {
            "i" -> style = Font.ITALIC
            "b" -> weight = Font.BOLD
            "small" -> size -= 2
            "big" -> size += 2
            "br" -> flush()
            "h1" -> center = true

            "sup" -> sup = true
            "abbr" -> abbr = true
            "pre" -> pre = true
        }
    }

    private fun closeTag(tag: Element) {
        when (tag.tag) {
            "i" -> style = Font.PLAIN
            "b" -> weight = Font.PLAIN
            "small" -> size += 2
            "big" -> size -= 2
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

        val maxAscend = line.maxOfOrNull { it.font.getLineMetrics(it.word, frc).ascent.toInt() } ?: 0
        val baseline = cursorY + 1.25 * maxAscend
        val maxDescend = line.maxOfOrNull { it.font.getLineMetrics(it.word, frc).descent.toInt() } ?: 0

        cursorY = (baseline + 1.25 * maxDescend).toInt()
        cursorX = hStep

        if (center) {
            val minX = line.firstOrNull()?.let { it.x } ?: 0
            val maxX = line.lastOrNull()?.let { it.x + it.font.getStringBounds(it.word, frc).width.toInt() } ?: 0
            val lineWidth = maxX - minX
            val startX = (width - lineWidth) / 2
            _displayList.addAll(line.map { DrawableWord(startX + it.x, it.y, it.word, it.font) })
        } else {
            _displayList.addAll(line)
        }

        line.clear()
    }

    private fun getFont(): Font {
        val key = when {
            sup -> FontKey(Font.SANS_SERIF, size / 2, weight, style)
            abbr -> FontKey(Font.SANS_SERIF, size - 2, Font.BOLD, style)
            pre -> FontKey(Font.MONOSPACED, size, weight, style)
            else -> FontKey(Font.SANS_SERIF, size, weight, style)
        }

        if (key !in fontCache) {
            val font = Font(key.font, key.weight + key.style, key.size)
            fontCache[key] = font
            return font
        }
        return fontCache[key]!!
    }
}

private data class FontKey(val font: String, val size: Int, val weight: Int, val style: Int)
