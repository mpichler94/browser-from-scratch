package io.github.mpichler94.browser

import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    val frame = MainFrame()

    val browser = Browser(args[0])
    SwingUtilities.invokeLater {
        frame.add(browser, BorderLayout.CENTER)
        frame.revalidate()
    }

    val url = URL("https://browser.engineering/book.css")
    val body = HttpClient().request(Request(url)).body
//    val body = """
//        html { font-size: 24px; line-height: 1.2; padding: 1em; }
//        body {
//            max-width: 60ch; margin: 0 auto; font-family: 'Crimson Pro', 'Times', serif;
//            font-weight: normal; text-align: justify; hyphens: auto; -webkit-hyphens: auto;
//        }
//        pre, code { hyphens: none; -webkit-hyphens: none; font-family: 'Inconsolata', monospace; }
//    """.trimIndent()
    val style = CssParser(body).parse()
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