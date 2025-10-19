package io.github.mpichler94.browser

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.font.FontRenderContext
import java.io.File
import java.time.Instant
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

class Browser : JPanel() {
    private val logger = KotlinLogging.logger {}
    private val client = HttpClient()
    private val cache: LinkedHashMap<URL, CachedResponse> = LinkedHashMap(1000, 0.75f)

    private val scrollStep = 10

    private var url = "about:blank"
    private var scroll = 0

    private var nodes: Token? = null
    private var document: Layout? = null
    private var displayList = emptyList<Drawable>()

    init {
        layout = null

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher {
            if (it.id == KeyEvent.KEY_PRESSED) {
                when (it.keyCode) {
                    KeyEvent.VK_DOWN -> scrollDown()
                    KeyEvent.VK_UP -> scrollUp()
                    KeyEvent.VK_F5 -> load(url)
                }
            }
            false
        }

        addMouseWheelListener {
            if (it.wheelRotation > 0) {
                scrollDown()
            } else {
                scrollUp()
            }
        }

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                scroll = max(0, min(scroll, (document?.height ?: 0) - height))
                reflow()
            }
        })
    }

    override fun addNotify() {
        super.addNotify()

        graphics.font = Font("Sans", Font.PLAIN, 16)

        reflow()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        draw(g)
    }

    fun load(url: String) {
        val showSource = url.startsWith("view-source:")
        this.url = url

        val response = try {
            getResponse(if (showSource) url.substringAfter("view-source:") else url)
        } catch (e: Throwable) {
            Response(200, mapOf(), "")
        }

        if (showSource) {
            showSource(response.body)
        } else {
            nodes = HtmlParser(response.body).parse()
            if (logger.isDebugEnabled()) {
                nodes?.printTree()
            }
            reflow()
        }
    }

    private fun getResponse(url: String): Response {
        val parsedUrl = URL(url)

        return if (parsedUrl.scheme == "data") {
            Response(body = url.substringAfter(","))
        } else if (parsedUrl.scheme == "file") {
            Response(body = File(parsedUrl.path).readText())
        } else {
            if (parsedUrl in cache && cache[parsedUrl]!!.validUntil > Instant.now()) {
                cache[parsedUrl]!!.response
            } else {
                val response = client.request(parsedUrl.createRequest())
                if (response.headers["cache-control"]?.contains("max-age") == true) {
                    val maxAge = response.headers["cache-control"]!!.substringAfter("max-age=").toInt()
                    cache[parsedUrl] = CachedResponse(
                        Instant.now().plusSeconds(maxAge.toLong()),
                        response
                    )

                    // TODO: more sophisticated cache eviction
                    if (cache.size > 740) {
                        cache.remove(cache.keys.first())
                    }
                }
                response
            }
        }
    }

    private fun reflow() {
        nodes?.run {
            document = DocumentLayout(this, width)
            document!!.layout()
            if (logger.isDebugEnabled()) {
                document!!.printTree()
            }
            displayList = document!!.paintTree()
        }
        repaint()
    }

    private fun draw(graphics: Graphics) {
        for (cmd in displayList) {
            if (cmd.top > scroll + height || cmd.bottom < scroll) {
                continue
            }
            cmd.execute(graphics, scroll)
        }

        drawScrollBar(graphics)
    }

    private fun drawScrollBar(graphics: Graphics) {
        val scrollPosition = height * scroll / (document?.height ?: 1)
        val scrollBarHeight = height * height / (document?.height ?: 1)
        if (scrollBarHeight <= height) {
            graphics.fillRect(width - 10, scrollPosition, 10, scrollBarHeight)
        }
    }

    private fun scrollDown() {
        if (scroll >= (document?.height ?: 0) - height) {
            return
        }

        scroll += scrollStep
        repaint()
    }

    private fun scrollUp() {
        if (scroll <= 0) {
            return
        }

        scroll -= scrollStep
        repaint()
    }


    private fun URL.createRequest(headers: Map<String, String> = mapOf()): Request {
        val allHeaders =
            mapOf("Host" to host, "Connection" to "keep-alive", "User-Agent" to "Browser from Scratch") + headers
        return Request(this, "GET", allHeaders)
    }


    private fun showSource(body: String) {
        println(body)
    }
}

private data class CachedResponse(val validUntil: Instant, val response: Response)

interface Drawable {
    val top: Int
    val left: Int
    val bottom: Int
    val right: Int

    fun execute(graphics: Graphics, scroll: Int)
}

class DrawText(x1: Int, y1: Int, val text: String, val font: Font) : Drawable {
    override val top = y1
    override val left = x1
    override val bottom = y1 + font.getLineMetrics(text, frc).height.toInt()
    override val right = y1 + font.getStringBounds(text, frc).width.toInt()

    companion object {
        private val frc = FontRenderContext(null, true, true)
    }

    fun withPos(x1: Int, y1: Int) = DrawText(x1, y1, text, font)

    override fun execute(graphics: Graphics, scroll: Int) {
        graphics.color = Color.black
        graphics.font = font
        graphics.drawString(text, left, top - scroll + font.getLineMetrics(text, frc).ascent.toInt())
    }
}

class DrawRect(x1: Int, y1: Int, x2: Int, y2: Int, private val color: Color) : Drawable {
    override val top = y1
    override val left = x1
    override val bottom = y2
    override val right = x2

    override fun execute(graphics: Graphics, scroll: Int) {
        graphics.color = color
        graphics.fillRect(left, top - scroll, right - left, bottom - top)
    }
}