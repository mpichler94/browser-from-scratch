package io.github.mpichler94.browser.layout

import io.github.mpichler94.browser.Drawable
import io.github.mpichler94.browser.Token
import java.awt.Color

interface Layout {
    val x: Int
    val y: Int
    val width: Int
    val height: Int
    val children: List<Layout>

    fun layout()
    fun paint(): List<Drawable>
}

internal class DocumentLayout(private val node: Token, browserWidth: Int) : Layout {
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
        val child = BlockLayout(node, this)
        children.add(child)
        child.layout()
        height = child.height
    }

    override fun paint() = emptyList<Drawable>()

    override fun toString(): String {
        return "Document { x=$x, y=$y, width=$width, height=$height }"
    }
}

internal data class FontKey(val font: String, val size: Int, val weight: Int, val style: Int)

internal enum class LayoutType { BLOCK, INLINE }

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

fun Layout.treeToList(): List<Layout> {
    val list = mutableListOf<Layout>()
    list.add(this)
    children.forEach { list.addAll(it.treeToList()) }
    return list
}

internal fun getColor(value: String): Color {
    if (value.startsWith("#")) {
        if (value.length == 4) {
            val r = value[1].digitToInt(16)
            val g = value[2].digitToInt(16)
            val b = value[3].digitToInt(16)
            return Color(r * 16 + r, g * 16 + g, b * 16 + b)
        } else if (value.length == 5) {
            val r = value[1].digitToInt(16)
            val g = value[2].digitToInt(16)
            val b = value[3].digitToInt(16)
            val a = value[4].digitToInt(16)
            return Color(r * 16 + r, g * 16 + g, b * 16 + b, a * 16 + a)
        } else if (value.length == 7) {
            return Color.decode(value)
        } else {
            return Color.decode(value.drop(1).take(6))
        }
    } else {
        return htmlColorMap[value] ?: Color.decode(value)
    }
}

private val htmlColorMap = mapOf(
    "aliceblue" to Color(240, 248, 255),
    "antiquewhite" to Color(250, 235, 215),
    "aqua" to Color(0, 255, 255),
    "aquamarine" to Color(127, 255, 212),
    "azure" to Color(240, 255, 255),
    "beige" to Color(245, 245, 220),
    "bisque" to Color(255, 228, 196),
    "black" to Color(0, 0, 0),
    "blanchedalmond" to Color(255, 235, 205),
    "blue" to Color(0, 0, 255),
    "blueviolet" to Color(138, 43, 226),
    "brown" to Color(165, 42, 42),
    "burlywood" to Color(222, 184, 135),
    "cadetblue" to Color(95, 158, 160),
    "chartreuse" to Color(127, 255, 0),
    "chocolate" to Color(210, 105, 30),
    "coral" to Color(255, 127, 80),
    "cornflowerblue" to Color(100, 149, 237),
    "cornsilk" to Color(255, 248, 220),
    "crimson" to Color(220, 20, 60),
    "cyan" to Color(0, 255, 255),
    "darkblue" to Color(0, 0, 139),
    "darkcyan" to Color(0, 139, 139),
    "darkgoldenrod" to Color(184, 134, 11),
    "darkgray" to Color(169, 169, 169),
    "darkgreen" to Color(0, 100, 0),
    "darkgrey" to Color(169, 169, 169),
    "darkkhaki" to Color(189, 183, 107),
    "darkmagenta" to Color(139, 0, 139),
    "darkolivegreen" to Color(85, 107, 47),
    "darkorange" to Color(255, 140, 0),
    "darkorchid" to Color(153, 50, 204),
    "darkred" to Color(139, 0, 0),
    "darksalmon" to Color(233, 150, 122),
    "darkseagreen" to Color(143, 188, 143),
    "darkslateblue" to Color(72, 61, 139),
    "darkslategray" to Color(47, 79, 79),
    "darkslategrey" to Color(47, 79, 79),
    "darkturquoise" to Color(0, 206, 209),
    "darkviolet" to Color(148, 0, 211),
    "deeppink" to Color(255, 20, 147),
    "deepskyblue" to Color(0, 191, 255),
    "dimgray" to Color(105, 105, 105),
    "dimgrey" to Color(105, 105, 105),
    "dodgerblue" to Color(30, 144, 255),
    "firebrick" to Color(178, 34, 34),
    "floralwhite" to Color(255, 250, 240),
    "forestgreen" to Color(34, 139, 34),
    "fuchsia" to Color(255, 0, 255),
    "gainsboro" to Color(220, 220, 220),
    "ghostwhite" to Color(248, 248, 255),
    "gold" to Color(255, 215, 0),
    "goldenrod" to Color(218, 165, 32),
    "gray" to Color(128, 128, 128),
    "green" to Color(0, 128, 0),
    "greenyellow" to Color(173, 255, 47),
    "grey" to Color(128, 128, 128),
    "honeydew" to Color(240, 255, 240),
    "hotpink" to Color(255, 105, 180),
    "indianred" to Color(205, 92, 92),
    "indigo" to Color(75, 0, 130),
    "ivory" to Color(255, 255, 240),
    "khaki" to Color(240, 230, 140),
    "lavender" to Color(230, 230, 250),
    "lavenderblush" to Color(255, 240, 245),
    "lawngreen" to Color(124, 252, 0),
    "lemonchiffon" to Color(255, 250, 205),
    "lightblue" to Color(173, 216, 230),
    "lightcoral" to Color(240, 128, 128),
    "lightcyan" to Color(224, 255, 255),
    "lightgoldenrodyellow" to Color(250, 250, 210),
    "lightgray" to Color(211, 211, 211),
    "lightgreen" to Color(144, 238, 144),
    "lightgrey" to Color(211, 211, 211),
    "lightpink" to Color(255, 182, 193),
    "lightsalmon" to Color(255, 160, 122),
    "lightseagreen" to Color(32, 178, 170),
    "lightskyblue" to Color(135, 206, 250),
    "lightslategray" to Color(119, 136, 153),
    "lightslategrey" to Color(119, 136, 153),
    "lightsteelblue" to Color(176, 196, 222),
    "lightyellow" to Color(255, 255, 224),
    "lime" to Color(0, 255, 0),
    "limegreen" to Color(50, 205, 50),
    "linen" to Color(250, 240, 230),
    "magenta" to Color(255, 0, 255),
    "maroon" to Color(128, 0, 0),
    "mediumaquamarine" to Color(102, 205, 170),
    "mediumblue" to Color(0, 0, 205),
    "mediumorchid" to Color(186, 85, 211),
    "mediumpurple" to Color(147, 112, 219),
    "mediumseagreen" to Color(60, 179, 113),
    "mediumslateblue" to Color(123, 104, 238),
    "mediumspringgreen" to Color(0, 250, 154),
    "mediumturquoise" to Color(72, 209, 204),
    "mediumvioletred" to Color(199, 21, 133),
    "midnightblue" to Color(25, 25, 112),
    "mintcream" to Color(245, 255, 250),
    "mistyrose" to Color(255, 228, 225),
    "moccasin" to Color(255, 228, 181),
    "navajowhite" to Color(255, 222, 173),
    "navy" to Color(0, 0, 128),
    "oldlace" to Color(253, 245, 230),
    "olive" to Color(128, 128, 0),
    "olivedrab" to Color(107, 142, 35),
    "orange" to Color(255, 165, 0),
    "orangered" to Color(255, 69, 0),
    "orchid" to Color(218, 112, 214),
    "palegoldenrod" to Color(238, 232, 170),
    "palegreen" to Color(152, 251, 152),
    "paleturquoise" to Color(175, 238, 238),
    "palevioletred" to Color(219, 112, 147),
    "papayawhip" to Color(255, 239, 213),
    "peachpuff" to Color(255, 218, 185),
    "peru" to Color(205, 133, 63),
    "pink" to Color(255, 192, 203),
    "plum" to Color(221, 160, 221),
    "powderblue" to Color(176, 224, 230),
    "purple" to Color(128, 0, 128),
    "red" to Color(255, 0, 0),
    "rosybrown" to Color(188, 143, 143),
    "royalblue" to Color(65, 105, 225),
    "saddlebrown" to Color(139, 69, 19),
    "salmon" to Color(250, 128, 114),
    "sandybrown" to Color(244, 164, 96),
    "seagreen" to Color(46, 139, 87),
    "seashell" to Color(255, 245, 238),
    "sienna" to Color(160, 82, 45),
    "silver" to Color(192, 192, 192),
    "skyblue" to Color(135, 206, 235),
    "slateblue" to Color(106, 90, 205),
    "slategray" to Color(112, 128, 144),
    "slategrey" to Color(112, 128, 144),
    "snow" to Color(255, 250, 250),
    "springgreen" to Color(0, 255, 127),
    "steelblue" to Color(70, 130, 180),
    "tan" to Color(210, 180, 140),
    "teal" to Color(0, 128, 128),
    "thatch" to Color(255, 99, 71),
    "thistle" to Color(216, 191, 216),
    "tomato" to Color(255, 99, 71),
    "turquoise" to Color(64, 224, 208),
    "violet" to Color(238, 130, 238),
    "wheat" to Color(245, 222, 179),
    "white" to Color(255, 255, 255),
    "whitesmoke" to Color(245, 245, 245),
    "yellow" to Color(255, 255, 0),
    "yellowgreen" to Color(154, 205, 50)
)