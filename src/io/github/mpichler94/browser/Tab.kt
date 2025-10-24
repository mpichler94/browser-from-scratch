package io.github.mpichler94.browser

import io.github.mpichler94.browser.layout.DocumentLayout
import io.github.mpichler94.browser.layout.Layout
import io.github.mpichler94.browser.layout.paintTree
import io.github.mpichler94.browser.layout.printTree
import io.github.mpichler94.browser.layout.toList
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Graphics
import java.awt.event.MouseEvent
import java.io.File
import java.time.Instant

class Tab(private val browser: Browser) {
    private val logger = KotlinLogging.logger {}
    private val client = HttpClient()
    private val cache: LinkedHashMap<URL, CachedResponse> = LinkedHashMap(1000, 0.75f)
    private val maxAgePattern = Regex("""max-age=(\d+)""")
    private val defaultStyleSheet: Map<Selector, Map<String, String>> =
        CssParser(Tab::class.java.getResource("/browser.css")!!.readText()).parse()
    private val inheritedProperties =
        mapOf(
            "color" to "black",
            "font-size" to "16px",
            "font-style" to "normal",
            "font-weight" to "normal",
            "font-family" to "sans-serif"
        )

    private var width: Int = 0
    private var height: Int = 0
    private val scrollStep = 10
    private val vStep = 50

    var rawUrl = "about:blank"
        private set
    private var url: URL? = null
    private var history = mutableListOf<String>()
    private var historyIndex = -1
    private var scroll = 0

    private var nodes: Token? = null
    private var document: Layout? = null
    private var displayList = emptyList<Drawable>()
    private var rules = listOf<Pair<Selector, Map<String, String>>>()

    val title: String
        get() {
            nodes?.let { html ->
                if (html is Element && html.tag == "html") {
                    val head = html.children.firstOrNull { it is Element && it.tag == "head" }
                    if (head == null) {
                        return rawUrl
                    }

                    val title = head.children.firstOrNull { it is Element && it.tag == "title" }
                    if (title == null) {
                        return rawUrl
                    }

                    val text = title.children.firstOrNull()
                    if (text is Text) {
                        return text.text.trim()
                    }
                }
            }
            return rawUrl
        }

    fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        scroll = scroll.coerceIn(0, (document?.height ?: 0) - height)

        layout()
    }

    fun mouseClicked(button: Int, x: Int, y: Int) {
        val y = y + scroll

        val objects = document?.toList()?.filter {
            it.x <= x && it.x + it.width > x && it.y <= y && it.y + it.height > y
        }

        var element = objects?.lastOrNull()?.node
        while (element != null) {
            if (element is Text) {
                // pass
            } else if (element is Element && element.tag == "a" && "href" in element.attributes) {
                val href = element.attributes["href"]!!
                val url = url?.resolve(href.substringBefore("#"))
                val fragment = href.substringAfter("#", "")
                val targetUrl = "${(url?.toString() ?: "")}#$fragment"

                if (button == MouseEvent.BUTTON2) {
                    return browser.newTab(targetUrl)
                }
                return load(targetUrl)
            }
            element = element.parent
        }
    }

    fun canGoBack(): Boolean {
        return historyIndex >= 1
    }

    fun goBack() {
        if (historyIndex > 0) {
            historyIndex -= 1
            doLoad()
        }
    }

    fun canGoForward(): Boolean {
        return historyIndex < history.size - 1
    }

    fun goForward() {
        if (historyIndex < history.size - 1) {
            historyIndex += 1
            doLoad()
        }
    }

    fun reload() {
        doLoad()
    }

    fun load(url: String) {
        if (url != rawUrl) {
            history.add(url)
            historyIndex++
        }
        doLoad()
    }

    private fun doLoad() {
        val url = history[historyIndex]
        val showSource = url.startsWith("view-source:")
        this.rawUrl = url
        val parsedUrl = url.substringAfter("view-source:").substringBefore("#")
        val fragment = url.substringAfter("#", "")

        val response = try {
            this.url = URL(parsedUrl)
            getResponse(parsedUrl)
        } catch (e: Throwable) {
            this.url = null
            Response(200, mapOf(), "")
        }

        if (showSource) {
            showSource(response.body)
        } else {
            nodes = HtmlParser(response.body).parse()
            if (logger.isDebugEnabled()) {
                nodes?.printTree()
            }
            rules = defaultStyleSheet.toList()

            val links = nodes!!.treeToList()
                .filterIsInstance<Element>()
                .filter { it.tag == "link" && it.attributes["rel"] == "stylesheet" }
                .filter { "href" in it.attributes }
                .map { it.attributes["href"]!! }

            for (link in links) {
                val styleUrl = URL(parsedUrl).resolve(link)
                val body = client.request(Request(styleUrl)).body
                rules += CssParser(body).parse().toList()
            }

            nodes!!.style()
            layout()
            scroll = 0
            if (fragment.isNotBlank()) {
                val element = document!!.findId(fragment)
                if (element != null) {
                    scroll = element.y
                }
            }
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
                    val maxAge = maxAgePattern.find(response.headers["cache-control"]!!)?.groupValues[1]?.toInt() ?: 0
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

    private fun layout() {
        nodes?.run {
            document = DocumentLayout(this, width)
            document!!.layout()
            if (logger.isDebugEnabled()) {
                document!!.printTree()
            }
            displayList = document!!.paintTree()
        }
    }

    fun draw(graphics: Graphics, yOffset: Int = 0) {
        for (cmd in displayList) {
            if (cmd.top > scroll + height || cmd.bottom < scroll) {
                continue
            }
            cmd.execute(graphics, scroll - yOffset)
        }

        drawScrollBar(graphics, yOffset)
    }

    private fun drawScrollBar(graphics: Graphics, yOffset: Int) {
        if (height == 0 || (document?.height ?: 0) < height) {
            return
        }

        val scrollPosition = height * scroll / (document?.height ?: 1)
        val scrollBarHeight = height * height / (document?.height ?: 1)
        graphics.fillRect(width - 10, scrollPosition + yOffset, 10, scrollBarHeight)
    }

    fun scrollDown() {
        if (scroll >= (document?.height ?: 0) - height + 2 * vStep) {
            return
        }

        scroll += scrollStep
    }

    fun scrollUp() {
        if (scroll <= 0) {
            return
        }

        scroll -= scrollStep
    }


    private fun URL.createRequest(headers: Map<String, String> = mapOf()): Request {
        val allHeaders =
            mapOf("Host" to host, "Connection" to "keep-alive", "User-Agent" to "Browser from Scratch") + headers
        return Request(this, "GET", allHeaders)
    }


    private fun showSource(body: String) {
        println(body)
    }

    private fun Layout.findId(id: String): Layout? {
        val element = children.firstOrNull { it.node is Element && (it.node as Element).attributes["id"] == id }
        if (element != null) {
            return element
        }

        for (child in children) {
            val found = child.findId(id)
            if (found != null) {
                return found
            }
        }

        return null
    }

    private fun Token.style() {
        for ((property, defaultValue) in inheritedProperties) {
            style[property] = parent?.style[property] ?: defaultValue
        }

        for ((selector, body) in rules.sortedBy { it.first.cascadePriority() }) {
            if (!selector.matches(this)) {
                continue
            }
            style.putAll(body)
        }

        if (this is Element && "style" in attributes) {
            val pairs = CssParser(attributes["style"]!!).body()
            style.putAll(pairs)
        }

        if (style["font-size"]?.endsWith("%") == true) {
            val parentFontSize = if (parent != null) {
                parent!!.style["font-size"]
            } else {
                inheritedProperties["font-size"]
            }
            val nodePct = style["font-size"]?.dropLast(1)?.toFloat()?.div(100) ?: 1.0f
            val parentPx = parentFontSize?.dropLast(2)?.toFloat() ?: 16.0f
            style["font-size"] = "${(parentPx * nodePct).toInt()}px"
        }

        children.forEach { it.style() }
    }
}

private data class CachedResponse(val validUntil: Instant, val response: Response)
