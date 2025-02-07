package me.hellrevenger.javadecompiler.ui

import com.strobel.assembler.metadata.MetadataSystem
import com.strobel.decompiler.DecompilationOptions
import me.hellrevenger.javadecompiler.decompiler.LinkableTextOutput
import java.awt.*
import javax.swing.*

class SouceViewer : JTabbedPane() {
    val tabs = hashMapOf<String, HashMap<String, Pair<Component, JTextPane>>>()

    init {
        border = BorderFactory.createLineBorder(Color.GRAY)
    }

    override fun addTab(path: String, component: Component?) {
        val delimiter = path.lastIndexOf("/")
        val title = if(delimiter == -1) path else path.substring(delimiter + 1)

        super.addTab("$title", component)

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
        if(foundTab != null) return foundTab.second

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