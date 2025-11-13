package io.github.mpichler94.browser

import io.github.humbleui.jwm.App
import io.github.humbleui.jwm.Event
import io.github.humbleui.jwm.EventKey
import io.github.humbleui.jwm.EventMouseButton
import io.github.humbleui.jwm.EventMouseScroll
import io.github.humbleui.jwm.EventTextInput
import io.github.humbleui.jwm.EventWindowCloseRequest
import io.github.humbleui.jwm.EventWindowResize
import io.github.humbleui.jwm.EventWindowScreenChange
import io.github.humbleui.jwm.Key
import io.github.humbleui.jwm.Window
import io.github.humbleui.jwm.skija.EventFrameSkija
import io.github.humbleui.jwm.skija.LayerGLSkija
import io.github.humbleui.skija.Canvas
import io.github.humbleui.skija.ImageInfo
import io.github.humbleui.skija.Paint
import io.github.humbleui.skija.Surface
import io.github.humbleui.types.IRect
import io.github.humbleui.types.Rect
import io.github.mpichler94.browser.io.HttpClient
import io.github.mpichler94.browser.io.Request
import io.github.mpichler94.browser.io.URL
import io.github.mpichler94.browser.render.Color
import java.util.concurrent.Executors
import java.util.function.Consumer
import kotlin.math.ceil

class Browser(
    private val window: Window,
    url: String,
) : Consumer<Event> {
    val tabs = mutableListOf<Tab>()
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Worker") }
    private val chrome = Chrome(this)
    private val layer: LayerGLSkija = LayerGLSkija()
    private var chromeSurface: Surface = Surface.makeRaster(
        ImageInfo.makeN32Premul(window.contentRect.width, ceil(chrome.bottom / window.screen.scale).toInt()),
    )
    private var tabSurface: Surface? = null
    private var tabSurfaceY: Int = 0
    var activeTab: Tab? = null
        set(value) {
            field = value
            val width = window.contentRect.width / window.screen.scale
            val height = window.contentRect.height / window.screen.scale
            value?.resize(width, height)
            App.runOnUIThread {
                rasterChrome()
                rasterTab()
                window.requestFrame()
            }
        }
    private var focus: String? = null

    init {
        window.layer = layer

        App.runOnUIThread {
            val width = window.contentRect.width / window.screen.scale
            val height = window.contentRect.height / window.screen.scale
            chrome.resize(width, height)
        }

        newTab(url)
    }

    override fun accept(e: Event) {
        when (e) {
            is EventWindowResize, is EventWindowScreenChange -> {
                executor.submit {
                    chromeSurface = chromeSurface.makeSurface(
                        window.contentRect.width,
                        ceil(chrome.bottom * window.screen.scale).toInt(),
                    )!!

                    val width = window.contentRect.width / window.screen.scale
                    val height = window.contentRect.height / window.screen.scale

                    chrome.resize(width, height)
                    activeTab?.resize(width, height - chrome.bottom)
                    rasterChrome()
                    rasterTab()
                    App.runOnUIThread {
                        window.requestFrame()
                    }
                }
            }

            is EventWindowCloseRequest -> {
                window.close()
                App.terminate()
            }

            is EventMouseButton -> {
                if (e.isPressed) {
                    return
                }
                val x = e.x / window.screen.scale
                val y = e.y / window.screen.scale
                if (y < chrome.bottom) {
                    activeTab?.blur()
                    chrome.mouseClicked(e.button, x, y)
                    focus = null
                    rasterChrome()
                } else {
                    focus = "content"
                    chrome.blur()
                    val url = activeTab?.decoratedUrl
                    activeTab?.mouseClicked(e.button, x, y - chrome.bottom)
                    if (url != activeTab?.decoratedUrl) {
                        rasterChrome()
                    }
                    rasterTab()
                }
                window.requestFrame()
            }

            is EventMouseScroll -> {
                activeTab?.scroll(e.deltaY / window.screen.scale * 0.5f)
                val scroll = (activeTab?.scroll ?: 0f) * window.screen.scale
                if (scroll < tabSurfaceY || scroll > tabSurfaceY + tabSurface!!.height - window.contentRect.height) {
                    tabSurfaceY = (scroll.toInt() - tabSurface!!.height / 2).coerceAtLeast(0)
                    rasterTab()
                }
                window.requestFrame()
            }

            is EventKey -> {
                if (!e.isPressed) {
                    return
                }

                when (e.key) {
                    Key.DOWN -> activeTab?.scroll(-10f)
                    Key.UP -> activeTab?.scroll(10f)
                    Key.F5 -> executor.submit {
                        activeTab?.reload()
                        App.runOnUIThread { rasterTab() }
                    }
                    else -> {
                        val consumed = chrome.keyPressed(e.key)
                        if (!consumed) {
                            if (focus == "content") {
                                activeTab?.keyPressed(e.key)
                                rasterTab()
                            }
                        } else {
                            rasterChrome()
                        }
                    }
                }
                window.requestFrame()
            }

            is EventTextInput -> {
                val consumed = chrome.keyTyped(e.text[0])
                if (!consumed) {
                    if (focus == "content") {
                        activeTab?.keyTyped(e.text[0])
                        rasterTab()
                    }
                } else {
                    rasterChrome()
                }
                window.requestFrame()
            }

            is EventFrameSkija -> {
                draw()
//                window.requestFrame() // for animation
            }
        }
    }

    fun newTab(url: String) {
        val tab = Tab(this)
        tab.load(url)
        activeTab = tab
        tabs.add(tab)
    }

    fun removeTab(index: Int) {
        var newIndex = tabs.indexOf(activeTab)

        tabs.removeAt(index)
        if (newIndex >= tabs.size) {
            newIndex = tabs.size - 1
        }
        activeTab = tabs[newIndex]
    }

    fun load(url: String, body: String? = null) {
        executor.submit {
            activeTab?.load(url, body)
            App.runOnUIThread {
                rasterTab()
                window.requestFrame()
            }
        }
    }

    fun goBack() {
        executor.submit {
            activeTab?.goBack()
            App.runOnUIThread {
                rasterTab()
                window.requestFrame()
            }
        }
    }

    fun goForward() {
        executor.submit {
            activeTab?.goForward()
            App.runOnUIThread {
                rasterTab()
                window.requestFrame()
            }
        }
    }

    private fun draw() {
        window.setTitle(activeTab?.title ?: "Browser")
        val canvas = layer.surface.canvas
        val width = layer.surface.width
        val height = layer.surface.height
        val scale = window.screen.scale

        canvas.clear(Color.WHITE.value)

        val tabRect = IRect.makeLTRB(0, (chrome.bottom * scale).toInt(), width, height)
        val tabOffset = chrome.bottom - (activeTab?.scroll ?: 0f) + tabSurfaceY / scale
        canvas.save()
        canvas.clipRect(tabRect.toRect())
        canvas.translate(0f, tabOffset * scale)
        tabSurface?.draw(canvas, 0, 0, null)
        canvas.restore()

        canvas.save()
        canvas.translate(0f, chrome.bottom * scale)
        canvas.scale(scale, scale)
        drawScrollBar(canvas, width / scale, height / scale - chrome.bottom)
        canvas.restore()

        val chromeRect = IRect.makeLTRB(0, 0, width, ceil(chrome.bottom * scale).toInt())
        canvas.save()
        canvas.clipRect(chromeRect.toRect())
        chromeSurface.draw(canvas, 0, 0, null)
        canvas.restore()
    }

    private fun rasterTab() {
        val documentHeight = ceil((activeTab?.document?.height ?: return) * window.screen.scale).toInt()
        val tabHeight = minOf(documentHeight, window.contentRect.height * 4)
        if (tabHeight == 0) {
            return
        }

        if (tabSurface == null || tabSurface?.height != tabHeight || tabSurface?.width != window.contentRect.width) {
            tabSurface = Surface.makeRaster(ImageInfo.makeN32Premul(window.contentRect.width, tabHeight))
        }
        val scroll = (activeTab?.scroll ?: 0f) * window.screen.scale
        tabSurfaceY = (scroll - tabSurface!!.height.toFloat() / 2f).toInt().coerceAtLeast(0)

        val canvas = tabSurface!!.canvas
        canvas.clear(Color.WHITE.value)
        canvas.save()
        canvas.clipRect(Rect.makeXYWH(0f, 0f, tabSurface!!.width.toFloat(), tabSurface!!.height.toFloat()))
        canvas.translate(0f, -tabSurfaceY.toFloat())
        val scale = window.screen.scale
        canvas.scale(scale, scale)

        activeTab?.raster(canvas, 1f)
        canvas.restore()
    }

    private fun rasterChrome() {
        val canvas = chromeSurface.canvas
        canvas.clear(Color.WHITE.value)
        canvas.save()
        val scale = window.screen.scale
        canvas.scale(scale, scale)

        chrome.paint(chromeSurface.width.toFloat(), chromeSurface.height.toFloat()).forEach { it.execute(canvas, 1f) }
        canvas.restore()
    }

    private fun drawScrollBar(canvas: Canvas, width: Float, height: Float) {
        val documentHeight = activeTab?.document?.height?.coerceAtLeast(1f) ?: 1f
        if (height == 0f || documentHeight < height) {
            return
        }
        val scroll = activeTab?.scroll ?: 0f

        val scrollPosition = height * scroll / documentHeight
        val scrollBarHeight = height * height / documentHeight
        val paint = Paint()
        paint.color = Color.BLACK.value
        val rect = Rect.makeXYWH((width - 10), (scrollPosition), 10.0f, scrollBarHeight)
        canvas.drawRect(rect, paint)
    }
}

internal fun URL.createRequest(method: String = "GET", referrer: URL? = null, body: String? = null): Request {
    val additionalHeaders = mutableMapOf(
        "Host" to host,
        "Connection" to "keep-alive",
        "User-Agent" to "Browser from Scratch",
    )
    val cookie = HttpClient.instance.getCookie(this)
    if (cookie != null) {
        var allowCookie = true
        if (referrer != null && cookie.parameters["samesite"] == "lax" && method != "GET") {
            allowCookie = host == referrer.host
        }
        if (allowCookie) {
            additionalHeaders["Cookie"] = cookie.value
        }
    }
    return Request(this, method, additionalHeaders, body)
}
