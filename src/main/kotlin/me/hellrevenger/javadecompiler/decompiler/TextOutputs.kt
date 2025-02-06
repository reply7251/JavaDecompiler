package me.hellrevenger.javadecompiler.decompiler

import com.strobel.assembler.metadata.*
import com.strobel.decompiler.PlainTextOutput
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

class LinkableTextOutput(val pane: JTextPane) : PlainTextOutput() {
    companion object {
        val instances = mutableSetOf<LinkableTextOutput>()
    }

    val links = hashMapOf<String, Pair<Int, Int>>()

    val stringBuilder = StringBuilder()
    var currentTextIndex = 0

    var needsIndent = false

    var counter = hashMapOf<String, Int>()


    init {
        instances.add(this)

        pane.isEditable = false

        val html = HTMLEditorKit()
        val styleSheet = StyleSheet()
        styleSheet.addRule("span.comment { color: #bbbbbb; }")
        styleSheet.addRule("span.text { color: #56aa5f; }")
        styleSheet.addRule("span.keyword { color: #e28743; }")
        styleSheet.addRule("pre { color: #ffffff; }")
        styleSheet.addRule("span.def { color: #1e81b0; }")
        styleSheet.addRule("span.ref { color: #76b5c5; }")
        html.styleSheet = styleSheet

        pane.editorKit = html
        pane.document = html.createDefaultDocument()

        pane.addHyperlinkListener {
            if(it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                links[it.description]?.let {
                    pane.select(it.first, it.second)
                }
            }
        }
    }

    override fun write(ch: Char) {
        super.write(ch)
        appendVisual(ch.toString())
    }

    override fun write(text: String) {
        super.write(text)
        appendVisual(text)
    }

    override fun writeLine() {
        super.writeLine()
        appendVisual("\n")
        needsIndent = true
    }

    override fun writeIndent() {
        super.writeIndent()
        if(needsIndent) {
            appendVisual(" ".repeat(indentDepth() * 2))
            needsIndent = false
        }
    }

    override fun writeAttribute(text: String?) {
        span("comment", "/* attr */ ")
        super.writeAttribute(text)
        count("attr")
    }

    override fun writeComment(value: String?) {
        span("comment")
        super.writeComment(value)
        endSpan()
    }

    override fun writeTextLiteral(value: Any?) {
        span("text")
        super.writeTextLiteral(value)
        endSpan()
    }

    override fun writeDefinition(text: String, definition: Any, isLocal: Boolean) {
        val className = definition::class.java.simpleName
        var processed = false

        (definition as? MethodDefinition)?.let {
            val def = "${it.fullName} ${it.signature}"
            links[def] = currentTextIndex + 1 to currentTextIndex + text.length + 1

            processed = true
        }
        (definition as? FieldDefinition)?.let {
            val def = it.fullName
            //span("def", "/* $def */ ")
            links[def] = currentTextIndex + 1 to currentTextIndex + text.length + 1
            processed = true
        }
        (definition as? ParameterDefinition)?.let {
            span("def", "/* param */ ")
            processed = true
        }
        if(!processed) {
            span("def", "/* def ${className} */ ")
        }
        super.writeDefinition(text, definition, isLocal)
    }

    override fun writeReference(text: String?, reference: Any) {
        val className = reference::class.java.simpleName
        var processed = false
        var hasHref = false
        (reference as? MethodDefinition)?.let {
            val ref = "${it.fullName} ${it.signature}"
            href(ref)
            processed = true
            hasHref = true
        }
        if(reference is PackageReference || className == "UnresolvedGenericType")
            processed = true
        (reference as? FieldReference)?.let {
            href(it.declaringType.toString().replace(".", "/") + "." + text)
            processed = true
            hasHref = true
        }
        if(!processed) {
            span("ref", "/* ref ${className} */ ")
        }
        super.writeReference(text, reference)

        if(hasHref) endHref()
    }

    fun href(target: String) = append("<a href=\"$target\">")
    fun endHref() = append("</a>")
    fun span(elementClass: String) = append("<span class=\"$elementClass\">")
    fun endSpan() = append("</span>")
    fun span(elementClass: String, content: String) {
        span(elementClass)
        appendVisual(content)
        endSpan()
    }

    fun append(html: String) {
        stringBuilder.append(html)
    }

    fun appendVisual(text: String) {
        stringBuilder.append(text.replace("<", "&lt;"))
        currentTextIndex += text.length
    }

    fun count(key: String) {
        counter[key] = (counter[key] ?: 0) + 1
    }


    fun flush() {
        pane.text = "<pre>$stringBuilder</pre>"

        println(stringBuilder)
        println(currentTextIndex)
        counter.forEach { t, u ->
            println("$t: $u")
        }
    }
}