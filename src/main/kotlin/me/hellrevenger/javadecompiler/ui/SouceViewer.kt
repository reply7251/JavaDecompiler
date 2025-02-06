package me.hellrevenger.javadecompiler.ui

import me.hellrevenger.javadecompiler.decompiler.LinkableTextOutput
import java.awt.*
import javax.swing.*

class SouceViewer : JTabbedPane() {
    init {
        border = BorderFactory.createLineBorder(Color.GRAY)
    }

    override fun addTab(path: String, component: Component?) {
        val delimiter = path.lastIndexOf("/")
        val title = if(delimiter == -1) path else path.substring(delimiter + 1)

        super.addTab(title, component)

        val tab = JPanel(GridBagLayout())
        tab.add(JLabel(title))
        tab.add(CloseButton(title, path))
        MainWindow.sourceViewer.setTabComponentAt(MainWindow.sourceViewer.tabCount-1, tab)
    }
}

class CloseButton(title: String, path: String) : JButton("x") {
    init {
        size = Dimension(size.width / 2, size.height / 2)
        addActionListener {
            val index = MainWindow.sourceViewer.indexOfTab(title)
            if(index >= 0) {
                LinkableTextOutput.instances[path]?.onClose()
                MainWindow.sourceViewer.removeTabAt(index)
            }
        }
    }
}