package io.github.mpichler94.browser.render

import io.github.humbleui.types.Point
import io.github.humbleui.types.Rect

fun Rect.contains(point: Point): Boolean = contains(point.x, point.y)

fun Rect.contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
