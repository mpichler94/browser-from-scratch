package io.github.mpichler94.browser

import io.github.humbleui.jwm.App
import java.io.File

fun main(args: Array<String>) {
    println("JVM Name: " + System.getProperty("java.vm.name"))
    println("JVM Version: " + System.getProperty("java.version"))
    println("Java Home: " + System.getProperty("java.home"))

    Thread.currentThread().name = "Browser"

    App.start {
        val window = App.makeWindow()
        window.setVisible(true)
        val scale = window.screen.scale
        window.setWindowSize((800 * scale).toInt(), (600 * scale).toInt())
        window.setTitle("Browser")
        val browser = Browser(window, if (args.isNotEmpty()) args[0] else "https://example.com")
        window.setEventListener(browser)

        window.setIcon(File(Browser::class.java.getResource("/icon.png").toURI()))
        window.setVisible(true)

        window.requestFrame()
    }
}
