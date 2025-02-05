package me.hellrevenger.javadecompiler.ui

import java.awt.Color
import java.awt.Dimension
import javax.swing.*
import com.formdev.flatlaf.FlatDarkLaf

class MainWindow : JFrame() {
    init {
        defaultCloseOperation = EXIT_ON_CLOSE

        if(FlatDarkLaf.setup()) {
            println("dark mode enabled")
        }


        val pane = JSplitPane()

        pane.leftComponent = JScrollPane(FileTree())
        pane.rightComponent = SouceViewer()
        pane.dividerLocation = 200
        add(pane)

        preferredSize = Dimension(800, 600)

        jMenuBar = JMenuBar()


        pack()
    }
}