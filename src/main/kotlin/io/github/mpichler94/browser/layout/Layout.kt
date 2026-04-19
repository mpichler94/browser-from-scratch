package io.github.mpichler94.browser.layout

import io.github.humbleui.types.IRect
import io.github.humbleui.types.Rect
import io.github.mpichler94.browser.Token
import io.github.mpichler94.browser.render.Blend
import io.github.mpichler94.browser.render.Color
import io.github.mpichler94.browser.render.DrawRRect
import io.github.mpichler94.browser.render.Drawable

interface Layout {
    val x: Float
    val y: Float
    val width: Float
    val height: Float
    val node: Token
    val children: List<Layout>
    val rect: Rect get() = Rect.makeXYWH(x, y, width, height)

    val pre: Boolean get() = false

    val shouldPaint: Boolean get() = true

    fun layout()

    fun paint(): List<Drawable>

    fun paintEffects(cmds: List<Drawable>): List<Drawable> = cmds
}

internal class DocumentLayout(
    override val node: Token,
    browserWidth: Float,
) : Layout {
    private val hStep = 13f
    private val vStep = 50f

    override val children = mutableListOf<Layout>()
    override val x: Float = hStep
    override val y: Float = vStep
    override val width: Float = browserWidth - 2 * hStep
    override var height: Float = 0f
        private set

    override fun layout() {
        val child = BlockLayout(node, this)
        children.add(child)
        child.layout()
        height = child.height + 2 * vStep
    }

    override fun paint() = emptyList<Drawable>()

    override fun toString(): String = "Document { x=$x, y=$y, width=$width, height=$height }"
}

internal enum class LayoutType { BLOCK, INLINE }

fun Layout.paintTree(displayList: MutableList<Drawable> = mutableListOf()): List<Drawable> {
    var cmds = mutableListOf<Drawable>()
    if (shouldPaint) {
        cmds.addAll(paint())
    }
    for (child in children) {
        child.paintTree(cmds)
    }
    if (shouldPaint) {
        cmds = paintEffects(cmds).toMutableList()
    }
    displayList.addAll(cmds)
    return displayList
}

fun Layout.printTree(indent: Int = 0) {
    print(" ".repeat(indent))
    println(this)
    children.forEach { it.printTree(indent + 2) }
}

fun Layout.treeToList(): List<Layout> {
    val list = mutableListOf<Layout>()
    list.add(this)
    children.forEach { list.addAll(it.treeToList()) }
    return list
}

fun Layout.toList(): List<Layout> {
    val list = mutableListOf<Layout>()
    list.add(this)
    children.forEach { list.addAll(it.toList()) }
    return list
}

internal fun getColor(value: String): Color? {
    if (value.startsWith("#")) {
        if (value.length == 4) {
            val r = value[1].digitToInt(16)
            val g = value[2].digitToInt(16)
            val b = value[3].digitToInt(16)
            return Color.makeRGB(r * 16 + r, g * 16 + g, b * 16 + b)
        } else if (value.length == 5) {
            val r = value[1].digitToInt(16)
            val g = value[2].digitToInt(16)
            val b = value[3].digitToInt(16)
            val a = value[4].digitToInt(16)
            return Color.makeARGB(a * 16 + a, r * 16 + r, g * 16 + g, b * 16 + b)
        } else if (value.length == 7) {
            val r = value.substring(1, 3).toInt(16)
            val g = value.substring(3, 5).toInt(16)
            val b = value.substring(5, 7).toInt(16)
            return Color.makeRGB(r, g, b)
        } else {
            val r = value.substring(1, 3).toInt(16)
            val g = value.substring(3, 5).toInt(16)
            val b = value.substring(5, 7).toInt(16)
            val a = value.substring(7, 9).toInt(16)
            return Color.makeARGB(a, r, g, b)
        }
    } else {
        return htmlColorMap[value]
    }
}

internal fun paintVisualEffects(
    node: Token,
    commands: List<Drawable>,
    rect: Rect,
): List<Drawable> {
    val opacity = node.style["opacity"]?.toFloatOrNull() ?: 1f
    var blendMode = node.style["mix-blend-mode"]
    if (commands.isEmpty()) {
        return listOf()
    }

    val cmds = commands.toMutableList()
    if (node.style.getOrElse("overflow") { "visible" } == "clip") {
        if (blendMode == null) {
            blendMode = "source-over"
        }
        val borderRadius = node.style["border-radius"]?.dropLast(2)?.toIntOrNull() ?: 0
        cmds.add(Blend(1f, "destination-in", null, listOf(DrawRRect(rect, borderRadius.toFloat(), Color.WHITE))))
    }

    val filter = node.style["filter"]
    return listOf(Blend(opacity, blendMode, filter, cmds))
}

// <editor-fold desc="HTML Color map">
private val htmlColorMap =
    mapOf(
        "aliceblue" to Color.makeRGB(240, 248, 255),
        "antiquewhite" to Color.makeRGB(250, 235, 215),
        "aqua" to Color.makeRGB(0, 255, 255),
        "aquamarine" to Color.makeRGB(127, 255, 212),
        "azure" to Color.makeRGB(240, 255, 255),
        "beige" to Color.makeRGB(245, 245, 220),
        "bisque" to Color.makeRGB(255, 228, 196),
        "black" to Color.makeRGB(0, 0, 0),
        "blanchedalmond" to Color.makeRGB(255, 235, 205),
        "blue" to Color.makeRGB(0, 0, 255),
        "blueviolet" to Color.makeRGB(138, 43, 226),
        "brown" to Color.makeRGB(165, 42, 42),
        "burlywood" to Color.makeRGB(222, 184, 135),
        "cadetblue" to Color.makeRGB(95, 158, 160),
        "chartreuse" to Color.makeRGB(127, 255, 0),
        "chocolate" to Color.makeRGB(210, 105, 30),
        "coral" to Color.makeRGB(255, 127, 80),
        "cornflowerblue" to Color.makeRGB(100, 149, 237),
        "cornsilk" to Color.makeRGB(255, 248, 220),
        "crimson" to Color.makeRGB(220, 20, 60),
        "cyan" to Color.makeRGB(0, 255, 255),
        "darkblue" to Color.makeRGB(0, 0, 139),
        "darkcyan" to Color.makeRGB(0, 139, 139),
        "darkgoldenrod" to Color.makeRGB(184, 134, 11),
        "darkgray" to Color.makeRGB(169, 169, 169),
        "darkgreen" to Color.makeRGB(0, 100, 0),
        "darkgrey" to Color.makeRGB(169, 169, 169),
        "darkkhaki" to Color.makeRGB(189, 183, 107),
        "darkmagenta" to Color.makeRGB(139, 0, 139),
        "darkolivegreen" to Color.makeRGB(85, 107, 47),
        "darkorange" to Color.makeRGB(255, 140, 0),
        "darkorchid" to Color.makeRGB(153, 50, 204),
        "darkred" to Color.makeRGB(139, 0, 0),
        "darksalmon" to Color.makeRGB(233, 150, 122),
        "darkseagreen" to Color.makeRGB(143, 188, 143),
        "darkslateblue" to Color.makeRGB(72, 61, 139),
        "darkslategray" to Color.makeRGB(47, 79, 79),
        "darkslategrey" to Color.makeRGB(47, 79, 79),
        "darkturquoise" to Color.makeRGB(0, 206, 209),
        "darkviolet" to Color.makeRGB(148, 0, 211),
        "deeppink" to Color.makeRGB(255, 20, 147),
        "deepskyblue" to Color.makeRGB(0, 191, 255),
        "dimgray" to Color.makeRGB(105, 105, 105),
        "dimgrey" to Color.makeRGB(105, 105, 105),
        "dodgerblue" to Color.makeRGB(30, 144, 255),
        "firebrick" to Color.makeRGB(178, 34, 34),
        "floralwhite" to Color.makeRGB(255, 250, 240),
        "forestgreen" to Color.makeRGB(34, 139, 34),
        "fuchsia" to Color.makeRGB(255, 0, 255),
        "gainsboro" to Color.makeRGB(220, 220, 220),
        "ghostwhite" to Color.makeRGB(248, 248, 255),
        "gold" to Color.makeRGB(255, 215, 0),
        "goldenrod" to Color.makeRGB(218, 165, 32),
        "gray" to Color.makeRGB(128, 128, 128),
        "green" to Color.makeRGB(0, 128, 0),
        "greenyellow" to Color.makeRGB(173, 255, 47),
        "grey" to Color.makeRGB(128, 128, 128),
        "honeydew" to Color.makeRGB(240, 255, 240),
        "hotpink" to Color.makeRGB(255, 105, 180),
        "indianred" to Color.makeRGB(205, 92, 92),
        "indigo" to Color.makeRGB(75, 0, 130),
        "ivory" to Color.makeRGB(255, 255, 240),
        "khaki" to Color.makeRGB(240, 230, 140),
        "lavender" to Color.makeRGB(230, 230, 250),
        "lavenderblush" to Color.makeRGB(255, 240, 245),
        "lawngreen" to Color.makeRGB(124, 252, 0),
        "lemonchiffon" to Color.makeRGB(255, 250, 205),
        "lightblue" to Color.makeRGB(173, 216, 230),
        "lightcoral" to Color.makeRGB(240, 128, 128),
        "lightcyan" to Color.makeRGB(224, 255, 255),
        "lightgoldenrodyellow" to Color.makeRGB(250, 250, 210),
        "lightgray" to Color.makeRGB(211, 211, 211),
        "lightgreen" to Color.makeRGB(144, 238, 144),
        "lightgrey" to Color.makeRGB(211, 211, 211),
        "lightpink" to Color.makeRGB(255, 182, 193),
        "lightsalmon" to Color.makeRGB(255, 160, 122),
        "lightseagreen" to Color.makeRGB(32, 178, 170),
        "lightskyblue" to Color.makeRGB(135, 206, 250),
        "lightslategray" to Color.makeRGB(119, 136, 153),
        "lightslategrey" to Color.makeRGB(119, 136, 153),
        "lightsteelblue" to Color.makeRGB(176, 196, 222),
        "lightyellow" to Color.makeRGB(255, 255, 224),
        "lime" to Color.makeRGB(0, 255, 0),
        "limegreen" to Color.makeRGB(50, 205, 50),
        "linen" to Color.makeRGB(250, 240, 230),
        "magenta" to Color.makeRGB(255, 0, 255),
        "maroon" to Color.makeRGB(128, 0, 0),
        "mediumaquamarine" to Color.makeRGB(102, 205, 170),
        "mediumblue" to Color.makeRGB(0, 0, 205),
        "mediumorchid" to Color.makeRGB(186, 85, 211),
        "mediumpurple" to Color.makeRGB(147, 112, 219),
        "mediumseagreen" to Color.makeRGB(60, 179, 113),
        "mediumslateblue" to Color.makeRGB(123, 104, 238),
        "mediumspringgreen" to Color.makeRGB(0, 250, 154),
        "mediumturquoise" to Color.makeRGB(72, 209, 204),
        "mediumvioletred" to Color.makeRGB(199, 21, 133),
        "midnightblue" to Color.makeRGB(25, 25, 112),
        "mintcream" to Color.makeRGB(245, 255, 250),
        "mistyrose" to Color.makeRGB(255, 228, 225),
        "moccasin" to Color.makeRGB(255, 228, 181),
        "navajowhite" to Color.makeRGB(255, 222, 173),
        "navy" to Color.makeRGB(0, 0, 128),
        "oldlace" to Color.makeRGB(253, 245, 230),
        "olive" to Color.makeRGB(128, 128, 0),
        "olivedrab" to Color.makeRGB(107, 142, 35),
        "orange" to Color.makeRGB(255, 165, 0),
        "orangered" to Color.makeRGB(255, 69, 0),
        "orchid" to Color.makeRGB(218, 112, 214),
        "palegoldenrod" to Color.makeRGB(238, 232, 170),
        "palegreen" to Color.makeRGB(152, 251, 152),
        "paleturquoise" to Color.makeRGB(175, 238, 238),
        "palevioletred" to Color.makeRGB(219, 112, 147),
        "papayawhip" to Color.makeRGB(255, 239, 213),
        "peachpuff" to Color.makeRGB(255, 218, 185),
        "peru" to Color.makeRGB(205, 133, 63),
        "pink" to Color.makeRGB(255, 192, 203),
        "plum" to Color.makeRGB(221, 160, 221),
        "powderblue" to Color.makeRGB(176, 224, 230),
        "purple" to Color.makeRGB(128, 0, 128),
        "red" to Color.makeRGB(255, 0, 0),
        "rosybrown" to Color.makeRGB(188, 143, 143),
        "royalblue" to Color.makeRGB(65, 105, 225),
        "saddlebrown" to Color.makeRGB(139, 69, 19),
        "salmon" to Color.makeRGB(250, 128, 114),
        "sandybrown" to Color.makeRGB(244, 164, 96),
        "seagreen" to Color.makeRGB(46, 139, 87),
        "seashell" to Color.makeRGB(255, 245, 238),
        "sienna" to Color.makeRGB(160, 82, 45),
        "silver" to Color.makeRGB(192, 192, 192),
        "skyblue" to Color.makeRGB(135, 206, 235),
        "slateblue" to Color.makeRGB(106, 90, 205),
        "slategray" to Color.makeRGB(112, 128, 144),
        "slategrey" to Color.makeRGB(112, 128, 144),
        "snow" to Color.makeRGB(255, 250, 250),
        "springgreen" to Color.makeRGB(0, 255, 127),
        "steelblue" to Color.makeRGB(70, 130, 180),
        "tan" to Color.makeRGB(210, 180, 140),
        "teal" to Color.makeRGB(0, 128, 128),
        "thatch" to Color.makeRGB(255, 99, 71),
        "thistle" to Color.makeRGB(216, 191, 216),
        "tomato" to Color.makeRGB(255, 99, 71),
        "turquoise" to Color.makeRGB(64, 224, 208),
        "violet" to Color.makeRGB(238, 130, 238),
        "wheat" to Color.makeRGB(245, 222, 179),
        "white" to Color.makeRGB(255, 255, 255),
        "whitesmoke" to Color.makeRGB(245, 245, 245),
        "yellow" to Color.makeRGB(255, 255, 0),
        "yellowgreen" to Color.makeRGB(154, 205, 50),
    )
// </editor-fold>
