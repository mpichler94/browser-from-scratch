package io.github.mpichler94.browser

import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    val frame = MainFrame()

    println("JVM Name: " + System.getProperty("java.vm.name"))
    println("JVM Version: " + System.getProperty("java.version"))
    println("Java Home: " + System.getProperty("java.home"))

    val browser = Browser(if (args.isNotEmpty()) args[0] else "https://example.com")
    SwingUtilities.invokeLater {
        frame.add(browser, BorderLayout.CENTER)
        frame.revalidate()
    }
}


class MainFrame : JFrame {
    constructor() : super("Browser") {
        size = Dimension(800, 600)
        iconImage = ImageIcon(MainFrame::class.java.getResource("/icon.png")).image
        layout = BorderLayout()

        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(800, 600)

        SwingUtilities.invokeLater {
            isVisible = true
        }
    }
}