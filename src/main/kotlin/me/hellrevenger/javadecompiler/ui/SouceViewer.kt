package me.hellrevenger.javadecompiler.ui

import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JTabbedPane

class SouceViewer : JTabbedPane() {
    init {
        border = BorderFactory.createLineBorder(Color.GRAY)
    }
}