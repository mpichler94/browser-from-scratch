package io.github.mpichler94.browser

import io.github.humbleui.jwm.Key
import io.github.humbleui.jwm.MouseButton
import io.github.humbleui.skija.Font
import io.github.humbleui.skija.FontMgr
import io.github.humbleui.skija.FontStyle
import io.github.humbleui.skija.paragraph.DecorationStyle
import io.github.humbleui.skija.paragraph.TextStyle
import io.github.humbleui.types.Rect
import io.github.mpichler94.browser.render.Color
import io.github.mpichler94.browser.render.DrawLine
import io.github.mpichler94.browser.render.DrawOutline
import io.github.mpichler94.browser.render.DrawParagraph
import io.github.mpichler94.browser.render.DrawRect
import io.github.mpichler94.browser.render.DrawText
import io.github.mpichler94.browser.render.Drawable
import io.github.mpichler94.browser.render.contains

class Chrome(
    private val browser: Browser,
) {
    private val font = Font(FontMgr.getDefault().matchFamilyStyle("Segoe UI", FontStyle.NORMAL)!!, 16f)
    private val boldFont = Font(FontMgr.getDefault().matchFamilyStyle("Segoe UI", FontStyle.BOLD)!!, 16f)
    private val fontHeight = font.metrics.height
    private val padding = 5f

    private val tabbarTop = 0f
    private val tabbarBottom = fontHeight + 2 * padding
    private lateinit var newTabRect: Rect

    private val urlbarTop = tabbarBottom
    private val urlbarBottom = urlbarTop + fontHeight + 2 * padding
    private lateinit var backRect: Rect
    private lateinit var forwardRect: Rect
    private lateinit var addressRect: Rect

    private var focus: String? = null
    private var addressBar = ""
    private var cursorPos = 0

    val bottom = urlbarBottom

    fun resize(width: Float, height: Float) {
        val plusWidth = font.measureTextWidth("+") + 2 * padding
        newTabRect = Rect.makeXYWH(padding, padding, plusWidth, fontHeight)

        val backWidth = font.measureTextWidth("<") + 2 * padding
        backRect = Rect.makeLTRB(padding, urlbarTop + padding, padding + backWidth, urlbarBottom - padding)
        val forwardWidth = font.measureTextWidth(">") + 2 * padding
        forwardRect = Rect.makeLTRB(
            backRect.right + 2 * padding,
            urlbarTop + padding,
            backRect.right + 2 * padding + forwardWidth,
            urlbarBottom - padding,
        )

        addressRect =
            Rect.makeLTRB(forwardRect.right + padding, urlbarTop + padding, width - padding, urlbarBottom - padding)
    }

    fun mouseClicked(button: MouseButton, x: Float, y: Float) {
        focus = null
        if (newTabRect.contains(x, y)) {
            browser.newTab("https://browser.engineering/")
        } else if (backRect.contains(x, y)) {
            browser.goBack()
        } else if (forwardRect.contains(x, y)) {
            browser.goForward()
        } else if (addressRect.contains(x, y)) {
            focus = "adress bar"
            addressBar = ""
            cursorPos = 0
        } else {
            browser.tabs.indices
                .firstOrNull { tabRect(it).contains(x, y) }
                ?.let {
                    if (button == MouseButton.PRIMARY) {
                        browser.activeTab = browser.tabs[it]
                    } else if (button == MouseButton.MIDDLE) {
                        browser.removeTab(it)
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

    fun keyPressed(keyCode: Key): Boolean {
        if (focus == "adress bar") {
            when (keyCode) {
                Key.ENTER -> {
                    browser.scheduleLoad(addressBar)
                    addressBar = ""
                    cursorPos = 0
                    focus = null
                }

                Key.BACKSPACE if (cursorPos > 0) -> {
                    addressBar = addressBar.removeRange(cursorPos - 1, cursorPos)
                    cursorPos--
                }

                Key.DELETE if (cursorPos < addressBar.length) -> {
                    addressBar = addressBar.removeRange(cursorPos, cursorPos + 1)
                    cursorPos--
                }

                Key.ESCAPE -> {
                    focus = null
                }

                Key.LEFT -> {
                    cursorPos = (cursorPos - 1).coerceAtLeast(0)
                }

                Key.RIGHT -> {
                    cursorPos = (cursorPos + 1).coerceAtMost(addressBar.length)
                }

                else -> {}
            }
            return true
        }

        return false
    }

    fun blur() {
        focus = null
    }

    private fun tabRect(index: Int): Rect {
        val tabsStart = newTabRect.right + padding
        val tabWidth = font.measureTextWidth("Tab X") + 2 * padding
        return Rect.makeLTRB(tabsStart + index * tabWidth, tabbarTop, tabsStart + (index + 1) * tabWidth, tabbarBottom)
    }

    fun paint(width: Float, height: Float): List<Drawable> {
        val cmds = mutableListOf<Drawable>()

        // Top bar
        cmds.add(DrawRect(0f, 0f, width, bottom, Color.WHITE))
        cmds.add(DrawLine(0f, bottom, width, bottom, Color.BLACK, 1f))

        // Tab bar
        cmds.add(DrawOutline(newTabRect, Color.BLACK, 1f))
        cmds.add(DrawText(newTabRect.left + padding, newTabRect.top, "+", font, Color.BLACK))

        for ((i, tab) in browser.tabs.withIndex()) {
            val bounds = tabRect(i)
            cmds.add(DrawLine(bounds.left, 0f, bounds.left, bounds.bottom, Color.BLACK, 1f))
            cmds.add(DrawLine(bounds.right, 0f, bounds.right, bounds.bottom, Color.BLACK, 1f))
            cmds.add(DrawText(bounds.left + padding, bounds.top + padding, "Tab ${i + 1}", font, Color.BLACK))

            if (tab == browser.activeTab) {
                cmds.add(DrawLine(0f, bounds.bottom, bounds.left, bounds.bottom, Color.BLACK, 1f))
                cmds.add(DrawLine(bounds.right, bounds.bottom, width, bounds.bottom, Color.BLACK, 1f))
            }
        }

        // Navigation controls
        var color = if (browser.activeTab?.canGoBack() == true) Color.BLACK else Color.makeGray(211)
        cmds.add(DrawOutline(backRect, color, 1f))
        cmds.add(DrawText(backRect.left + padding, backRect.top, "<", font, color))
        color = if (browser.activeTab?.canGoForward() == true) Color.BLACK else Color.makeGray(211)
        cmds.add(DrawOutline(forwardRect, color, 1f))
        cmds.add(DrawText(forwardRect.left + padding, forwardRect.top, ">", font, color))

        // Address bar
        cmds.add(DrawOutline(addressRect, Color.BLACK, 1f))
        if (focus == "adress bar") {
            cmds.add(DrawText(addressRect.left + padding, addressRect.top, addressBar, font, Color.BLACK))
            val w = font.measureTextWidth(addressBar.substring(0, cursorPos))
            cmds.add(
                DrawLine(
                    addressRect.left + padding + w,
                    addressRect.top,
                    addressRect.left + padding + w,
                    addressRect.bottom,
                    Color.BLACK,
                    1f,
                ),
            )
        } else {
            var x = addressRect.left + padding
            var url = browser.commitData?.url ?: ""

            if (url.startsWith("Unsafe")) {
                cmds.add(DrawText(x, addressRect.top, "Unsafe", boldFont, Color.BLACK))
                x += boldFont.measureTextWidth("Unsafe ")
                url = url.replaceFirst("Unsafe", "").trim()
                if ("https://" in url) {
                    val ts = TextStyle()
                        .setDecorationStyle(DecorationStyle.NONE.withLineThrough(true).withColor(Color.RED.value))
                        .setFontFamily(font.typeface!!.familyName)
                        .setFontStyle(FontStyle.BOLD)
                        .setFontSize(font.size)
                        .setColor(Color.RED.value)

                    val paragraph = DrawParagraph(x, addressRect.top, "https", ts)
                    cmds.add(paragraph)
                    x += paragraph.rect.width
                    url = url.replaceFirst("https", "").trim()
                }
            }

            cmds.add(DrawText(x, addressRect.top, url, font, Color.BLACK))
        }

        return cmds
    }
}
