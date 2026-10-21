package com.pokedaisy.app

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import com.pokedaisy.app.companion.ui.MenuKey

/** Dispatch directly to the visible Compose view, without moving Android's display focus. */
internal fun companionFocus(view: View, key: MenuKey, repeat: Boolean, start: () -> Unit): Boolean {
    if (!view.isShown) return false
    val code = when (key) {
        MenuKey.UP -> KeyEvent.KEYCODE_DPAD_UP
        MenuKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        MenuKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        MenuKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        MenuKey.A -> KeyEvent.KEYCODE_DPAD_CENTER
        else -> return false
    }
    if (key == MenuKey.A && repeat) return true
    if (view.isInTouchMode) view.requestFocusFromTouch() else if (!view.hasFocus()) view.requestFocus()
    start()
    val now = SystemClock.uptimeMillis()
    view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, if (repeat) 1 else 0))
    view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    return true
}
