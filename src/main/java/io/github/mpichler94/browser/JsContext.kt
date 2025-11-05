package io.github.mpichler94.browser

import io.github.oshai.kotlinlogging.KotlinLogging
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.SandboxPolicy
import org.graalvm.polyglot.TypeLiteral
import org.graalvm.polyglot.Value
import java.io.OutputStream

class JsContext(private val tab: Tab) {
    val logger = KotlinLogging.logger {}
    val context = Context.newBuilder("js")
//        .sandbox(SandboxPolicy.CONSTRAINED)
        .allowHostAccess(HostAccess.newBuilder()
            .allowAccessAnnotatedBy(HostAccess.Export::class.java)
            .allowArrayAccess(true)
            .allowListAccess(true)
            .allowMapAccess(true)
            .build())
        .out(OutputStream.nullOutputStream())
        .err(OutputStream.nullOutputStream())
    .build()

    val nodeToHandle = mutableMapOf<Element, Int>()
    val handleToNode = mutableMapOf<Int, Element>()

    init {
        context.getBindings("js").putMember("console", Console())
        context.getBindings("js").putMember("__document", Document())
        context.getBindings("js").putMember("tab", tab)

        val runtimeJs = JsContext::class.java.getResource("/runtime.js").readText()
        context.eval("js", runtimeJs)
    }

    fun run(code: String): Value {

        val result = try {
            context.eval("js", code)
        } catch (e: Exception) {
            logger.error(e) { "Error while executing JavaScript code" }
            Value.asValue("")
        }

        return result
    }

    fun dispatchEvent(type: String, element: Element): Boolean {
        val handle = nodeToHandle[element] ?: return false
        val value = run("new Node($handle).dispatchEvent(new Event(\"$type\"))")
        val doDefault = value.getMember("do_default")?.asBoolean() ?: true
        val propagate = value.getMember("propagate")?.asBoolean() ?: true

        if (!propagate) {
            return doDefault
        }

        val parent = element.parent
        if (parent == null || parent !is Element) {
            return doDefault
        }

        return doDefault && dispatchEvent(type, parent)
    }

    private fun Element.getHandle(): Int {
        return if (this !in nodeToHandle) {
            val handle = nodeToHandle.size
            nodeToHandle[this] = handle
            handleToNode[handle] = this
            handle
        } else {
            nodeToHandle[this]!!
        }
    }

    inner class Document {
        @HostAccess.Export
        fun querySelectorAll(selector: String): List<Int> {
            val selector = CssParser(selector).selector()

            return tab.nodes?.treeToList()
                ?.filterIsInstance<Element>()
                ?.filter { selector.matches(it) }
                ?.map { it.getHandle() } ?: emptyList()
        }

        @HostAccess.Export
        fun createElement(tag: String): Int {
            val elt = Element(tag, mutableMapOf(), null)
            val handle = elt.getHandle()
            return handle
        }

        @HostAccess.Export
        fun getAttribute(handle: Int, name: String): String {
            val elt = handleToNode[handle] ?: return ""
            val attr = elt.attributes[name]
            return attr ?: ""
        }

        @HostAccess.Export
        fun setInnerHTML(handle: Int, html: String) {
            val doc = HtmlParser("<html><body>$html</body></html>").parse()
            val newNodes = doc.children[0].children
            val elt = handleToNode[handle] ?: return
            elt.children.clear()
            elt.children.addAll(newNodes)
            for (child in elt.children) {
                child.parent = elt
            }
            tab.render()
        }

        @HostAccess.Export
        fun getInnerHTML(handle: Int): String {
            val elt = handleToNode[handle] ?: return ""
            return elt.children.joinToString("") {
                if (it is Element) {
                    it.serialize()
                } else {
                    it.toString()
                }
            }
        }

        private fun Token.serialize(): String {
            var result = this.toString()
            if (children.firstOrNull() is Element) {
                result += "\n"
            }
            result += children.joinToString("") { it.serialize() }
            if (this is Element) {
                if (tag in HtmlParser.selfClosingTags) {
                    return result + "\n"
                }
                if (children.isEmpty()) {
                    return result.replaceBeforeLast('>', "/>\n")
                }
                return result + "</${this.tag}>\n"
            }
            return result
        }

        @HostAccess.Export
        fun getOuterHTML(handle: Int): String {
            val elt = handleToNode[handle] ?: return ""
            return elt.serialize()
        }

        @HostAccess.Export
        fun getChildren(handle: Int): List<Int> {
            val elt = handleToNode[handle] ?: return emptyList()
            return elt.children.filterIsInstance<Element>().map { it.getHandle() }
        }

        @HostAccess.Export
        fun insertBefore(parentHandle: Int, childHandle: Int, referenceHandle: Int?) {
            val parent = handleToNode[parentHandle] ?: return
            val child = handleToNode[childHandle] ?: return
            if (referenceHandle == null) {
                parent.children.add(child)
            } else {
                val reference = handleToNode[referenceHandle] ?: return
                val index = parent.children.indexOf(reference)
                if (index == -1) return
                parent.children.add(index, child)
            }
            child.parent = parent
            tab.render()
        }

        @HostAccess.Export
        fun removeChild(parentHandle: Int, childHandle: Int): Int? {
            val parent = handleToNode[parentHandle] ?: return null
            val child = handleToNode[childHandle] ?: return null
            parent.children.remove(child)
            child.parent = null
            return childHandle
        }

        @HostAccess.Export
        fun getIDs(): Map<String, Int> {
            return tab.nodes?.treeToList()
                ?.filterIsInstance<Element>()
                ?.filter { "id" in it.attributes}
                ?.associate { it.attributes["id"]!! to it.getHandle() } ?: emptyMap()
        }
    }
}

class Console {
    @HostAccess.Export
    fun log(vararg args: Any) {
        println(args.joinToString(" "))
    }
}

class EventResult(val do_default: Boolean, val propagate: Boolean)