package io.github.mpichler94.browser

import java.awt.Font
import java.awt.Graphics
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.io.File
import java.time.Instant
import javax.swing.JPanel

class Browser : JPanel() {
    private val client = HttpClient()
    private val cache: LinkedHashMap<URL, CachedResponse> = LinkedHashMap(1000, 0.75f)

    private val hStep = 13
    private val vStep = 50
    private val scrollStep = 10

    private var url = "about:blank"
    private var scroll = 0
    private var canvasHeight = 0

    // 1 = scroll - height / canvasHeight
    // 0 = scroll
    private val scrollPosition: Int get() = (height * scroll) / canvasHeight

    private var text = ""
    private var displayList: List<DrawableWord> = listOf()

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
                displayList = layout(text)
                repaint()
            }
        })
    }

    override fun addNotify() {
        super.addNotify()

        graphics.font = Font("Sans", Font.PLAIN, 16)

        displayList = layout(text)
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
            text = lex(response.body)
            displayList = layout(text)
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
        for (c in displayList) {
            if (c.y > scroll + height || c.y + vStep < scroll) {
                continue
            }
            graphics.drawString(c.word, c.x, c.y - scroll)
        }

        graphics.fillRect(width - 10, scrollPosition, 10, 40)
    }

    private fun scrollDown() {
        if (scroll >= canvasHeight - height) {
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

    private fun layout(text: String): List<DrawableWord> {
        val displayList = mutableListOf<DrawableWord>()

        var cursorX = hStep
        var cursorY = vStep
        val metrics = graphics.fontMetrics
        val space = metrics.charWidth(' ')
        for (word in text.split(' ')) {
            val w = metrics.stringWidth(word)
            if (cursorX + w > width - hStep) {
                cursorY += metrics.height
                cursorX = hStep
            }

            displayList.add(DrawableWord(cursorX, cursorY, word))
            cursorX += w + space
        }

        canvasHeight = cursorY + vStep
        return displayList
    }

    private fun URL.createRequest(headers: Map<String, String> = mapOf()): Request {
        val allHeaders =
            mapOf("Host" to host, "Connection" to "keep-alive", "User-Agent" to "Browser from Scratch") + headers
        return Request(this, "GET", allHeaders)
    }

    private fun lex(body: String): String {
        var inTag = false
        var inEntity = false
        var entity = ""
        var text = ""
        for (c in body) {
            when (c) {
                '<' -> inTag = true
                '>' -> inTag = false
                '&' -> inEntity = true
                ';' -> {
                    inEntity = false
                    print(entities[entity] ?: "")
                    entity = ""
                }

                else if (inEntity) -> entity += c
                else if (!inTag) -> text += c
            }
        }
        return text
    }

    private fun showSource(body: String) {
        println(body)
    }

    private val entities = mapOf("lt" to "<", "gt" to ">")
}

private data class CachedResponse(val validUntil: Instant, val response: Response)

private data class DrawableCharacter(val x: Int, val y: Int, val character: Char)
private data class DrawableWord(val x: Int, val y: Int, val word: String)

