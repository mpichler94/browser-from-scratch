package io.github.mpichler94.browser

import java.awt.Color
import java.awt.Graphics
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.Executors
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.text.contains
import kotlin.text.split

class Browser(url: String) : JPanel() {
    val tabs = mutableListOf<Tab>()
    var activeTab: Tab? = null
        set(value) {
            field = value
            value?.resize(width, height)
            repaint()
        }
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Worker") }
    private val chrome = Chrome(this)
    private var focus: String? = null

    init {
        layout = null
        background = Color.WHITE
        isOpaque

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher {
            if (it.id == KeyEvent.KEY_PRESSED) {
                when (it.keyCode) {
                    KeyEvent.VK_DOWN -> activeTab?.scrollDown()
                    KeyEvent.VK_UP -> activeTab?.scrollUp()
                    KeyEvent.VK_F5 -> activeTab?.reload()
                    else -> {
                        val consumed = chrome.keyPressed(it.keyCode)
                        if (!consumed && focus == "content") {
                            activeTab?.keyPressed(it.keyCode)
                        }
                    }
                }
                repaint()
            } else if (it.id == KeyEvent.KEY_TYPED && it.keyChar.code in 0x20..0x7e) {
                val consumed = chrome.keyTyped(it.keyChar)
                if (!consumed && focus == "content") {
                    activeTab?.keyTyped(it.keyChar)
                }
            }
            false
        }

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.y < chrome.bottom) {
                    activeTab?.blur()
                    chrome.mouseClicked(e.button, e.x, e.y)
                    focus = null
                } else {
                    focus = "content"
                    chrome.blur()
                    activeTab?.mouseClicked(e.button, e.x, e.y - chrome.bottom)
                }
                repaint()
            }
        })

        addMouseWheelListener {
            if (it.wheelRotation > 0) {
                activeTab?.scrollDown()
            } else {
                activeTab?.scrollUp()
            }
            repaint()
        }

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                executor.submit {
                    chrome.resize()
                    activeTab?.resize(width, height - chrome.bottom)
                }
            }
        })

        newTab(url)
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

    override fun addNotify() {
        super.addNotify()

        executor.submit {
            chrome.resize()
            activeTab?.resize(width, height)
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        setTitle()

        activeTab?.draw(g, chrome.bottom)

        chrome.paint().forEach { it.execute(g, 0) }
    }

    private fun setTitle() {
        var parent = parent
        while (parent != null && parent !is JFrame) {
            parent = parent.parent
        }
        parent?.title = activeTab?.title ?: "Browser"
    }
}

internal fun URL.createRequest(
    method: String = "GET",
    referrer: URL? = null,
    body: String? = null
): Request {
    val additionalHeaders = mutableMapOf("Host" to host, "Connection" to "keep-alive", "User-Agent" to "Browser from Scratch")
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

