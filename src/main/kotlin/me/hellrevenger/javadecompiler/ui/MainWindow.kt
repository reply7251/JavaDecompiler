package me.hellrevenger.javadecompiler.ui

import java.awt.Dimension
import javax.swing.*
import com.formdev.flatlaf.FlatDarkLaf
import com.strobel.decompiler.DecompilerSettings
import java.awt.Toolkit

object MainWindow : JFrame() {

    val settings: DecompilerSettings = DecompilerSettings.javaDefaults()
    val sourceViewer: SouceViewer
    val fileTree: FileTree
    val analyzer: Analyzer

    init {
        defaultCloseOperation = EXIT_ON_CLOSE

        if(FlatDarkLaf.setup()) {
            println("dark mode enabled")
        }

        sourceViewer = SouceViewer()
        fileTree = FileTree()
        analyzer = Analyzer()

        val pane = JSplitPane()
        val pane2 = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        pane2.leftComponent = sourceViewer
        pane2.rightComponent = JScrollPane(analyzer)
        pane2.dividerLocation = 450
        pane.rightComponent = pane2
        pane.leftComponent = JScrollPane(fileTree)
        pane.dividerLocation = 200
        add(pane)

        title = "Java Decompiler"

        preferredSize = Dimension(1080, 720)

        jMenuBar = JMenuBar()

        pack()

        Utils.setToCenter(this)
    }
}