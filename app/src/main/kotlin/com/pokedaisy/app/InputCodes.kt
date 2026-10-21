package com.pokedaisy.app

/** Virtual keys for the left stick. Hats use Android DPAD keys, triggers use BUTTON_L2/R2. */
object InputCodes {
    const val STICK_UP = -101
    const val STICK_DOWN = -102
    const val STICK_LEFT = -103
    const val STICK_RIGHT = -104
    val named = mapOf("STICK_UP" to STICK_UP, "STICK_DOWN" to STICK_DOWN,
        "STICK_LEFT" to STICK_LEFT, "STICK_RIGHT" to STICK_RIGHT)
    fun name(code: Int): String? = named.entries.firstOrNull { it.value == code }?.key

    fun axes(hatX: Float, hatY: Float, stickX: Float, stickY: Float, left: Float = 0f, right: Float = 0f): Set<Int> = buildSet {
        if (hatY < -0.5f) add(19); if (hatY > 0.5f) add(20)
        if (hatX < -0.5f) add(21); if (hatX > 0.5f) add(22)
        if (stickY < -0.5f) add(STICK_UP); if (stickY > 0.5f) add(STICK_DOWN)
        if (stickX < -0.5f) add(STICK_LEFT); if (stickX > 0.5f) add(STICK_RIGHT)
        if (left > 0.5f) add(104); if (right > 0.5f) add(105)
    }
}
