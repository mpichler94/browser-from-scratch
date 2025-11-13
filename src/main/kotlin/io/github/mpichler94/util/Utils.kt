package io.github.mpichler94.util

fun Float.coerceIn(minimumValue: Float, maximumValue: Float): Float {
    require(minimumValue <= maximumValue) { "Cannot coerce value to an empty range: maximum $maximumValue is less than minimum $minimumValue." }
    if (this < minimumValue) return minimumValue
    if (this > maximumValue) return maximumValue
    return this
}
