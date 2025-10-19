package io.github.mpichler94.browser

import java.awt.Color
import java.awt.Font
import java.awt.font.FontRenderContext

interface Layout {
    val x: Int
    val y: Int
    val width: Int
    val height: Int
    val children: List<Layout>

    fun layout()
    fun paint(): List<Drawable>
}

class BlockLayout(
    private val node: Token,
    private val parent: Layout,
    private val previous: Layout?
) : Layout {
    private val vStep = 50
    private val frc = FontRenderContext(null, true, true)
    private val blockElements = setOf(
        "html", "body", "article", "section", "nav", "aside", "h1", "h2", "h3", "h4", "h5", "h6", "hgroup", "header",
        "footer", "address", "p", "hr", "pre", "blockquote", "ol", "ul", "menu", "li", "dl", "dt", "dd", "figure",
        "figcaption", "main", "div", "table", "form", "fieldset", "legend", "details", "summary"
    )

    private val fontCache = mutableMapOf<FontKey, Font>()
    override val children = mutableListOf<Layout>()
    private var cursorX = 0
    private var cursorY = 0
    private var weight = Font.PLAIN
    private var style = Font.PLAIN
    private var size = 16
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
        x = parent.x
        width = parent.width
        y = previous?.let { it.y + it.height } ?: parent.y
        val mode = layoutMode()
        if (mode == LayoutType.BLOCK) {
            var previous: Layout? = getToc()
            for (child in node.children) {
                if (node is Element && node.tag == "head") {
                    continue
                }
                val next = BlockLayout(child, this, previous)
                children.add(next)
                previous = next
            }
        } else {
            cursorX = 0
            cursorY = 0
            weight = Font.PLAIN
            style = Font.PLAIN
            size = 16

            line = mutableListOf()
            recurse(node)
            flush()
        }

        for (child in children) {
            child.layout()
        }

        height = if (mode == LayoutType.BLOCK) {
            children.sumOf { it.height }
        } else {
            cursorY
        }
    }

    private fun getToc(): Layout? {
        return if (node is Element && node.tag == "nav" && node.attributes["id"] == "toc") {
            val token = Element("div", mapOf("class" to "toc"), node)
            val toc = Text("Table of Contents", token)
            token.children.add(toc)
            val layout = BlockLayout(token, this, null)
            children.add(layout)
            layout
        } else {
            null
        }
    }

    private fun layoutMode(): LayoutType {
        if (node is Text) {
            return LayoutType.INLINE
        } else if (node.children.any { it is Element && it.tag in blockElements }) {
            return LayoutType.BLOCK
        } else if (node.children.isNotEmpty()) {
            return LayoutType.INLINE
        } else {
            return LayoutType.BLOCK
        }
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
        if (cursorX + w > width) {
            flush()
        }

        val y = if (sup) cursorY - bounds.height.toInt() / 2 else cursorY
        line.add(DrawText(cursorX, y, word.trim(), font))
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

        val maxAscend = line.maxOfOrNull { it.font.getLineMetrics(it.text, frc).ascent.toInt() } ?: 0
        val baseline = cursorY + 1.25 * maxAscend
        val maxDescend = line.maxOfOrNull { it.font.getLineMetrics(it.text, frc).descent.toInt() } ?: 0

        cursorY = (baseline + 1.25 * maxDescend).toInt()
        cursorX = 0

        for (element in line) {
            val x = this.x + element.left
            val y = this.y + element.top
            displayList.add(DrawText(x, y, element.text, element.font))
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

    override fun paint(): List<Drawable> {
        val cmds = mutableListOf<Drawable>()

        if (node is Element) {
            when {
                node.tag == "pre" -> cmds.add(DrawRect(x, y, x + width, y + height, Color.gray))
                node.tag == "nav" && node.attributes["class"] == "links" -> cmds.add(
                    DrawRect(x, y, x + width, y + height, Color.lightGray)
                )

                node.tag == "li" -> cmds.add(DrawRect(x, y - 4 + height / 2, x + 8, y + 4 + height / 2, Color.black))
                node.tag == "div" && node.attributes["class"] == "toc" -> cmds.add(
                    DrawRect(x, y, x + width, y + height, Color.gray)
                )
            }
        }

        if (layoutMode() == LayoutType.INLINE) {
            if (node is Element && node.tag == "li") {
                cmds.addAll(displayList.map { it.withPos(it.left + 15, it.top) })
            } else {
                cmds.addAll(displayList)
            }
        }

        return cmds
    }

    override fun toString(): String {
        return "Block { x=$x, y=$y, width=$width, height=$height }"
    }
}

class DocumentLayout(private val node: Token, browserWidth: Int) : Layout {
    private val hStep = 13
    private val vStep = 50

    override val children = mutableListOf<Layout>()
    override var x: Int = hStep
        private set
    override var y: Int = vStep
        private set
    override var width: Int = browserWidth - 2 * hStep
        private set
    override var height: Int = 0
        private set

    override fun layout() {
        val child = BlockLayout(node, this, null)
        children.add(child)
        child.layout()
        height = child.height
    }

    override fun paint() = emptyList<Drawable>()

    override fun toString(): String {
        return "Document { x=$x, y=$y, width=$width, height=$height }"
    }
}

private data class FontKey(val font: String, val size: Int, val weight: Int, val style: Int)

private enum class LayoutType { BLOCK, INLINE }

fun Layout.paintTree(displayList: MutableList<Drawable> = mutableListOf()): List<Drawable> {
    displayList.addAll(paint())
    for (child in children) {
        child.paintTree(displayList)
    }
    return displayList
}

fun Layout.printTree(indent: Int = 0) {
    print(" ".repeat(indent))
    println(this)
    children.forEach { it.printTree(indent + 2) }
}
