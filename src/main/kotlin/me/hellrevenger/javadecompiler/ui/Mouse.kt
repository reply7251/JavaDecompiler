package me.hellrevenger.javadecompiler.ui

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTextPane
import javax.swing.text.AttributeSet
import javax.swing.text.Element
import javax.swing.text.Position.Bias
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument

class HyperLinkRightClickHandler(val trigger: (e: MouseEvent, element: Element, href: String) -> Unit) : MouseAdapter() {
    companion object {
        val discardBias = arrayOfNulls<Bias>(1)
    }

    override fun mouseClicked(e: MouseEvent) {
        if(e.button == MouseEvent.BUTTON3) {
            val elem = getElement(e) ?: return
            val attrs = elem.attributes.getAttribute(HTML.Tag.A) as? AttributeSet ?: return
            val href = attrs.getAttribute(HTML.Attribute.HREF) as? String ?: return
            trigger(e, elem, href)
        }
    }

    fun getElement(e: MouseEvent): Element? {
        val pane = e.source as? JTextPane ?: return null
        val pos = pane.ui.viewToModel2D(pane, e.point, discardBias)
        if(pos >= 0) {
            val doc = pane.document as? HTMLDocument ?: return null
            val elem = doc.getCharacterElement(pos)
            if(elem.attributes.getAttribute(HTML.Tag.A) != null) {
                return elem
            }
        }
        return null
    }
}