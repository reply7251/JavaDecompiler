package me.hellrevenger.javadecompiler

import me.hellrevenger.javadecompiler.ui.MainWindow
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        MainWindow.pack()
        MainWindow.isVisible = true
    }
}