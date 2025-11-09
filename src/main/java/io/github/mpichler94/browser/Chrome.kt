package io.github.mpichler94.browser

import java.awt.Color
import java.awt.Font
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute

class Chrome(private val browser: Browser) {
    private val frc = FontRenderContext(null, true, true)
    private val font = Font(Font.SANS_SERIF, Font.PLAIN, 20)
    private val fontHeight = font.getLineMetrics("", FontRenderContext(null, true, true)).height.toInt()
    private val padding = 5

    private val tabbarTop = 0
    private val tabbarBottom = fontHeight + 2 * padding
    private val newTabRect: Rectangle

    private val urlbarTop = tabbarBottom
    private val urlbarBottom = urlbarTop + fontHeight + 2 * padding
    private val backRect: Rectangle
    private val forwardRect: Rectangle
    private var addressRect: Rectangle

    private var focus: String? = null
    private var addressBar = ""
    private var cursorPos = 0

    val bottom = urlbarBottom

    init {
        val plusWidth = font.getStringBounds("+", frc).width.toInt() + 2 * padding
        newTabRect = Rectangle(padding, padding, padding + fontHeight, padding + plusWidth)

        val backWidth = font.getStringBounds("<", frc).width.toInt() + 2 * padding
        backRect = Rectangle(urlbarTop + padding, padding, urlbarBottom - padding, padding + backWidth)
        val forwardWidth = font.getStringBounds(">", frc).width.toInt() + 2 * padding
        forwardRect = Rectangle(
            urlbarTop + padding,
            backRect.right + 2 * padding,
            urlbarBottom - padding,
            backRect.right + 2 * padding + forwardWidth
        )
        addressRect =
            Rectangle(urlbarTop + padding, forwardRect.right + padding, urlbarBottom - padding, browser.width - padding)
    }

    fun resize() {
        addressRect =
            Rectangle(urlbarTop + padding, forwardRect.right + padding, urlbarBottom - padding, browser.width - padding)
    }

    fun mouseClicked(button: Int, x: Int, y: Int) {
        focus = null
        if (newTabRect.contains(x, y)) {
            browser.newTab("https://browser.engineering/")
        } else if (backRect.contains(x, y)) {
            browser.activeTab?.goBack()
        } else if (forwardRect.contains(x, y)) {
            browser.activeTab?.goForward()
        } else if (addressRect.contains(x, y)) {
            focus = "adress bar"
            addressBar = ""
            cursorPos = 0
        } else {
            browser.tabs.indices
                .firstOrNull { tabRect(it).contains(x, y) }
                ?.let {
                    if (button == MouseEvent.BUTTON1) {
                        browser.activeTab = browser.tabs[it]
                    } else if (button == MouseEvent.BUTTON2) {
                        browser.tabs.removeAt(it)
                    }
                }
        }
    }

    fun keyTyped(key: Char): Boolean {
        if (focus == "adress bar") {
            addressBar = addressBar.substring(0, cursorPos) + key + addressBar.substring(cursorPos)
            cursorPos++
            return true
        }

        return false
    }

    fun keyPressed(keyCode: Int): Boolean {
        if (focus == "adress bar") {
            when (keyCode) {
                KeyEvent.VK_ENTER -> {
                    browser.activeTab?.load(addressBar)
                    addressBar = ""
                    cursorPos = 0
                    focus = null
                }

                KeyEvent.VK_BACK_SPACE if (cursorPos > 0) -> {
                    addressBar = addressBar.removeRange(cursorPos - 1, cursorPos)
                    cursorPos--
                }

                KeyEvent.VK_DELETE if (cursorPos < addressBar.length) -> {
                    addressBar = addressBar.removeRange(cursorPos, cursorPos + 1)
                    cursorPos--
                }

                KeyEvent.VK_ESCAPE -> focus = null
                KeyEvent.VK_LEFT -> cursorPos = (cursorPos - 1).coerceAtLeast(0)
                KeyEvent.VK_RIGHT -> cursorPos = (cursorPos + 1).coerceAtMost(addressBar.length)
            }
            return true
        }

        return false
    }

    fun blur() {
        focus = null
    }

    private fun tabRect(index: Int): Rectangle {
        val tabsStart = newTabRect.right + padding
        val tabWidth = font.getStringBounds("Tab X", frc).width.toInt() + 2 * padding
        return Rectangle(tabbarTop, tabsStart + index * tabWidth, tabbarBottom, tabsStart + (index + 1) * tabWidth)
    }

    fun paint(): List<Drawable> {
        val cmds = mutableListOf<Drawable>()

        // Top bar
        cmds.add(DrawRect(0, 0, browser.width, bottom, Color.WHITE))
        cmds.add(DrawLine(0, bottom, browser.width, bottom, Color.BLACK, 1))

        // Tab bar
        cmds.add(DrawOutline(newTabRect, Color.BLACK, 1))
        cmds.add(DrawText(newTabRect.left + padding, newTabRect.top, "+", font, Color.BLACK))

        for ((i, tab) in browser.tabs.withIndex()) {
            val bounds = tabRect(i)
            cmds.add(DrawLine(bounds.left, 0, bounds.left, bounds.bottom, Color.BLACK, 1))
            cmds.add(DrawLine(bounds.right, 0, bounds.right, bounds.bottom, Color.BLACK, 1))
            cmds.add(DrawText(bounds.left + padding, bounds.top + padding, "Tab $i", font, Color.BLACK))

            if (tab == browser.activeTab) {
                cmds.add(DrawLine(0, bounds.bottom, bounds.left, bounds.bottom, Color.BLACK, 1))
                cmds.add(DrawLine(bounds.right, bounds.bottom, browser.width, bounds.bottom, Color.BLACK, 1))
            }
        }

        // Navigation controls
        var color = if (browser.activeTab?.canGoBack() == true) Color.black else Color.lightGray
        cmds.add(DrawOutline(backRect, color, 1))
        cmds.add(DrawText(backRect.left + padding, backRect.top, "<", font, color))
        color = if (browser.activeTab?.canGoForward() == true) Color.black else Color.lightGray
        cmds.add(DrawOutline(forwardRect, color, 1))
        cmds.add(DrawText(forwardRect.left + padding, forwardRect.top, ">", font, color))

        // Address bar
        cmds.add(DrawOutline(addressRect, Color.BLACK, 1))
        if (focus == "adress bar") {
            cmds.add(DrawText(addressRect.left + padding, addressRect.top, addressBar, font, Color.BLACK))
            val w = font.getStringBounds(addressBar.substring(0, cursorPos), frc).width.toInt()
            cmds.add(
                DrawLine(
                    addressRect.left + padding + w,
                    addressRect.top,
                    addressRect.left + padding + w,
                    addressRect.bottom,
                    Color.BLACK,
                    1
                )
            )
        } else {
            var x = addressRect.left + padding
            var url = browser.activeTab?.decoratedUrl ?: ""

            if (url.startsWith("Unsafe")) {
                val boldFont = font.deriveFont(Font.BOLD)
                cmds.add(DrawText(x, addressRect.top, "Unsafe", boldFont, Color.RED))
                x += boldFont.getStringBounds("Unsafe ", frc).width.toInt()
                url = url.replaceFirst("Unsafe", "").trim()
                if ("https://" in url) {
                    val attributes = font.attributes as MutableMap<TextAttribute, Any>
                    attributes[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
                    font.deriveFont(attributes)
                    cmds.add(DrawText(x, addressRect.top, "https", font.deriveFont(attributes), Color.RED))
                    x += font.getStringBounds("https", frc).width.toInt()
                    url = url.replaceFirst("https", "").trim()
                }
            }

            cmds.add(DrawText(x, addressRect.top, url, font, Color.BLACK))
        }

        return cmds
    }

}

class Rectangle(val top: Int, val left: Int, val bottom: Int, val right: Int) {
    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
}