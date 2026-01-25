package io.github.mpichler94.browser.layout

import io.github.mpichler94.browser.Element
import io.github.mpichler94.browser.Text
import io.github.mpichler94.browser.Token
import io.github.mpichler94.browser.render.Color
import io.github.mpichler94.browser.render.DrawRRect
import io.github.mpichler94.browser.render.DrawRect
import io.github.mpichler94.browser.render.Drawable

internal class BlockLayout(
    override val node: Token,
    private val parent: Layout,
    private val previous: Layout? = null,
) : Layout {
    override val children = mutableListOf<Layout>()
    override var x = 0f
        private set
    override var y = 0f
        private set
    override var width = 0f
        private set
    override var height = 0f
        private set

    override val pre: Boolean get() = (node is Element && node.tag == "pre") || parent.pre

    override val shouldPaint get() = node !is Element || (node.tag != "input" && node.tag != "button")

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
            if (child is Element && child.tag in listOf("head", "style", "script", "link")) {
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

        height = children.map { it.height }.sum()
    }

    private fun getToc(): Layout? =
        if (node is Element && node.tag == "nav" && node.attributes["id"] == "toc") {
            val token = Element("div", mutableMapOf("class" to "toc"), node)
            token.style.putAll(node.style)
            val toc = Text("Table of Contents", token)
            toc.style.putAll(node.style)
            token.children.add(toc)
            BlockLayout(token, this, null)
        } else {
            null
        }

    private fun getLiMarker(): Token? =
        if (node is Element && node.tag == "li") {
            val token = Element("li", mutableMapOf(), node)
            token.style.putAll(node.style)
            token
        } else {
            null
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
            val radius = node.style["border-radius"]?.removeSuffix("px")?.toIntOrNull() ?: 0
            cmds.add(DrawRRect(rect, radius.toFloat(), bgColor))
        }

        if (node is Element) {
            when {
                node.tag == "nav" && node.attributes["class"] == "links" ->
                    cmds.add(
                        DrawRect(rect, Color.makeGray(211)),
                    )

                node.tag == "div" && node.attributes["class"] == "toc" ->
                    cmds.add(
                        DrawRect(rect, Color.makeGray(128)),
                    )
            }
        }

        return cmds
    }

    override fun paintEffects(cmds: List<Drawable>): List<Drawable> = paintVisualEffects(node, cmds, rect)

    override fun toString(): String = "Block { x=$x, y=$y, width=$width, height=$height }"
}
