package io.github.mpichler94.browser

import io.github.oshai.kotlinlogging.KotlinLogging

class CssParser(private val s: String) {
    private val logger = KotlinLogging.logger {}

    private var i = 0

    fun parse(): Map<Selector, Map<String, String>> {
        val rules = mutableMapOf<Selector, Map<String, String>>()
        while (i < s.length) {
            try {
                whitespace()
                val selector = combinator()
                literal('{')
                whitespace()
                val body = body()
                literal('}')
                rules[selector] = body
            } catch (e: Exception) {
                logger.debug(e) { "Error parsing CSS: ${e.message}" }
                val why = ignoreUntil('}')
                if (why == '}') {
                    literal('}')
                    whitespace()
                }
            }
        }
        return rules
    }

    fun body(): Map<String, String> {
        val pairs = mutableMapOf<String, String>()
        while (i < s.length && s[i] != '}') {
            try {
                val (prop, value) = pair()
                pairs[prop] = value
                whitespace()
                literal(';')
                whitespace()
            } catch (e: Exception) {
                logger.debug(e) { "Error parsing CSS: ${e.message}" }
                val why = ignoreUntil(';', '}')
                if (why == ';') {
                    literal(';')
                    whitespace()
                } else {
                    break
                }
            }
        }
        return pairs
    }

    private fun whitespace() {
        while (i < s.length && s[i].isWhitespace()) {
            i += 1
        }
    }

    private fun word(): String {
        val start = i
        while (i < s.length) {
            if (s[i].isLetterOrDigit() || s[i] in listOf('#', '-', '.', '%')) {
                i += 1
            } else {
                break
            }
        }
        check(i > start) { "Parsing error at $i" }
        return s.substring(start, i)
    }

    private fun literal(literal: Char) {
        check(i < s.length && s[i] == literal) { "Parsing error at $i" }
        i += 1
    }

    private fun pair(): Pair<String, String> {
        val prop = word()
        whitespace()
        literal(':')
        whitespace()
        val value = value()
        return prop.lowercase() to value
    }

    private fun value(): String {
        val start = i
        while (i < s.length) {
            if (s[i] !in listOf('\r', '\n', ';', '}')) {
                i += 1
            } else {
                break
            }
        }
        check(i > start) { "Parsing error at $i" }
        return s.substring(start, i).trim()
    }

    private fun ignoreUntil(vararg chars: Char): Char? {
        while (i < s.length) {
            if (s[i] in chars) {
                return s[i]
            } else {
                i += 1
            }
        }
        return null
    }

    private fun combinator(): Selector {
        var out = selector()
        whitespace()
        while (i < s.length && s[i] != '{') {
            if (s[i] == ',') {
                literal(',')
                whitespace()
                if (out is ListSelector) {
                    out.add(selector())
                } else {
                    out = ListSelector(out)
                }
                whitespace()
                continue
            }
            val descendant = selector()
            if (out is ListSelector) {
                out.add(descendant)
            } else {
                out = DescendantSelector(out, descendant)
            }
            whitespace()
        }
        return out
    }

    private fun selector(): Selector {
        val name = word()
        return if (name.contains('.')) {
            ClassSelector(name)
        } else {
            TagSelector(name)
        }
    }
}

interface Selector {
    val priority: Int
    fun matches(node: Token): Boolean
}

private class TagSelector(private val tag: String) : Selector {
    override val priority = 1

    override fun matches(node: Token): Boolean {
        return node is Element && tag == node.tag
    }

    override fun toString(): String = "<$tag>"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TagSelector

        return tag == other.tag
    }

    override fun hashCode(): Int = tag.hashCode()

}

private class DescendantSelector(private val ancestor: Selector, private val descendant: Selector) : Selector {
    override val priority = ancestor.priority + descendant.priority

    override fun matches(node: Token): Boolean {
        if (!descendant.matches(node)) {
            return false
        }
        var n: Token? = node
        while (n?.parent != null) {
            if (ancestor.matches(n.parent!!)) {
                return true
            }
            n = n.parent
        }
        return false
    }

    override fun toString(): String = "$ancestor $descendant"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DescendantSelector

        if (ancestor != other.ancestor) return false
        if (descendant != other.descendant) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ancestor.hashCode()
        result = 31 * result + descendant.hashCode()
        return result
    }
}

private class ClassSelector(name: String) : Selector {
    override val priority: Int

    val tag: String?
    val classes: List<String>

    init {
        var p = 0
        val tagName = name.substringBefore('.')
        if (tagName.isNotBlank()) {
            tag = tagName
            p = 1
        } else {
            tag = null
        }
        val classNames = name.substringAfter('.').split('.')
        classes = classNames.map { it.trim() }
        priority = p + classes.size
    }

    override fun matches(node: Token): Boolean {
        if (node is Element) {
            if (tag != null && tag != node.tag) {
                return false
            }
            if (node.attributes["class"]?.split(' ')?.all { it in classes } == true) {
                return true
            }
        }
        return false
    }

    override fun toString(): String = "$tag.${classes.joinToString(".")}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClassSelector

        if (tag != other.tag) return false
        if (classes != other.classes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tag?.hashCode() ?: 0
        result = 31 * result + classes.hashCode()
        return result
    }
}

private class ListSelector(vararg selectors: Selector) : Selector {
    private val selectors = mutableListOf(*selectors)

    override val priority: Int get() = selectors.maxOf { it.cascadePriority() }

    fun add(selector: Selector) {
        selectors.add(selector)
    }

    override fun matches(node: Token): Boolean {
        return selectors.any { it.matches(node) }
    }

    override fun toString(): String {
        return selectors.joinToString(", ")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ListSelector

        return selectors == other.selectors
    }

    override fun hashCode(): Int {
        return selectors.hashCode()
    }
}

fun Selector.cascadePriority(): Int {
    return priority
}