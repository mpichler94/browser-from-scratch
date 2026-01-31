package io.github.mpichler94.browser.render

import io.github.humbleui.skija.Canvas
import io.github.humbleui.skija.Font
import io.github.humbleui.skija.FontMgr
import io.github.humbleui.skija.Paint
import io.github.humbleui.skija.PaintMode
import io.github.humbleui.skija.paragraph.FontCollection
import io.github.humbleui.skija.paragraph.Paragraph
import io.github.humbleui.skija.paragraph.ParagraphBuilder
import io.github.humbleui.skija.paragraph.ParagraphStyle
import io.github.humbleui.skija.paragraph.TextStyle
import io.github.humbleui.types.Rect

interface Drawable {
    val rect: Rect

    fun execute(canvas: Canvas, scale: Float)
}

class DrawText(
    x1: Float,
    y1: Float,
    val text: String,
    val font: Font,
    val color: Color,
) : Drawable {
    override val rect = Rect.makeXYWH(x1, y1, font.measureTextWidth(text), font.metrics.height)

    override fun execute(
        canvas: Canvas,
        scale: Float,
    ) {
        val paint = Paint()
        paint.color = color.value
        paint.isAntiAlias = true
        val baseline = rect.top - font.metrics.ascent
        font.setSubpixel(true)
        canvas.drawString(text, rect.left, baseline, font, paint)
    }
}

class DrawRect(override val rect: Rect, private val color: Color,
) : Drawable {
    constructor(left: Float, top: Float, right: Float, bottom: Float, color: Color): this(Rect.makeLTRB(left, top, right, bottom), color)

    override fun execute(
        canvas: Canvas,
        scale: Float,
    ) {
        val paint = Paint()
        paint.color = color.value
        paint.isAntiAlias = true
        canvas.drawRect(rect, paint)
    }
}

class DrawOutline(
    override val rect: Rect,
    private val color: Color,
    private val thickness: Float,
) : Drawable {
    override fun execute(
        canvas: Canvas,
        scale: Float,
    ) {
        val paint = Paint()
        paint.color = color.value
        paint.strokeWidth = thickness
        paint.mode = PaintMode.STROKE
        paint.isAntiAlias = true
        canvas.drawRect(rect, paint)
    }
}

class DrawLine(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    private val color: Color,
    private val thickness: Float,
) : Drawable {
    override val rect = Rect.makeLTRB(x1, y1, x2, y2)

    override fun execute(
        canvas: Canvas,
        scale: Float,
    ) {
        val paint = Paint()
        paint.color = color.value
        paint.strokeWidth = thickness
        paint.isAntiAlias = true
        val r = rect
        canvas.drawLine(r.left, r.top, r.right, r.bottom, paint)
    }
}

class DrawRRect(
    override val rect: Rect,
    private val radius: Float,
    private val color: Color,
) : Drawable {
    override fun execute(canvas: Canvas, scale: Float) {
        val paint = Paint()
        paint.color = color.value
        paint.isAntiAlias = true
        canvas.drawRRect(rect.withRadii(radius), paint)
    }
}

class DrawParagraph(
    x1: Float,
    y1: Float,
    val text: String,
    val style: TextStyle,
) : Drawable {
    override val rect: Rect
    private val paragraph: Paragraph

    init {
        val ps = ParagraphStyle()
        val fc = FontCollection()
        fc.setDefaultFontManager(FontMgr.getDefault())
        val pb = ParagraphBuilder(ps, fc)

        pb.pushStyle(style)
        pb.addText(text)

        paragraph = pb.build()
        paragraph.layout(Float.POSITIVE_INFINITY)
        rect = Rect.makeXYWH(x1, y1, paragraph.longestLine, paragraph.height)
    }

    override fun execute(
        canvas: Canvas,
        scale: Float,
    ) {
        paragraph.paint(canvas, rect.left, rect.top)
    }
}
