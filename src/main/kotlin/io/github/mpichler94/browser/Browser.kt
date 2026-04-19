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
import io.github.mpichler94.browser.render.Drawable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.math.ceil

class Browser(
    private val window: Window,
    url: String,
) : Consumer<Event> {
    val tabs = mutableListOf<Tab>()
    val measure = MeasureTime()

    var activeTab: Tab? = null
        set(value) {
            field = value
            activeTabUrl
            activeTabScroll
            activeTabDisplayList
            activeTabHeight
            val width = window.contentRect.width / window.screen.scale
            val height = window.contentRect.height / window.screen.scale
            value?.apply {
                resize(width, height)
                needsRasterAndDraw()
                needsAnimationFrame(this)
                taskRunner.start()
            }
        }
    var activeTabUrl: String? = null
        private set

    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "Worker") }
    private val chrome = Chrome(this)
    private val layer: LayerGLSkija = LayerGLSkija()

    private var chromeSurface: Surface =
        Surface.makeRaster(
            ImageInfo.makeN32Premul(window.contentRect.width, ceil(chrome.bottom / window.screen.scale).toInt()),
        )
    private var tabSurface: Surface? = null
    private var tabSurfaceY: Int = 0
    private var focus: String? = null
    private var animationTimer: ScheduledFuture<*>? = null
    private var needsRasterAndDraw = true
    private var needsAnimationFrame = true
    private var activeTabScroll: Float = 0f
    private var activeTabDisplayList: List<Drawable> = emptyList()
    private var activeTabHeight: Float = 800f

    init {
        window.layer = layer

        App.runOnUIThread {
            val width = window.contentRect.width / window.screen.scale
            val height = window.contentRect.height / window.screen.scale
            chrome.resize(width, height)
        }

        newTab(url)
    }

    @Synchronized
    override fun accept(e: Event) {
        when (e) {
            is EventWindowResize, is EventWindowScreenChange -> {
                chromeSurface =
                    chromeSurface.makeSurface(
                        window.contentRect.width,
                        ceil(chrome.bottom * window.screen.scale).toInt(),
                    )!!

                val width = window.contentRect.width / window.screen.scale
                val height = window.contentRect.height / window.screen.scale

                chrome.resize(width, height)
                needsRasterAndDraw()
                activeTab?.schedule {
                    resize(width, height)
                    needsRasterAndDraw()
                }
            }

            is EventWindowCloseRequest -> {
                tabs.forEach { it.taskRunner.needsQuit() }
                measure.finish()
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
                    needsRasterAndDraw()
                } else {
                    focus = "content"
                    chrome.blur()
                    activeTab?.schedule { mouseClicked(e.button, x, y - chrome.bottom) }
                }
            }

            is EventMouseScroll -> {
                synchronized(this) {
                    if (activeTab == null) return
                    activeTabScroll = clampScroll(activeTabScroll - e.deltaY)
                    needsRasterAndDraw()
                    needsAnimationFrame(activeTab!!)
                }
//                clampScroll(activeTabScroll - e.deltaY)
//                activeTab?.scroll(e.deltaY / window.screen.scale * 0.5f)
//                val scroll = (activeTab?.scroll ?: 0f) * window.screen.scale
//                if (scroll < tabSurfaceY || scroll > tabSurfaceY + tabSurface!!.height - window.contentRect.height) {
//                    tabSurfaceY = (scroll.toInt() - tabSurface!!.height / 2).coerceAtLeast(0)
//                }
//                needsRasterAndDraw()
            }

            is EventKey -> {
                if (!e.isPressed) {
                    return
                }

                when (e.key) {
                    Key.DOWN -> {
                        activeTab?.scroll(-10f)
                    }

                    Key.UP -> {
                        activeTab?.scroll(10f)
                    }

                    Key.F5 -> {
                        activeTab?.schedule { reload() }
                    }

                    else -> {
                        val consumed = chrome.keyPressed(e.key)
                        if (!consumed) {
                            if (focus == "content") {
                                activeTab?.schedule { keyPressed(e.key) }
                            }
                        } else {
                            needsRasterAndDraw()
                        }
                    }
                }
            }

            is EventTextInput -> {
                val consumed = chrome.keyTyped(e.text[0])
                if (!consumed) {
                    if (focus == "content") {
                        activeTab?.schedule { keyTyped(e.text[0]) }
                    }
                } else {
                    needsRasterAndDraw()
                }
            }

            is EventFrameSkija -> {
                if (needsRasterAndDraw) {
                    measure.time("raster")
                    rasterChrome()
                    rasterTab()
                    draw()
                    measure.stop("raster")
                } else {
                    draw()
                }

                needsRasterAndDraw = false
                window.requestFrame() // for animation
                scheduleAnimationFrame()
            }
        }
    }

    @Synchronized
    fun newTab(url: String) {
        val tab = Tab(this)
        activeTab = tab
        tabs.add(tab)
        scheduleLoad(url)
    }

    fun removeTab(index: Int) {
        var newIndex = tabs.indexOf(activeTab)

        tabs.removeAt(index)
        if (newIndex >= tabs.size) {
            newIndex = tabs.size - 1
        }
        activeTab = tabs[newIndex]
    }

    fun scheduleLoad(
        url: String,
        body: String? = null,
    ) {
        activeTab?.taskRunner?.clearPendingTasks()
        activeTab?.schedule { load(url, body) }
    }

    fun goBack() {
        activeTab?.taskRunner?.clearPendingTasks()
        activeTab?.schedule { goBack() }
    }

    fun goForward() {
        activeTab?.taskRunner?.clearPendingTasks()
        activeTab?.schedule { goForward() }
    }

    fun scheduleAnimationFrame() {
        if (needsAnimationFrame && animationTimer == null) {
            val activeTab = this.activeTab
            val scroll = activeTabScroll
            animationTimer =
                executor.schedule({
                    activeTab?.schedule {
                        needsAnimationFrame = false
                        runAnimationFrame(scroll)
                    }
                    animationTimer = null
                }, 33, TimeUnit.MILLISECONDS)
        }
    }

    fun needsRasterAndDraw() {
        needsRasterAndDraw = true
    }

    fun needsAnimationFrame(tab: Tab) {
        if (tab == activeTab) {
            needsAnimationFrame = true
        }
    }

    @Synchronized
    fun commit(
        tab: Tab,
        data: CommitData,
    ) {
        if (tab == activeTab) {
            activeTabUrl = data.url
            if (data.scroll != null) {
                activeTabScroll = data.scroll
            }
            activeTabHeight = data.height
            if (data.displayList.isNotEmpty()) {
                activeTabDisplayList = data.displayList
            }
            animationTimer = null
            needsRasterAndDraw()
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
        val tabOffset = chrome.bottom - activeTabScroll + tabSurfaceY / scale
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
        val scroll = activeTabScroll * window.screen.scale
        tabSurfaceY = (scroll - tabSurface!!.height.toFloat() / 2f).toInt().coerceAtLeast(0)

        val canvas = tabSurface!!.canvas
        canvas.clear(Color.WHITE.value)
        canvas.save()
        canvas.clipRect(Rect.makeXYWH(0f, 0f, tabSurface!!.width.toFloat(), tabSurface!!.height.toFloat()))
        canvas.translate(0f, -tabSurfaceY.toFloat())
        val scale = window.screen.scale
        canvas.scale(scale, scale)

        activeTabDisplayList.forEach { cmd ->
            cmd.execute(canvas, 1f)
        }
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

    private fun drawScrollBar(
        canvas: Canvas,
        width: Float,
        height: Float,
    ) {
        val documentHeight = activeTab?.document?.height?.coerceAtLeast(1f) ?: 1f
        if (height == 0f || documentHeight < height) {
            return
        }
        val scroll = activeTabScroll

        val scrollPosition = height * scroll / documentHeight
        val scrollBarHeight = height * height / documentHeight
        val paint = Paint()
        paint.color = Color.BLACK.value
        val rect = Rect.makeXYWH((width - 10), (scrollPosition), 10.0f, scrollBarHeight)
        canvas.drawRect(rect, paint)
    }

    private fun clampScroll(scroll: Float): Float {
        val height = activeTabHeight ?: 800f
        val maxScroll = height - (window.contentRect.height / window.screen.scale - chrome.bottom)
        return scroll.coerceIn(0f, maxScroll)
    }
}

internal fun URL.createRequest(
    method: String = "GET",
    referrer: URL? = null,
    body: String? = null,
): Request {
    val additionalHeaders =
        mutableMapOf(
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
