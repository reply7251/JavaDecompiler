package me.hellrevenger.javadecompiler.ui

import com.strobel.assembler.metadata.MetadataSystem
import com.strobel.decompiler.DecompilationOptions
import me.hellrevenger.javadecompiler.decompiler.LinkableTextOutput
import java.awt.*
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.text.AttributeSet
import javax.swing.text.ElementIterator
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument

class SouceViewer : JTabbedPane(), Searchable {
    val tabs = hashMapOf<String, HashMap<String, Pair<Component, JTextPane>>>()
    val searchComponent: JSearch

    init {
        border = BorderFactory.createLineBorder(Color.GRAY)

        searchComponent = JSearch()
        searchComponent.target = this
        registerKeyboardAction({ e ->
            if(tabCount > 0)
                searchComponent.isVisible = true
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    }

    override fun addTab(path: String, component: Component?) {
        val delimiter = path.lastIndexOf("/")
        val title = if(delimiter == -1) path else path.substring(delimiter + 1)

        super.addTab(title, component)

        val tab = JPanel(GridBagLayout())
        tab.add(JLabel("$title   "))
        tab.add(CloseButton(path))
        setTabComponentAt(tabCount-1, tab)
    }

    fun findTab(path: String): Pair<Component, JTextPane>? {
        tabs.forEach { (t, u) ->
            val tab = u[path]
            if(tab != null) return tab
        }
        return null
    }


    fun openClass(file: String, system: MetadataSystem, path: String): JTextPane? {
        val foundTab = findTab(path)
        if(foundTab != null) {
            selectedIndex = indexOfTabComponent(foundTab.first)
            return foundTab.second
        }

        val lookup = system.lookupType(path) ?: return null
        val resolve = lookup.resolve() ?: return null
        val pane = JTextPane()
        val output = LinkableTextOutput(path, pane)
        MainWindow.settings.language.decompileType(resolve, output, DecompilationOptions())
        output.flush()
        val tabContent = JScrollPane(pane)
        addTab(path, tabContent)
        selectedComponent = tabContent
        tabs.computeIfAbsent(file) { hashMapOf() }[path] = getTabComponentAt(tabCount - 1) to pane

        return pane
    }

    fun searchHref(targetHref: String, afterHref: String? = null) {
        val pane = getTextPane() ?: return
        val doc = pane.document as? HTMLDocument ?: return
        val iter = ElementIterator(doc)
        var findAfter = afterHref == null
        while (iter.next() != null) {
            val elem = iter.current()
            (elem.attributes.getAttribute(HTML.Tag.A) as? AttributeSet)?.let {
                val href = it.getAttribute(HTML.Attribute.HREF) as String
                if(href == afterHref) {
                    findAfter = true
                } else if(findAfter && href == targetHref) {
                    var startOffset = elem.startOffset
                    while (doc.getText(startOffset, 1) == " ") startOffset++
                    pane.grabFocus()
                    pane.select(startOffset, elem.endOffset)
                    return
                }
            }
        }
    }

    fun onFileRemoved(path: String) {
        tabs[path]?.forEach { (t, u) ->
            closeTab(t)
        }
        tabs.remove(path)
    }

    fun closeTab(path: String) {
        findTab(path)?.let {
            LinkableTextOutput.instances[path]?.onClose()
            removeTabAt(indexOfTabComponent(it.first))
            tabs.forEach { (t, u) ->
                u.remove(path)
            }
        }
    }

    fun getTextPane(): JTextPane? = (selectedComponent as? JScrollPane)?.let { return it.viewport.view as? JTextPane }

    override fun search(text: String, config: SearchConfig) {
        getTextPane()?.let { pane ->
            val source = pane.document.getText(0, pane.document.length)
            val regexOptions = mutableSetOf<RegexOption>()
            if(!config.regex) regexOptions.add(RegexOption.LITERAL)
            if(!config.matchCase) regexOptions.add(RegexOption.IGNORE_CASE)
            RegexOption.DOT_MATCHES_ALL
            val regex = if(config.matchCase) {
                text.toRegex()
            } else {
                text.toRegex(RegexOption.IGNORE_CASE)
            }
            var result = regex.find(source, pane.selectionEnd)
            if(result == null) result = regex.find(source, 0)
            result?.let { result ->
                pane.select(result.range.first, result.range.last+1)
            }
        }
    }
}

class CloseButton(path: String) : JButton("x") {
    init {
        addActionListener {
            MainWindow.sourceViewer.closeTab(path)
        }
    }
}