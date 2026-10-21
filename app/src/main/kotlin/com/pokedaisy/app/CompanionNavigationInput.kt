package com.pokedaisy.app

import com.pokedaisy.app.companion.ui.MenuKey

/** Merged physical edges, with bounded direction repeat even when an analog stick stops
 * sending motion events. Releases and device removal cannot affect another controller. */
class CompanionNavigationInput(private val send: (MenuKey, Boolean) -> Unit) {
    private data class Hold(val key: MenuKey, var next: Long)
    private val held = linkedMapOf<Pair<Int, Int>, Hold>()

    fun edge(device: Int, code: Int, key: MenuKey?, down: Boolean, now: Long) {
        val id = device to code
        if (!down) { held.remove(id); return }
        if (key == null || id in held) return
        held[id] = Hold(key, now + 350)
        send(key, false)
    }

    fun tick(now: Long) {
        for ((id, hold) in held.toMap()) {
            if (held[id] !== hold || hold.key !in directions || now < hold.next) continue
            hold.next = now + 90
            send(hold.key, true)
        }
    }

    fun releaseDevice(device: Int) { held.keys.removeAll { it.first == device } }
    fun clear() = held.clear()
    val repeating get() = held.values.any { it.key in directions }

    private companion object {
        val directions = setOf(MenuKey.UP, MenuKey.DOWN, MenuKey.LEFT, MenuKey.RIGHT)
    }
}
