package io.github.mpichler94.browser

fun main(args: Array<String>) {
    load(args[0])
}

fun load(url: String) {
    if (url.startsWith("view-source:")) {
        val parsedUrl = URL(url.substringAfter("view-source:"))
        val body = parsedUrl.request()
        showSource(body)
        return
    }
    val parsedUrl = URL(url)
    val body = parsedUrl.request()
    show(body)
}

fun show(body: String) {
    var inTag = false
    var inEntity = false
    var entity = ""
    for(c in body) {
        when(c) {
            '<' -> inTag = true
            '>' -> inTag = false
            '&' -> inEntity = true
            ';' -> {
                inEntity = false
                print(entities[entity] ?: "")
                entity = ""
            }
            else if (inEntity) -> entity += c
            else if (!inTag) -> print(c)
        }
    }
}

fun showSource(body: String) {
    println(body)
}

private val entities = mapOf("lt" to "<", "gt" to ">")