package io.github.mpichler94.browser

import java.awt.Font
import java.awt.font.FontRenderContext

class Layout(tokens: List<Token>, private val width: Int) {

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
        for (token in tokens) {
            token(token)
        }
        flush()
        height = cursorY + vStep
    }

    private fun token(token: Token) {
        if (token is Text) {
            val font = getFont()
            val space = font.getStringBounds(" ", frc).width.toInt()
            if (pre) {
                word(token.text, space, font)
                return
            }
            for (word in token.text.split(' ')) {
                word(if (abbr) word.uppercase() else word, space, font)
            }
        } else if (token is Tag) {
            tag(token)
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

    private fun tag(tag: Tag) {
        when (tag.tag) {
            "i" -> style = Font.ITALIC
            "/i" -> style = Font.PLAIN
            "b" -> weight = Font.BOLD
            "/b" -> weight = Font.PLAIN
            "small" -> size -= 2
            "/small" -> size += 2
            "big" -> size += 2
            "/big" -> size -= 2
            "br" -> flush()
            "/p" -> {
                flush()
                cursorY += vStep
            }

            "/h1" -> {
                flush()
                center = false
            }

            "sup" -> sup = true
            "/sup" -> sup = false
            "abbr" -> abbr = true
            "/abbr" -> abbr = false
            "/pre" -> pre = false
        }
        if (tag.tag.startsWith("h1")) {
            center = true
        } else if (tag.tag.startsWith("pre")) {
            pre = true
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