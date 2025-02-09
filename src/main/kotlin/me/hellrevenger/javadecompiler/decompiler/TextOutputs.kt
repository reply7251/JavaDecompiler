package me.hellrevenger.javadecompiler.decompiler

import com.strobel.assembler.metadata.*
import com.strobel.decompiler.PlainTextOutput
import me.hellrevenger.javadecompiler.ui.HyperLinkRightClickHandler
import me.hellrevenger.javadecompiler.ui.KeyBoard
import me.hellrevenger.javadecompiler.ui.KeyEventDispatcher
import me.hellrevenger.javadecompiler.ui.MainWindow
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.io.Writer
import javax.swing.AbstractAction
import javax.swing.JPopupMenu
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.*
import javax.swing.text.AbstractDocument.AbstractElement
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

val literalRegex = "[a-zA-Z_][\\w\$]*".toRegex()

class LinkableTextOutput(val className: String, val pane: JTextPane) : PlainTextOutput(Writer.nullWriter()), KeyEventDispatcher {
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

    val highlightPainter = DefaultHighlighter.DefaultHighlightPainter(Color.decode("0x606060"))


    var literal = true
    val literals = hashMapOf<String, HashSet<Int>>()



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
            if(it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                tryHighlight(it)
                if(KeyBoard.isModifierPressed(KeyEvent.CTRL_DOWN_MASK) && !it.description.startsWith("!")) {
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
        pane.addMouseListener(HyperLinkRightClickHandler { e, element, href ->
            val popup = JPopupMenu()
            popup.add(object : AbstractAction("Analyze") {
                override fun actionPerformed(e: ActionEvent?) {
                    MainWindow.analyzer.analyze(href)
                }
            })

            popup.setLocation(e.xOnScreen, e.yOnScreen)
            popup.invoker = pane
            popup.isVisible = true
        })

        KeyBoard.registerKeyEvent(this)
        pane.highlighter = DefaultHighlighter()

        pane.caret = object : DefaultCaret() {
            override fun setSelectionVisible(vis: Boolean) {
                super.setSelectionVisible(true)
            }
        }
    }

    override fun write(ch: Char) {
        super.write(ch)
        appendVisual(ch.toString())
    }

    override fun write(text: String) {
        super.write(text)
        var hasLiteral = false
        if(literal && literalRegex.matches(text)) {
            literals.computeIfAbsent(text) { hashSetOf() }.add(currentTextIndex)
            href("!$text")
            hasLiteral = true
            // why procyon doesn't handle variables and method references (::), just treat them as identifier
        }
        appendVisual(text)
        if(hasLiteral) endHref()
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
        literal = false
        super.writeAttribute(text)
        literal = true
        count("attr")
    }

    override fun writeComment(value: String?) {
        span("comment")
        literal = false
        super.writeComment(value)
        literal = true
        endSpan()
    }

    override fun writeTextLiteral(value: Any?) {
        span("text")
        literal = false
        super.writeTextLiteral(value)
        literal = true
        endSpan()
    }

    override fun writeDefinition(text: String, definition: Any, isLocal: Boolean) {
        val className = definition::class.java.simpleName
        var processed = false
        val start = currentTextIndex + 1
        val end = currentTextIndex + text.length + 1
        var hasHref = false

        (definition as? MethodReference)?.let {
            val type = it.declaringType.fullName.replace(".", "/")
            val name = "${it.name} ${it.signature}"
            val def = "$type.$name"
            links[def] = start to end
            processed = true
            href("!$def")
            hasHref = true
        }
        (definition as? FieldDefinition)?.let {
            val type = it.declaringType.fullName.replace(".", "/")
            val name = it.name
            val def = "$type.$name"
            links[def] = start to end
            href("!$def")
            processed = true
            hasHref = true
        }
        (definition as? ParameterDefinition)?.let {
            href("!" + it.name)
            hasHref = true
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
        literal = false
        super.writeDefinition(text, definition, isLocal)
        literal = true

        if(hasHref) endHref()
    }

    override fun writeReference(text: String?, reference: Any) {
        val className = reference::class.java.simpleName
        var processed = false
        var hasHref = false
        (reference as? MethodReference)?.let {
            val type = it.declaringType.fullName.replace(".", "/")
            val name = "${it.name} ${it.signature}"
            val ref = "$type.$name"
            href(ref)
            processed = true
            hasHref = true
        }
        if(reference is PackageReference || reference is GenericParameter || className == "UnresolvedGenericType")
            processed = true
        (reference as? FieldReference)?.let {
            val type = it.declaringType.fullName.replace(".", "/")
            val name = it.name
            val ref = "$type.$name"
            href(ref)
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
        literal = false
        super.writeReference(text, reference)
        literal = true

        if(hasHref) endHref()
    }

    override fun writeKeyword(text: String?) {
        span("keyword")
        literal = false
        super.writeKeyword(text)
        literal = true
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

                var startOffset = it.startOffset
                while (it.document.getText(startOffset, 1) == " ") startOffset++
                (it.document as? DefaultStyledDocument)?.setCharacterAttributes(startOffset, it.endOffset - startOffset,
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

    fun sameHref(href1: String, href2: String): Boolean {
        val href1 = if(href1.startsWith("!")) href1.substring(1) else href1
        val href2 = if(href2.startsWith("!")) href2.substring(1) else href2
        return href1 == href2
    }

    fun tryHighlight(e: HyperlinkEvent) {
        (pane.document as? HTMLDocument)?.let { doc ->
            pane.highlighter.removeAllHighlights()
            val iter = ElementIterator(doc)
            while (iter.next() != null) {
                val elem = iter.current()
                (elem.attributes.getAttribute(HTML.Tag.A) as? AttributeSet)?.let {
                    val href = it.getAttribute(HTML.Attribute.HREF) as String
                    if(sameHref(href, e.description)) {
                        var startOffset = elem.startOffset
                        while (doc.getText(startOffset, 1) == " ") startOffset++
                        pane.highlighter.addHighlight(startOffset, elem.endOffset, highlightPainter)
                    }
                }
            }
            links[e.description]?.let {
                pane.highlighter.addHighlight(it.first, it.second, highlightPainter)
            }
        }
    }
}

class FullScanTextOutput : PlainTextOutput(Writer.nullWriter()) {
    var currentUsage : HasUsage? = null
    val types = hashMapOf<String, JavaType>()
    var lastKeyword = ""

    fun getType(name: String) = types.computeIfAbsent(name) { JavaType(name) }

    override fun write(text: String) {
        if(text == "{" && lastKeyword == "static") {
            currentUsage = null
        }
    }

    override fun writeKeyword(text: String) {
        lastKeyword = text
    }

    override fun writeDefinition(text: String, definition: Any, isLocal: Boolean) {
        lastKeyword = ""
        (definition as? MethodReference)?.let {
            val type = getType(it.declaringType.fullName.replace(".", "/"))
            val def = "${it.name} ${it.signature}"
            currentUsage = type.getMethod(def)
        }
        (definition as? FieldDefinition)?.let {
            val def = it.name
            val type = getType(it.declaringType.fullName.replace(".", "/"))
            currentUsage = type.getField(def)
        }
        (definition as? TypeDefinition)?.let {
            val def = it.fullName.replace(".", "/")
            currentUsage = getType(def)
        }
        super.writeDefinition(text, definition, isLocal)
    }

    override fun writeReference(text: String?, reference: Any) {
        (reference as? MethodReference)?.let {
            val type = getType(it.declaringType.fullName.replace(".", "/"))
            val ref = "${it.name} ${it.signature}"
            currentUsage?.addUsage(type.getMethod(ref))
        }
        (reference as? FieldReference)?.let {
            val type = getType(it.declaringType.fullName.replace(".", "/"))
            val ref = it.name
            currentUsage?.addUsage(type.getField(ref))
        }
        (reference as? TypeReference)?.let {
            val type = getType(it.fullName.replace(".", "/"))
            currentUsage?.addUsage(type)
        }
        super.writeReference(text, reference)
    }

    abstract class HasUsage {
        val uses = hashSetOf<HasUsage>()
        val usedBy = hashSetOf<HasUsage>()

        fun addUsage(target: HasUsage) {
            uses.add(target)
            target.usedBy.add(this)
        }
    }
    class JavaType(val name: String): HasUsage() {
        val fields = hashMapOf<String, JavaMember>()
        val methods = hashMapOf<String, JavaMember>()
        fun getMethod(name: String) = methods.computeIfAbsent(name) { JavaMember(this, name) }
        fun getField(name: String)  = fields.computeIfAbsent(name) { JavaMember(this, name) }
        override fun toString(): String {
            return name
        }
    }
    class JavaMember(val owner: JavaType, val name: String) : HasUsage() {
        override fun toString(): String {
            return "$owner.$name"
        }
    }
}