package me.hellrevenger.javadecompiler.ui

import java.awt.Toolkit
import java.awt.Window

object Utils {
    fun setToCenter(component: Window) {
        val screen = Toolkit.getDefaultToolkit().screenSize
        component.setLocation((screen.width - component.width) / 2, (screen.height - component.height) / 2)
    }
}