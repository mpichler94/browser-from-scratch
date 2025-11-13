package io.github.mpichler94.browser

import io.github.humbleui.jwm.Key
import io.github.humbleui.jwm.MouseButton
import io.github.humbleui.skija.Canvas
import io.github.humbleui.types.Point
import io.github.humbleui.types.Rect
import io.github.mpichler94.util.coerceIn
import io.github.mpichler94.browser.io.HttpClient
import io.github.mpichler94.browser.io.Response
import io.github.mpichler94.browser.io.URL
import io.github.mpichler94.browser.layout.DocumentLayout
import io.github.mpichler94.browser.layout.Layout
import io.github.mpichler94.browser.layout.paintTree
import io.github.mpichler94.browser.layout.printTree
import io.github.mpichler94.browser.layout.toList
import io.github.mpichler94.browser.render.Drawable
import io.github.mpichler94.browser.render.contains
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.URLEncoder
import javax.net.ssl.SSLException
import kotlin.math.min

class Tab(private val browser: Browser) {
    private val logger = KotlinLogging.logger {}
    private val client = HttpClient.instance

    private val defaultStyleSheet: Map<Selector, Map<String, String>> =
        CssParser(Tab::class.java.getResource("/browser.css")!!.readText()).parse()
    private val inheritedProperties = mapOf(
            "color" to "black",
            "font-size" to "16px",
            "font-style" to "normal",
            "font-weight" to "normal",
            "font-family" to "sans-serif"
        )

    private var width: Float = 0f
    private var height: Float = 0f
    private val vStep = 50

    private var rawUrl = "about:blank"
    internal var url: URL? = null
        private set
    internal var decoratedUrl: String = ""
        private set
    private var history = mutableListOf<String>()
    private var historyIndex = -1
    internal var scroll = 0f
    private set

    internal var nodes: Token? = null
        private set
    internal var document: Layout? = null
        private set
    private var displayList = emptyList<Drawable>()
    private var rules = listOf<Pair<Selector, Map<String, String>>>()
    private var focus: Element? = null
    private var js: JsContext? = null
    private val allowedOrigins: MutableSet<String> = mutableSetOf()

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

    fun resize(width: Float, height: Float) {
        this.width = width
        this.height = height
        val documentHeight = document?.height ?: 0f
        if (documentHeight > height) {
            scroll = scroll.coerceIn(0f, (document?.height ?: 0f) - height)
        } else {
            scroll = 0f
        }

        layout()
    }

    fun mouseClicked(button: MouseButton, x: Float, y: Float) {
        focus?.let { it.isFocused = false }

        val y = y + scroll

        val objects = document?.toList()?.filter {
            val radius = it.node.style["border-radius"]?.dropLast(2)?.toIntOrNull() ?: 0
            isPointInRoundedRect(Point(x, y), it.rect, radius.toFloat())
        }

        var element = objects?.lastOrNull()?.node
        while (element != null) {
            if (element is Text) {
                // pass
            } else if (element is Element && element.tag == "a" && "href" in element.attributes) {
                if (js?.dispatchEvent("click", element) == true) return
                val href = element.attributes["href"]!!
                val url = url?.resolve(href.substringBefore("#"))
                val fragment = href.substringAfter("#", "")
                val targetUrl = if (fragment.isNotBlank()) {
                    "${(url?.toString() ?: "")}#$fragment"
                } else {
                    (url?.toString() ?: "")
                }

                if (button == MouseButton.MIDDLE) {
                    return browser.newTab(targetUrl)
                }
                return load(targetUrl)
            } else if (element is Element && element.tag == "input") {
                if (js?.dispatchEvent("click", element) == true) return
                element.attributes["value"] = ""
                focus = element
                element.isFocused = true
            } else if (element is Element && element.tag == "button") {
                if (js?.dispatchEvent("click", element) == true) return
                while (element != null) {
                    if (element is Element && element.tag == "form" && "action" in element.attributes) {
                        return submitForm(element)
                    }
                    element = element.parent
                }
            }
            element = element?.parent
        }
        render()
    }

    private fun isPointInRoundedRect(point: Point, rect: Rect, radius: Float): Boolean {
        if (!rect.contains(point)) {
            return false
        }

        val radius = radius.coerceIn(0f, min(rect.width, rect.height) / 2f)
        val point = point.offset(-rect.left, -rect.top)

        // inside central rectanble
        if (point.x >= radius && point.x <= rect.width - radius) {
            return true
        }
        if (point.y >= radius && point.y <= rect.height - radius) {
            return true
        }

        // check corners
        val cornerX = if (point.x < radius) radius else rect.width - radius
        val cornerY = if (point.y < radius) radius else rect.height - radius
        val dx = point.x - cornerX
        val dy = point.y - cornerY
        return (dx * dx + dy * dy) <= (radius * radius)
    }

    fun keyPressed(keyCode: Key) {
        if (keyCode == Key.ENTER) {
            if (focus != null) {
                var element = focus!!.parent
                while (element != null) {
                    if (element is Element && element.tag == "form" && "action" in element.attributes) {
                        return submitForm(element)
                    }
                    element = element.parent
                }
            }
        }
    }

    fun keyTyped(key: Char) {
        if (focus != null) {
            if (js?.dispatchEvent("keydown", focus!!) == true) return
            focus!!.attributes["value"] = focus!!.attributes["value"] + key
            render()
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

    fun load(url: String, body: String? = null) {
        if (url != rawUrl) {
            history.add(url)
            historyIndex++
        }
        doLoad(body)
    }

    fun blur() {
        focus = null
    }

    private fun doLoad(body: String? = null) {
        val url = history[historyIndex]
        val showSource = url.startsWith("view-source:")
        rawUrl = url
        decoratedUrl = url
        val parsedUrl = url.substringAfter("view-source:").substringBefore("#")
        val fragment = url.substringAfter("#", "")
        allowedOrigins.clear()


            this.url = URL(parsedUrl)
            if (this.url?.scheme == "https") {
                decoratedUrl = "\uD83D\uDD12 $url"
            }
        val response =    getResponse(parsedUrl, body)


        if("content-security-policy" in response.headers) {
            val csp = response.headers["content-security-policy"]!!.split(' ')
            if (csp.isNotEmpty() && csp.first() == "default-src") {
                allowedOrigins.clear()
                for (origin in csp.drop(1)) {
                    allowedOrigins.add(URL(origin).origin)
                }
            }
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
                val body = getResponse(styleUrl.toString()).body
                rules += CssParser(body).parse().toList()
            }

            val scripts = nodes!!.treeToList()
                .filterIsInstance<Element>()
                .filter { it.tag == "script" && "src" in it.attributes }
                .map { it.attributes["src"]!! }

            js = JsContext(this)
            for (script in scripts) {
                val scriptUrl = URL(parsedUrl).resolve(script)
                val body = getResponse(scriptUrl.toString()).body

                js?.run(body)
            }

            nodes!!.style()
            layout()
            scroll = 0f
            if (fragment.isNotBlank()) {
                val element = document?.findId(fragment)
                if (element != null) {
                    scroll = element.y
                }
            }
        }
    }

    private fun getResponse(url: String, body: String? = null): Response {
        val parsedUrl = URL(url)

        if (allowedOrigins.isNotEmpty() && parsedUrl.origin !in allowedOrigins) {
            logger.warn { "Blocked request $url due to CSP" }
            return Response(403, mapOf(), "")
        }

        return if (parsedUrl.scheme == "data") {
            Response(body = url.substringAfter(","))
        } else if (parsedUrl.scheme == "file") {
            Response(body = File(parsedUrl.path).readText())
        } else {
            try {
                val response = if (body != null) {
                    client.request(parsedUrl.createRequest("POST", this.url, body))
                } else {
                    client.request(parsedUrl.createRequest(referrer = this.url))
                }
                response
            } catch (e: SSLException) {
                this.url = null
                decoratedUrl = "Unsafe $url"
                Response(200, mapOf(), "<h1>Certificate invalid</h1>")
            } catch (e: Throwable) {
                logger.error(e) { "Error while requesting $url" }
                Response(444, body = "")
            }
        }
    }

    private fun layout() {
        if (width == 0f) {
            return
        }
        nodes?.run {
            document = DocumentLayout(this, width)
            document!!.layout()
            if (logger.isDebugEnabled()) {
                document!!.printTree()
            }
            displayList = document!!.paintTree()
        }
    }

    internal fun render() {
        nodes!!.style()
        layout()
    }

    fun raster(canvas: Canvas, scale: Float) {
        for (cmd in displayList) {
            cmd.execute(canvas, scale)
        }
    }



    fun scroll(delta: Float) {
        if (delta < 0) { // scroll down
            val maxscroll = (document?.height ?: 0f) - height + 2f * vStep
            if (scroll >= maxscroll) {
                return
            }
            scroll = (scroll - delta).coerceAtMost(maxscroll)
        } else {
            if (scroll <= 0) {
                return
            }
            scroll = (scroll - delta).coerceAtLeast(0f)
        }
    }

    private fun submitForm(form: Element) {
        if (js?.dispatchEvent("submit", form) == true) return

        val inputs = form.treeToList()
            .filterIsInstance<Element>()
            .filter { it.tag == "input" && "name" in it.attributes }

        val body = inputs.map {
            val name = URLEncoder.encode(it.attributes["name"]!!, Charsets.UTF_8).replace("+", "%20")
            val value = URLEncoder.encode(it.attributes["value"] ?: "", Charsets.UTF_8).replace("+", "%20")
            "$name=$value"
        }.joinToString("&")
        val url = url!!.resolve(form.attributes["action"]!!)
        load(url.toString(), body)
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
            val nodePct = style["font-size"]?.dropLast(1)?.toFloatOrNull()?.div(100) ?: 1.0f
            val parentPx = parentFontSize?.dropLast(2)?.toFloatOrNull() ?: 16.0f
            style["font-size"] = "${(parentPx * nodePct).toInt()}px"
        }

        children.forEach { it.style() }
    }
}

