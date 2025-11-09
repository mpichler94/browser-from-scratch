package io.github.mpichler94.browser.layout

import io.github.mpichler94.browser.DrawRect
import io.github.mpichler94.browser.Drawable
import io.github.mpichler94.browser.Element
import io.github.mpichler94.browser.Text
import io.github.mpichler94.browser.Token
import java.awt.Color

internal class BlockLayout(
    override val node: Token,
    private val parent: Layout,
    private val previous: Layout? = null,
) : Layout {
    override val children = mutableListOf<Layout>()
    override var x = 0
        private set
    override var y = 0
        private set
    override var width = 0
        private set
    override var height = 0
        private set

    override fun layout() {
        x = parent.x
        width = parent.width
        y = previous?.let { it.y + it.height } ?: parent.y

        val inline = mutableListOf<Token>()
        var previous: Layout? = getToc()
        previous?.let { children.add(it) }
        val marker = getLiMarker()
        marker?.let { inline.add(it) }

        for (child in node.children) {
            if (child is Element && child.tag in listOf("head", "style", "script")) {
                continue
            }

            if (layoutMode(child) == LayoutType.BLOCK) {
                if (inline.isNotEmpty()) {
                    val next = InlineLayout(inline.toList(), this, previous)
                    inline.clear()
                    children.add(next)
                    previous = next
                }
                val next = BlockLayout(child, this, previous)
                children.add(next)
                previous = next
            } else if (child is Element && child.tag in listOf("input", "button")) {
                if (inline.isNotEmpty()) {
                    val next = InlineLayout(inline.toList(), this, previous)
                    inline.clear()
                    children.add(next)
                    previous = next
                }
                val next = InputLayout(child, this, previous)
                children.add(next)
                previous = next
            } else {
                inline.add(child)
            }
        }

        if (inline.isNotEmpty()) {
            val next = InlineLayout(inline, this, previous)
            children.add(next)
        }

        for (child in children) {
            child.layout()
        }

        height = children.sumOf { it.height }
    }

    private fun getToc(): Layout? {
        return if (node is Element && node.tag == "nav" && node.attributes["id"] == "toc") {
            val token = Element("div", mutableMapOf("class" to "toc"), node)
            token.style.putAll(node.style)
            val toc = Text("Table of Contents", token)
            toc.style.putAll(node.style)
            token.children.add(toc)
            BlockLayout(token, this, null)
        } else {
            null
        }
    }

    private fun getLiMarker(): Token? {
        return if (node is Element && node.tag == "li") {
            val token = Element("li", mutableMapOf(), node)
            token.style.putAll(node.style)
            token
        } else {
            null
        }
    }

    private fun layoutMode(node: Token): LayoutType {
        if (node is Text) {
            return LayoutType.INLINE
        } else if (node.style["display"] == "inline") {
            return LayoutType.INLINE
        } else if (node.style["display"] == "block" || node.children.any { it.style["display"] == "block" }) {
            return LayoutType.BLOCK
        } else {
            return LayoutType.INLINE
        }
    }

    override fun paint(): List<Drawable> {
        val cmds = mutableListOf<Drawable>()

        val bgColor = node.style["background-color"]?.let { getColor(it) }
        if (bgColor != null) {
            cmds.add(DrawRect(x, y, x + width, y + height, bgColor))
        }

        if (node is Element) {
            when {
                node.tag == "nav" && node.attributes["class"] == "links" -> cmds.add(
                    DrawRect(x, y, x + width, y + height, Color.lightGray)
                )

                node.tag == "div" && node.attributes["class"] == "toc" -> cmds.add(
                    DrawRect(x, y, x + width, y + height, Color.gray)
                )
            }
        }

        return cmds
    }

    override fun toString(): String {
        return "Block { x=$x, y=$y, width=$width, height=$height }"
    }
}