package io.github.mpichler94.browser.render

@JvmInline
value class Color(val value: Int) {
    companion object {
        val TRANSPARENT = Color(0)
        val WHITE = makeGray(255)
        val BLACK = makeGray(0)
        val RED = makeRGB(255, 0, 0)

        fun makeARGB(a: Int, r: Int, g: Int, b: Int): Color {
            assert(a in 0..255) { "Alpha is out of 0..255 range: $a" }
            assert(r in 0..255) { "Red is out of 0..255 range: $r" }
            assert(g in 0..255) { "Green is out of 0..255 range: $g" }
            assert(b in 0..255) { "Blue is out of 0..255 range: $b" }
            return Color(((a and 0xFF) shl 24)
                    or ((r and 0xFF) shl 16)
                    or ((g and 0xFF) shl 8)
                    or (b and 0xFF))
        }

        fun makeRGB(r: Int, g: Int, b: Int): Color = makeARGB(255, r, g, b)
        fun makeGray(v: Int): Color = makeRGB(v, v, v)
    }
}
