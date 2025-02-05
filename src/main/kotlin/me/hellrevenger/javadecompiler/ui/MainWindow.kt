package me.hellrevenger.javadecompiler.ui

import java.awt.Dimension
import javax.swing.*
import com.formdev.flatlaf.FlatDarkLaf
import com.strobel.decompiler.DecompilerSettings

class MainWindow : JFrame() {

    val settings: DecompilerSettings = DecompilerSettings()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE

        if(FlatDarkLaf.setup()) {
            println("dark mode enabled")
        }


        val pane = JSplitPane()

        pane.leftComponent = JScrollPane(FileTree(settings))
        pane.rightComponent = SouceViewer()
        pane.dividerLocation = 200
        add(pane)

        preferredSize = Dimension(800, 600)

        jMenuBar = JMenuBar()


        pack()
    }
}