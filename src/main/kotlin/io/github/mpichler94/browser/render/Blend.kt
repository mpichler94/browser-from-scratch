package io.github.mpichler94.browser.render

import io.github.humbleui.skija.BlendMode
import io.github.humbleui.skija.Canvas
import io.github.humbleui.skija.FilterTileMode
import io.github.humbleui.skija.ImageFilter
import io.github.humbleui.skija.Paint
import io.github.humbleui.types.Rect
import kotlin.math.max
import kotlin.math.min

class Blend(private val opacity: Float, private val blendMode: String?, private val filter: String?, private val children: List<Drawable>): Drawable {
    override val rect: Rect

    init {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = 0f
        var bottom = 0f
        children.forEach {
            left = min(left, it.rect.left)
            top = min(top, it.rect.top)
            right = max(right, it.rect.right)
            bottom = max(bottom, it.rect.bottom)
        }

        rect = Rect.makeLTRB(left, top, right, bottom)
    }

    override fun execute(canvas: Canvas, scale: Float) {
        val type = filter?.substringBefore('(')
        val params = filter?.substringAfter('(')?.substringBefore(')')
        val paint = Paint().setBlendMode(parseBlendMode(blendMode)).setAlphaf(opacity).setAntiAlias(true)
        if (type == "blur") {
            val radius = params?.dropLast(2)?.toIntOrNull() ?: 1
            val sigma = (2f * radius - 1f) / 4f
            paint.imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL)
        }

        val shouldSave = opacity < 1 || blendMode != null || filter != null

        if (shouldSave) {
            canvas.saveLayer(rect, paint)
        }
        children.forEach { it.execute(canvas, scale) }
        if (shouldSave) {
            canvas.restore()
        }
    }

    private fun parseBlendMode(blendMode: String?): BlendMode {
        return when (blendMode) {
            "multiply" -> BlendMode.MULTIPLY
            "difference" -> BlendMode.DIFFERENCE
            "destination-in" -> BlendMode.DST_IN
            "source-over" -> BlendMode.SRC_OVER
            else -> BlendMode.SRC_OVER
        }
    }
}
