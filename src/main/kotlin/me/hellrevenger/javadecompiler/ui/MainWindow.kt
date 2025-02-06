package me.hellrevenger.javadecompiler.ui

import java.awt.Dimension
import javax.swing.*
import com.formdev.flatlaf.FlatDarkLaf
import com.strobel.decompiler.DecompilerSettings

object MainWindow : JFrame() {

    val settings: DecompilerSettings = DecompilerSettings()
    val sourceViewer: SouceViewer
    val fileTree: FileTree

    init {
        defaultCloseOperation = EXIT_ON_CLOSE

        if(FlatDarkLaf.setup()) {
            println("dark mode enabled")
        }

        sourceViewer = SouceViewer()
        fileTree = FileTree()


        val pane = JSplitPane()
        pane.rightComponent = sourceViewer
        pane.leftComponent = JScrollPane(fileTree)
        pane.dividerLocation = 200
        add(pane)

        preferredSize = Dimension(800, 600)

        jMenuBar = JMenuBar()

        pack()
    }
}