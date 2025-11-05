package io.github.mpichler94.browser

import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.font.FontRenderContext

interface Drawable {
    val top: Int
    val left: Int
    val bottom: Int
    val right: Int

    fun execute(graphics: Graphics, scroll: Int)
}

class DrawText(x1: Int, y1: Int, val text: String, val font: Font, val color: Color) : Drawable {
    override val top = y1
    override val left = x1
    override val bottom = y1 + font.getLineMetrics(text, frc).height.toInt()
    override val right = y1 + font.getStringBounds(text, frc).width.toInt()

    companion object {
        private val frc = FontRenderContext(null, true, true)
    }

    override fun execute(graphics: Graphics, scroll: Int) {
        graphics.color = color
        graphics.font = font
        graphics.drawString(text, left, top - scroll + font.getLineMetrics(text, frc).ascent.toInt())
    }
}

class DrawRect(
    override val left: Int,
    override val top: Int,
    override val right: Int,
    override val bottom: Int,
    private val color: Color
) : Drawable {
    override fun execute(graphics: Graphics, scroll: Int) {
        graphics.color = color
        graphics.fillRect(left, top - scroll, right - left, bottom - top)
    }
}

class DrawOutline(rect: Rectangle, private val color: Color, private val thickness: Int) : Drawable {
    override val top = rect.top
    override val left = rect.left
    override val bottom = rect.bottom
    override val right = rect.right

    override fun execute(graphics: Graphics, scroll: Int) {
        graphics.color = color
        graphics.drawRect(left, top - scroll, right - left, bottom - top)
    }
}

class DrawLine(x1: Int, y1: Int, x2: Int, y2: Int, private val color: Color, private val thickness: Int) : Drawable {
    override val top = y1
    override val left = x1
    override val bottom = y2
    override val right = x2

    override fun execute(graphics: Graphics, scroll: Int) {
        graphics.color = color
        graphics.drawLine(left, top - scroll, right, bottom - scroll)
    }
}