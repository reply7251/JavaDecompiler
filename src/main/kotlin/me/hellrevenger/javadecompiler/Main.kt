package me.hellrevenger.javadecompiler

import me.hellrevenger.javadecompiler.ui.MainWindow
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        val window = MainWindow()
        window.pack()
        window.isVisible = true
    }
}