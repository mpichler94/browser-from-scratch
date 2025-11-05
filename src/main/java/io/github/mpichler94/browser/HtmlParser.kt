package io.github.mpichler94.browser

class HtmlParser(private val body: String) {
    companion object {
        internal val selfClosingTags = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"
        )
    }

    private val entities = mapOf("lt" to "<", "gt" to ">")
    private val headTags = setOf("base", "basefont", "bgsound", "noscript", "link", "meta", "title", "style", "script")
    private val unfinished = mutableListOf<Element>()


    fun parse(): Token {
        var inTag = false
        var inEntity = false
        var inScript: Boolean
        var inAttribute = false
        var entity = ""
        var buffer = ""
        for (c in body) {
            inScript = unfinished.lastOrNull()?.tag == "script"
            when (c) {
                '"' if (inTag) -> {
                    inAttribute = !inAttribute
                    buffer += c
                }

                '<' if (!inAttribute && !inScript) -> {
                    inTag = true
                    if (buffer.isNotBlank()) {
                        addText(buffer)
                    }
                    buffer = ""
                }

                '>' if (!inAttribute) -> {
                    if (inScript) {
                        if (!buffer.endsWith("</script")) {
                            buffer += c
                            continue
                        }
                        addText(buffer.substringBeforeLast("</script"))
                        addTag("/script")
                        buffer = ""
                        continue
                    }
                    val inComment = buffer.startsWith("!--")
                    if (inComment && !buffer.endsWith("--")) {
                        continue
                    }
                    inTag = false
                    if (buffer.isNotBlank() && !inComment) {
                        addTag(buffer)
                    }
                    buffer = ""
                }

                '&' if (!inTag && !inAttribute && !inScript) -> inEntity = true
                ';' if (inEntity) -> {
                    inEntity = false
                    buffer += entities[entity] ?: entity
                    entity = ""
                }

                else if (inEntity) -> entity += c
                else -> buffer += c
            }
        }
        if (!inTag && buffer.isNotEmpty()) {
            addText(buffer)
        }
        return finish()
    }

    private fun addText(text: String) {
        if (text.isBlank()) {
            return
        }
        implicitTags()

        val parent = unfinished.lastOrNull()
        val node = Text(text, parent)
        parent?.children?.add(node)
    }

    private fun addTag(text: String) {
        val trimmedText = text.trimEnd('/')
        val (tag, attributes) = getAttributes(trimmedText)
        if (tag.startsWith("!")) {
            return
        }
        implicitTags(tag)

        if (tag.startsWith("/")) {
            if (unfinished.size == 1) {
                return
            }

            val node = unfinished.removeLast()
            val parent = unfinished.lastOrNull()
            parent?.children?.add(node)
        } else if (tag in selfClosingTags || text.endsWith("/")) {
            val parent = unfinished.lastOrNull()
            val node = Element(tag, attributes, parent)
            parent?.children?.add(node)

        } else {
            val parent = unfinished.lastOrNull()
            val node = Element(tag, attributes, parent)
            unfinished.add(node)
        }
    }

    private fun finish(): Token {
        if (unfinished.isEmpty()) {
            implicitTags()
        }

        while (unfinished.size > 1) {
            val node = unfinished.removeLast()
            val parent = unfinished.lastOrNull()
            parent?.children?.add(node)
        }
        return unfinished.removeLast()
    }

    private fun getAttributes(text: String): Tag {
        val parts = text.split(' ', '\n', '\t', '\r')
        val tag = parts[0].lowercase().trim()
        val attributes = mutableMapOf<String, String>()
        for (pair in parts.subList(1, parts.size)) {
            if (pair.contains('=')) {
                val (key, value) = pair.split('=', limit = 2)
                attributes[key.lowercase().trim()] = value.trim().trim('"', '\'')
            } else {
                attributes[pair.lowercase().trim()] = ""
            }
        }
        return Tag(tag, attributes)
    }

    private fun implicitTags(tag: String? = null) {
        while (true) {
            val openTags = unfinished.map { it.tag }
            when {
                openTags.isEmpty() && tag != "html" -> {
                    addTag("html")
                }

                openTags == listOf("html") && tag !in listOf("head", "body", "/html") -> {
                    if (tag in headTags) {
                        addTag("head")
                    } else {
                        addTag("body")
                    }
                }

                openTags == listOf("html", "head") && tag !in headTags.plus("/head") -> {
                    addTag("/head")
                }

                openTags.lastOrNull() == "p" && tag == "p" -> {
                    addTag("/p")
                }

                openTags.lastOrNull() == "li" && tag == "li" -> {
                    addTag("/li")
                }

                else -> {
                    break
                }
            }
        }
    }
}

fun Token.printTree(indent: Int = 0) {
    print(" ".repeat(indent))
    println(this)
    children.forEach { it.printTree(indent + 2) }
    if (this is Element && children.isNotEmpty()) {
        println(" ".repeat(indent) + "</${this.tag}>")
    }
}

fun Token.treeToList(): List<Token> {
    val list = mutableListOf<Token>()
    list.add(this)
    children.forEach { list.addAll(it.treeToList()) }
    return list
}

sealed interface Token {
    var parent: Token?
    val children: MutableList<Token>
    val style: MutableMap<String, String>
    var isFocused: Boolean
}

private data class Tag(val tag: String, val attributes: MutableMap<String, String>)

class Text(val text: String, override var parent: Token?) : Token {
    override val children = mutableListOf<Token>()
    override val style = mutableMapOf<String, String>()
    override var isFocused = false

    override fun toString(): String {
        return text
    }
}

class Element(val tag: String, val attributes: MutableMap<String, String>, override var parent: Token?) : Token {
    override val children = mutableListOf<Token>()
    override val style = mutableMapOf<String, String>()
    override var isFocused = false

    override fun toString(): String {
        val attrString = attributes.map {
            if (it.value.isBlank()) {
                it.key
            } else {
                "${it.key}=\"${it.value}\""
            }
        }.joinToString(" ")
        return "<$tag${if (attrString.isBlank()) "" else " "}$attrString>"
    }

}