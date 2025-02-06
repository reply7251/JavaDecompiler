package me.hellrevenger.javadecompiler.ui

import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

object KeyBoard {
    val pressedKeys = hashSetOf<Int>()
    val modifiers = hashSetOf<Int>()

    val keyEvents = hashSetOf<KeyEventDispatcher>()

    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher {
            synchronized(this) {
                if (it.id == KeyEvent.KEY_PRESSED) {
                    pressedKeys.add(it.keyCode)
                } else if (it.id == KeyEvent.KEY_RELEASED) {
                    pressedKeys.remove(it.keyCode)
                }
                if(it.isControlDown) {
                    modifiers.add(KeyEvent.CTRL_DOWN_MASK)
                } else {
                    modifiers.remove(KeyEvent.CTRL_DOWN_MASK)
                }
                keyEvents.forEach { dispatcher -> dispatcher.onKey(it) }
                false
            }
        }
    }

    fun isKeyPressed(key: Int) = pressedKeys.contains(key)
    fun isModifierPressed(key: Int) = modifiers.contains(key)

    fun registerKeyEvent(dispatcher: KeyEventDispatcher) = keyEvents.add(dispatcher)
    fun unregisterKeyEvent(dispatcher: KeyEventDispatcher) = keyEvents.remove(dispatcher)
}

interface KeyEventDispatcher {
    fun onKey(event: KeyEvent)
}