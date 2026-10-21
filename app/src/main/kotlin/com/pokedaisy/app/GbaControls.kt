package com.pokedaisy.app

import android.util.Log
import android.view.KeyEvent
import java.io.File
import java.util.Properties

/**
 * Remappable mapping of physical keys/buttons to GBA buttons, from
 * `files/controls.properties`. One or more key names per GBA button
 * (comma-separated; any of them triggers it), including directions and stick axes.
 */
object GbaControls {

    /** START2 / SELECT2: a second button for START / SELECT, X / Y by default, like the
     * DS games' X (menu) and Y (registered item). TURBO A / B (issue #32, unbound by default)
     * press A / B on and off while held - [TURBO] marks them in [load]'s map, [GbaInput] pulses them. */
    enum class Btn(val bit: Int, val label: String) {
        A(MgbaCore.Key.A, "A"), B(MgbaCore.Key.B, "B"),
        L(MgbaCore.Key.L, "L"), R(MgbaCore.Key.R, "R"),
        START(MgbaCore.Key.START, "START"), SELECT(MgbaCore.Key.SELECT, "SELECT"),
        // On a DS game they're its own X / Y ([DS_X] / [DS_Y]: GbaInput swaps them in).
        START2(MgbaCore.Key.START or DS_X, "START 2"), SELECT2(MgbaCore.Key.SELECT or DS_Y, "SELECT 2"),
        TURBO_A(MgbaCore.Key.A or TURBO, "TURBO A"), TURBO_B(MgbaCore.Key.B or TURBO, "TURBO B"),
        UP(MgbaCore.Key.UP, "UP"), DOWN(MgbaCore.Key.DOWN, "DOWN"),
        LEFT(MgbaCore.Key.LEFT, "LEFT"), RIGHT(MgbaCore.Key.RIGHT, "RIGHT");

        val prop get() = name.lowercase()
        /** null: global settings shared across systems; in-game labels name the active core's buttons. */
        fun title(ds: Boolean? = null): String = when (ds) {
            true -> "DS " + when (this) { START2 -> "X"; SELECT2 -> "Y"; else -> label }
            false -> "GBA $label"
            null -> when (this) {
                START2 -> "DS X / GBA START 2"
                SELECT2 -> "DS Y / GBA SELECT 2"
                else -> label
            }
        }
        /** Touch controls and RESET remain available even if every physical input is cleared. */
        val canClear get() = true
    }

    private val DEFAULTS = linkedMapOf(
        "a" to "BUTTON_A, Z",
        "b" to "BUTTON_B, X",
        "l" to "BUTTON_L1, A",
        "r" to "BUTTON_R1, S",
        "start" to "BUTTON_START, ENTER",
        "select" to "BUTTON_SELECT, BACKSLASH, SHIFT_RIGHT",
        // Face buttons go by Android's names (Xbox positions) on every device, even where
        // the printed labels differ (AYN, Retroid: BUTTON_A is the bottom one); GAME BUTTONS remaps.
        "start2" to "BUTTON_X",
        "select2" to "BUTTON_Y",
        "turbo_a" to "",
        "turbo_b" to "",
        "up" to "DPAD_UP, STICK_UP",
        "down" to "DPAD_DOWN, STICK_DOWN",
        "left" to "DPAD_LEFT, STICK_LEFT",
        "right" to "DPAD_RIGHT, STICK_RIGHT",
    )

    /** Above every GBA key bit: a [load] value with it is a turbo button for the bits below. */
    const val TURBO = 1 shl 16

    /** START 2 / SELECT 2's mark: on a DS game ([GbaInput.ds]) the key presses the DS's X / Y instead. */
    const val DS_X = 1 shl 17
    const val DS_Y = 1 shl 18

    private fun file(dir: File) = File(dir, "controls.properties")

    private fun readProps(dir: File, persistDefaults: Boolean = true): Properties {
        val p = Properties()
        val f = file(dir)
        if (f.isFile) runCatching { f.inputStream().use(p::load) }
        var changed = false
        for ((k, v) in DEFAULTS) if (!p.containsKey(k)) { p.setProperty(k, v); changed = true }
        if (changed && persistDefaults) runCatching {
            f.outputStream().use { p.store(it, "PokeDaisy GBA button map — Android key names, comma-separated") }
        }
        return p
    }

    /** keycode -> core bit. Ambiguous imported bindings stay unbound until the player fixes them. */
    fun load(dir: File): Map<Int, Int> {
        return mapping(rawBindings(dir))
    }

    fun defaults(): Map<Btn, List<String>> = Btn.entries.associateWith { btn ->
        DEFAULTS[btn.prop].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
    }

    fun mapping(raw: Map<Btn, List<String>>): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        val conflicts = hashSetOf<Int>()
        for (btn in Btn.entries) {
            raw[btn].orEmpty().forEach { n ->
                resolveKey(n.trim())?.let {
                    if (out[it] != null && out[it] != btn.bit) conflicts += it
                    out[it] = btn.bit
                }
            }
        }
        return out - conflicts
    }

    fun conflicted(raw: Map<Btn, List<String>>, btn: Btn): Boolean = raw[btn].orEmpty().any { name ->
        val key = resolveKey(name)
        key != null && raw.any { (other, names) -> other != btn && names.any { resolveKey(it) == key } }
    }

    fun rawBindings(dir: File, persistDefaults: Boolean = true): Map<Btn, List<String>> {
        val props = readProps(dir, persistDefaults)
        return Btn.entries.associateWith {
            props.getProperty(it.prop).orEmpty().split(',').map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
        }
    }

    /** [btn] is [keyCode] now - and that key leaves any other button it was on (else the later button kept it). */
    fun setBinding(dir: File, btn: Btn, keyCode: Int) {
        val props = readProps(dir)
        val name = Hotkeys.keyName(keyCode)
        for (other in Btn.entries) if (other != btn) {
            val keys = props.getProperty(other.prop).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (name in keys) props.setProperty(other.prop, (keys - name).joinToString(", "))
        }
        props.setProperty(btn.prop, name)
        runCatching { file(dir).outputStream().use { props.store(it, "PokeDaisy GBA button map") } }
    }

    /** Leaves [btn] unbound; touchscreen controls remain available. */
    fun clearBinding(dir: File, btn: Btn) {
        if (!btn.canClear) return
        val props = readProps(dir)
        props.setProperty(btn.prop, "")
        runCatching { file(dir).outputStream().use { props.store(it, "PokeDaisy GBA button map") } }
    }

    fun resolveKey(name: String): Int? {
        if (name.isEmpty()) return null
        InputCodes.named[name.uppercase()]?.let { return it }
        return runCatching { KeyEvent::class.java.getField("KEYCODE_${name.uppercase()}").getInt(null) }
            .onFailure { Log.w("pokedaisy", "unknown control key: $name") }.getOrNull()
    }
}
