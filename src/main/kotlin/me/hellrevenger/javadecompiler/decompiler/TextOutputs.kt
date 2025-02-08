package me.hellrevenger.javadecompiler.decompiler

import com.strobel.assembler.metadata.*
import com.strobel.decompiler.PlainTextOutput
import me.hellrevenger.javadecompiler.ui.KeyBoard
import me.hellrevenger.javadecompiler.ui.KeyEventDispatcher
import me.hellrevenger.javadecompiler.ui.MainWindow
import java.awt.event.KeyEvent
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.AbstractDocument.AbstractElement
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

class LinkableTextOutput(val className: String, val pane: JTextPane) : PlainTextOutput(), KeyEventDispatcher {
    companion object {
        val instances = mutableMapOf<String, LinkableTextOutput>()

        val underline = SimpleAttributeSet()
        val noUnderline = SimpleAttributeSet()
        init {
            StyleConstants.setUnderline(underline, true)
            StyleConstants.setUnderline(noUnderline, false)
        }
    }

    val links = hashMapOf<String, Pair<Int, Int>>()

    val stringBuilder = StringBuilder()
    var currentTextIndex = 0

    var needsIndent = false

    var counter = hashMapOf<String, Int>()

    var focusElement: AbstractElement? = null
        set(value) {
            changeFocus(field, value)
            field = value
        }


    init {
        instances[className] = this

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
            if(KeyBoard.isModifierPressed(KeyEvent.CTRL_DOWN_MASK)) {
                if(it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    links[it.description]?.let {
                        pane.select(it.first, it.second)
                        return@addHyperlinkListener
                    }
                    val delimiter = it.description.indexOf(".")
                    val className = if(delimiter == -1) it.description else it.description.substring(0, delimiter)
                    MainWindow.fileTree.openClass(className)?.let { pane ->
                        instances[className]?.links?.get(it.description)?.let {
                            pane.select(it.first, it.second)
                            return@addHyperlinkListener
                        }
                    }
                }
            }
            if(it.eventType == HyperlinkEvent.EventType.ENTERED) {
                focusElement = it.sourceElement as? AbstractElement
            } else if(it.eventType == HyperlinkEvent.EventType.EXITED) {
                focusElement = null
            }
        }

        KeyBoard.registerKeyEvent(this)
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
        val start = currentTextIndex + 1
        val end = currentTextIndex + text.length + 1

        (definition as? MethodDefinition)?.let {
            val def = "${it.fullName} ${it.signature}"
            links[def] = start to end
            processed = true
        }
        (definition as? FieldDefinition)?.let {
            val def = it.fullName
            links[def] = start to end
            processed = true
        }
        (definition as? ParameterDefinition)?.let {
            processed = true
        }
        (definition as? TypeDefinition)?.let {
            val def = it.fullName.replace(".", "/")
            links[def] = start to end
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
        (reference as? MethodReference)?.let {
            val ref = "${it.fullName} ${it.signature}"
            href(ref)
            processed = true
            hasHref = true
        }
        if(reference is PackageReference || reference is GenericParameter || className == "UnresolvedGenericType")
            processed = true
        (reference as? FieldReference)?.let {
            href(it.declaringType.toString().replace(".", "/") + "." + text)
            processed = true
            hasHref = true
        }
        (reference as? TypeReference)?.let {
            href(it.fullName.replace(".", "/"))
            processed = true
            hasHref = true
        }
        if(!processed) {
            span("ref", "/* ref ${className} */ ")
        }
        super.writeReference(text, reference)

        if(hasHref) endHref()
    }

    override fun writeKeyword(text: String?) {
        span("keyword")
        super.writeKeyword(text)
        endSpan()
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
    }

    fun changeFocus(old: AbstractElement?, new: AbstractElement?) {
        old?.let {
            (it.document as? DefaultStyledDocument)?.setCharacterAttributes(it.startOffset, it.endOffset - it.startOffset, noUnderline, false)
        }
        new?.let {
            if(KeyBoard.isModifierPressed(KeyEvent.CTRL_DOWN_MASK)) {
                (it.document as? DefaultStyledDocument)?.setCharacterAttributes(it.startOffset, it.endOffset - it.startOffset,
                    underline, false)
            }
        }
    }

    fun onClose() {
        instances.remove(className)
        KeyBoard.unregisterKeyEvent(this)
    }

    override fun onKey(event: KeyEvent) {
        changeFocus(focusElement, focusElement)
    }
}