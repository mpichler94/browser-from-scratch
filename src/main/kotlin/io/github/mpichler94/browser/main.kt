package io.github.mpichler94.browser

import io.github.humbleui.jwm.App
import java.io.File

fun main(args: Array<String>) {

    println("JVM Name: " + System.getProperty("java.vm.name"))
    println("JVM Version: " + System.getProperty("java.version"))
    println("Java Home: " + System.getProperty("java.home"))

    App.start {
        val window = App.makeWindow()
        window.setTitle("Browser")
        val browser = Browser(window, if (args.isNotEmpty()) args[0] else "https://example.com")
        window.setEventListener(browser)
        val scale = window.screen.scale
        window.setWindowSize((800 * scale).toInt(), (600 * scale).toInt())

        window.setIcon(File(Browser::class.java.getResource("/icon.png").toURI()))
        window.setVisible(true)

        window.requestFrame()
    }
}
