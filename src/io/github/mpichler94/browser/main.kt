package io.github.mpichler94.browser

import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    val frame = MainFrame()

    val browser = Browser()
    SwingUtilities.invokeLater {
        frame.add(browser, BorderLayout.CENTER)
        frame.revalidate()
    }

    browser.load(args[0])
}


class MainFrame : JFrame {
    constructor() : super("Browser") {
        size = Dimension(800, 600)
        layout = BorderLayout()

        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(800, 600)

        SwingUtilities.invokeLater {
            isVisible = true
        }
    }
}