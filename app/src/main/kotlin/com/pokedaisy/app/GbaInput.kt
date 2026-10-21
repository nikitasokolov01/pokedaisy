package com.pokedaisy.app

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Combines remapped device keys, touchscreen and script input into the active core's mask.
 * ControllerInputRouter merges analog/digital reports before they reach this class.
 */
class GbaInput {
    @Volatile
    var mask: Int = 0
        private set

    private var buttonBits = 0   // from key events: the bits of every key in heldKeys
    private var turboBits = 0    // TURBO A / B held: pulsed by the engine ([turboMask])
    private val turboKeys = HashMap<Pair<Int, Int>, Int>()
    // Per key, not per bit: START and START 2 (X) are two keys for one bit, and
    // letting go of one mustn't release the other.
    private val heldKeys = HashMap<Pair<Int, Int>, Int>()
    private val deviceControls = HashMap<Int, Map<Int, Int>>()
    private val pulses = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
    private var touchBits = 0    // from the on-screen TouchControlsView
    private var scriptBits = 0   // synthetic presses (BattleInputController)
    private var scriptExclusive = false

    @Volatile
    private var controls: Map<Int, Int> = DEFAULT_CONTROLS

    @Synchronized fun setControls(map: Map<Int, Int>) {
        controls = map.toMap()
        deviceControls.clear()
        // Releases may arrive after a rebind removed their old key, or be swallowed by capture.
        releaseAll()
    }

    /** A DS game runs: START 2 / SELECT 2 ([GbaControls.DS_X] / [GbaControls.DS_Y]) press its X / Y. */
    @Volatile var ds = false
        set(value) {
            if (field == value) return
            field = value
            releaseAll()
        }

    /** @return true if the event was consumed. */
    @Synchronized fun setDeviceControls(device: Int, map: Map<Int, Int>) { deviceControls[device] = map.toMap() }

    @Synchronized fun onKey(keyCode: Int, down: Boolean, device: Int = 0): Boolean {
        val key = device to keyCode
        val bit = keyToBit(keyCode, device) ?: return false
        if (bit and GbaControls.TURBO != 0) {
            if (down) turboKeys[key] = bit and GbaControls.TURBO.inv() else turboKeys.remove(key)
            turboBits = turboKeys.values.fold(0) { acc, b -> acc or b }
            return true
        }
        if (down) heldKeys[key] = bit else heldKeys.remove(key)
        buttonBits = heldKeys.values.fold(0) { acc, b -> acc or b }
        recompute()
        return true
    }

    /** The GBA button [keyCode] is bound to (D-pad, or the player's GAME BUTTONS; a TURBO key counts as its button),
     * null = none. The companion's window reads keys through this, so it follows the player's bindings. */
    @Synchronized fun buttonFor(keyCode: Int, device: Int = 0): Int? = keyToBit(keyCode, device)?.let { it and GbaControls.TURBO.inv() }

    /** Deferred modifier tap lasts two emulated frames, even under fast-forward. */
    @Synchronized fun tap(keyCode: Int, device: Int) {
        val bit = keyToBit(keyCode, device)?.and(GbaControls.TURBO.inv()) ?: return
        pulses[device to keyCode] = bit to 2
        recompute()
    }

    @Synchronized fun frameMask(): Int {
        val out = mask
        val remaining = pulses.mapValues { it.value.first to it.value.second - 1 }.filterValues { it.second > 0 }
        pulses.clear(); pulses.putAll(remaining)
        recompute()
        return out
    }

    @Synchronized fun releaseDevice(device: Int) {
        heldKeys.keys.removeAll { it.first == device }
        turboKeys.keys.removeAll { it.first == device }
        pulses.keys.removeAll { it.first == device }
        deviceControls.remove(device)
        buttonBits = heldKeys.values.fold(0, Int::or)
        turboBits = turboKeys.values.fold(0, Int::or)
        recompute()
    }

    /** Lets go of every key the player holds (not a script's): the companion's window took the buttons, or a rebind
     *  the keys' releases. */
    @Synchronized fun releaseAll() {
        heldKeys.clear()
        turboKeys.clear()
        pulses.clear()
        buttonBits = 0
        turboBits = 0
        touchBits = 0
        touchTurboBits = 0
        recompute()
    }

    /** The TURBO A / B buttons held (keys or the touch controls'), which the engine presses every other 2 frames;
     * none while a script steers. */
    val turboMask: Int get() = if (scriptExclusive) 0 else turboBits or touchTurboBits

    @Volatile private var touchTurboBits = 0

    /** The touch controls' GBA buttons held ([bits]) and their TURBO A / B ([turbo], pulsed like [turboMask]). */
    @Synchronized fun setTouchBits(bits: Int, turbo: Int = 0) {
        touchBits = bits
        touchTurboBits = turbo
        recompute()
    }

    /**
     * Synthetic presses from the companion's battle controls. While
     * [exclusive] (an automated sequence is running) the player's own keys -
     * buttons, D-pad, touch controls - are ignored, so they can't knock the
     * sequence off course; they count again the moment it ends.
     */
    @Synchronized fun setScript(bits: Int, exclusive: Boolean) {
        scriptBits = bits
        scriptExclusive = exclusive
        recompute()
    }

    /** One-off presses for the game (the QUICK MENU's SELECT for a bike or a rod): pressed on top of everything else,
     * apart from [setScript]'s channel, which the battle controls send every frame (they'd wipe it). */
    @Volatile private var pressBits = 0

    @Synchronized fun setPress(bits: Int) {
        pressBits = bits
        recompute()
    }

    private fun recompute() {
        val taps = pulses.values.fold(0) { bits, tap -> bits or tap.first }
        mask = (if (scriptExclusive) scriptBits else buttonBits or touchBits or scriptBits or taps) or pressBits
    }

    private fun keyToBit(keyCode: Int, device: Int = 0): Int? =
        (deviceControls[device] ?: controls)[keyCode]?.let(::forCore) ?: when (keyCode) {
        // Some controllers (8BitDo, GameSir in a few modes) send a diagonal as one key.
        KeyEvent.KEYCODE_DPAD_UP_LEFT -> (keyToBit(19, device) ?: 0) or (keyToBit(21, device) ?: 0)
        KeyEvent.KEYCODE_DPAD_UP_RIGHT -> (keyToBit(19, device) ?: 0) or (keyToBit(22, device) ?: 0)
        KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> (keyToBit(20, device) ?: 0) or (keyToBit(21, device) ?: 0)
        KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> (keyToBit(20, device) ?: 0) or (keyToBit(22, device) ?: 0)
        else -> null
    }

    /** A binding's bits for the game running: START 2 / SELECT 2 are X / Y on a DS, else START / SELECT. */
    private fun forCore(bits: Int): Int {
        val marks = GbaControls.DS_X or GbaControls.DS_Y
        if (bits and marks == 0) return bits
        if (!ds) return bits and marks.inv()
        var out = bits and marks.inv() and (MgbaCore.Key.START or MgbaCore.Key.SELECT).inv()
        if (bits and GbaControls.DS_X != 0) out = out or MgbaCore.Key.X
        if (bits and GbaControls.DS_Y != 0) out = out or MgbaCore.Key.Y
        return out
    }

    companion object {
        private const val DEAD = 0.5f

        /** The D-pad directions a joystick event holds (its HAT, else the left stick); null = not a joystick's. */
        fun motionBits(event: MotionEvent): Int? {
            if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return null
            val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val x = if (abs(hx) > 0.5f) hx else event.getAxisValue(MotionEvent.AXIS_X)
            val y = if (abs(hy) > 0.5f) hy else event.getAxisValue(MotionEvent.AXIS_Y)
            var bits = 0
            if (x < -DEAD) bits = bits or MgbaCore.Key.LEFT
            if (x > DEAD) bits = bits or MgbaCore.Key.RIGHT
            if (y < -DEAD) bits = bits or MgbaCore.Key.UP
            if (y > DEAD) bits = bits or MgbaCore.Key.DOWN
            return bits
        }

        private val DEFAULT_CONTROLS = mapOf(
            19 to MgbaCore.Key.UP, 20 to MgbaCore.Key.DOWN, 21 to MgbaCore.Key.LEFT, 22 to MgbaCore.Key.RIGHT,
            KeyEvent.KEYCODE_BUTTON_A to MgbaCore.Key.A, KeyEvent.KEYCODE_Z to MgbaCore.Key.A,
            KeyEvent.KEYCODE_BUTTON_B to MgbaCore.Key.B, KeyEvent.KEYCODE_X to MgbaCore.Key.B,
            KeyEvent.KEYCODE_BUTTON_L1 to MgbaCore.Key.L, KeyEvent.KEYCODE_A to MgbaCore.Key.L,
            KeyEvent.KEYCODE_BUTTON_R1 to MgbaCore.Key.R, KeyEvent.KEYCODE_S to MgbaCore.Key.R,
            KeyEvent.KEYCODE_BUTTON_START to MgbaCore.Key.START, KeyEvent.KEYCODE_ENTER to MgbaCore.Key.START,
            KeyEvent.KEYCODE_BUTTON_SELECT to MgbaCore.Key.SELECT,
            KeyEvent.KEYCODE_BACKSLASH to MgbaCore.Key.SELECT, KeyEvent.KEYCODE_SHIFT_RIGHT to MgbaCore.Key.SELECT,
        )
    }
}
