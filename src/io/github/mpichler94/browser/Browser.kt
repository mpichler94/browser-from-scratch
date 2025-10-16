package io.github.mpichler94.browser

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

class Browser : JPanel() {
    private val client = HttpClient()
    private val cache: LinkedHashMap<URL, CachedResponse> = LinkedHashMap(1000, 0.75f)

    private val vStep = 50
    private val scrollStep = 10
    private val frc = FontRenderContext(null, true, true)

    private var url = "about:blank"
    private var scroll = 0

    private var tokens = listOf<Token>()
    private var l: Layout? = null

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
                l = Layout(tokens, width)
                repaint()
            }
        })
    }

    override fun addNotify() {
        super.addNotify()

        graphics.font = Font("Sans", Font.PLAIN, 16)

        l = Layout(tokens, width)
        repaint()
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
            tokens = lex(response.body)
            l = Layout(tokens, width)
            repaint()
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

    private fun draw(graphics: Graphics) {
        for (c in l?.displayList ?: emptyList()) {
            if (c.y > scroll + height || c.y + vStep < scroll) {
                continue
            }
            graphics.font = c.font
            graphics.drawString(c.word, c.x, c.y - scroll + font.getLineMetrics(c.word, frc).ascent.toInt())
        }

        val scrollPosition = height * scroll / (l?.height ?: 1)
        val scrollBarHeight = height * height / (l?.height ?: 1)
        if (scrollBarHeight <= height) {
            graphics.fillRect(width - 10, scrollPosition, 10, scrollBarHeight)
        }
    }

    private fun scrollDown() {
        if (scroll >= (l?.height ?: 0) - height) {
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

    private fun lex(body: String): List<Token> {
        val out = mutableListOf<Token>()
        var inTag = false
        var inEntity = false
        var entity = ""
        var buffer = ""
        for (c in body) {
            when (c) {
                '<' -> {
                    inTag = true
                    if (buffer.isNotBlank()) {
                        out.add(Text(buffer))
                    }
                    buffer = ""
                }

                '>' -> {
                    inTag = false
                    out.add(Tag(buffer))
                    buffer = ""
                }

                '&' -> inEntity = true
                ';' -> {
                    inEntity = false
                    buffer += entities[entity] ?: entity
                    entity = ""
                }

                else if (inEntity) -> entity += c
                else -> buffer += c
            }
        }
        if (!inTag && buffer.isNotEmpty()) {
            out.add(Text(buffer))
        }
        return out
    }

    private fun showSource(body: String) {
        println(body)
    }

    private val entities = mapOf("lt" to "<", "gt" to ">")
}

private data class CachedResponse(val validUntil: Instant, val response: Response)

data class DrawableWord(val x: Int, val y: Int, val word: String, val font: Font)

sealed interface Token
data class Text(val text: String) : Token
data class Tag(val tag: String) : Token