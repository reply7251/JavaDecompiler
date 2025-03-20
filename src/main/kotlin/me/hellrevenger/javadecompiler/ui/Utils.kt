package me.hellrevenger.javadecompiler.ui

import java.awt.Cursor
import java.awt.Toolkit
import java.awt.Window
import javax.swing.JComponent

object Utils {
    fun setToCenter(component: Window) {
        val screen = Toolkit.getDefaultToolkit().screenSize
        component.setLocation((screen.width - component.width) / 2, (screen.height - component.height) / 2)
    }

    var waitCounter = 0

}
fun JComponent.waitCursor(callback: () -> Unit) {
    cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
    Utils.waitCounter++
    callback()
    if(--Utils.waitCounter <= 0)
        cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
}