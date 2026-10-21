package com.pokedaisy.app

/** Device-local shortcut ownership. Only the first key is a reserved modifier; ordinary
 * shoulder holds stay immediate. A chord cannot activate after one of its keys reached
 * the game. Completed chord releases never replay keys. */
internal class ShortcutRouter(raw: Map<Hotkeys.Action, List<String>>) {
    private data class Chord(val action: Hotkeys.Action, val keys: List<Int>)
    private val chords = raw.flatMap { (a, values) -> values.mapNotNull { value ->
        val names = value.split('+').map(String::trim)
        val keys = names.mapNotNull(GbaControls::resolveKey).distinct()
        if (keys.isEmpty() || keys.size != names.size) null else Chord(a, keys)
    } }.let { all ->
        // Imported ambiguous chords fail closed, including hold shortcuts used as modifiers.
        all.filter { c -> all.none { other -> other !== c &&
            (other.keys.containsAll(c.keys) || c.keys.containsAll(other.keys)) } }
    }.sortedByDescending { it.keys.size }
    private val held = linkedSetOf<Int>()
    private val reserved = hashSetOf<Int>()
    private val used = hashSetOf<Int>()
    private val gameHeld = hashSetOf<Int>()
    private val active = hashSetOf<Chord>()
    data class Result(val consumed: Boolean, val game: List<Pair<Int, Boolean>> = emptyList(),
        val taps: List<Int> = emptyList(), val actions: List<Hotkeys.Event> = emptyList())

    fun isBound(code: Int) = chords.any { code in it.keys }

    fun key(code: Int, down: Boolean): Result {
        if (down && !held.add(code)) return Result(code in reserved)
        if (!down && !held.remove(code)) return Result(false)
        val before = active.map { it.action }.toSet()
        val game = mutableListOf<Pair<Int, Boolean>>()
        val taps = mutableListOf<Int>()
        var consumed = code in reserved
        if (down) {
            val prefix = chords.any { it.keys.size > 1 && it.keys.first() == code }
            val following = chords.any { it.keys.size > 1 && it.keys.first() in reserved &&
                it.keys.first() in held && code in it.keys && it.keys.none(gameHeld::contains) }
            val single = chords.any { it.keys == listOf(code) }
            consumed = prefix || following || single
            if (consumed) reserved += code else { gameHeld += code; game += code to true }
            val match = chords.firstOrNull { c ->
                c.keys.all(held::contains) && c.keys.none(gameHeld::contains) &&
                    (c.keys.size > 1 || !prefix) && c !in active &&
                    // A shorter action must not fire while a longer chord is being entered.
                    chords.none { larger -> larger.keys.size > c.keys.size && larger.keys.containsAll(c.keys) }
            }
            if (match != null) { active += match; reserved += match.keys; used += match.keys }
        } else {
            active.removeAll { code in it.keys }
            if (reserved.remove(code)) {
                if (code !in used) {
                    // A single action sharing a chord modifier is a tap action, never a hold.
                    val single = chords.firstOrNull { it.keys == listOf(code) }
                    if (single != null) {
                        used.remove(code)
                        return Result(true, actions = listOf(Hotkeys.Event(single.action, true), Hotkeys.Event(single.action, false)))
                    }
                    taps += code
                }
            } else if (gameHeld.remove(code)) game += code to false
            used.remove(code)
        }
        val after = active.map { it.action }.toSet()
        val actions = (before - after).map { Hotkeys.Event(it, false) } + (after - before).map { Hotkeys.Event(it, true) }
        return Result(consumed, game, taps, actions)
    }

    fun cancel(): List<Hotkeys.Event> {
        val releases = active.map { it.action }.distinct().map { Hotkeys.Event(it, false) }
        held.clear(); reserved.clear(); used.clear(); gameHeld.clear(); active.clear()
        return releases
    }
}

/** Merges digital/analog reports before edge detection, and keeps holds/chords device-local. */
class ControllerInputRouter(
    private val input: GbaInput,
    private val profile: (Int) -> InputProfile,
    private val action: (Hotkeys.Event) -> Unit,
) {
    private class Pad(val shortcuts: ShortcutRouter) {
        val keys = hashSetOf<Int>()
        var axes: Set<Int> = emptySet()
        var held: Set<Int> = emptySet()
    }
    private val pads = hashMapOf<Int, Pad>()
    private val actionOwners = hashMapOf<Hotkeys.Action, MutableSet<Int>>()
    private var generation = 0
    var hotkeysEnabled = true
        set(value) { if (field != value) { releaseAll(); field = value } }

    private fun pad(device: Int) = pads.getOrPut(device) {
        val p = profile(device)
        input.setDeviceControls(device, GbaControls.mapping(p.buttons))
        Pad(ShortcutRouter(p.hotkeys))
    }

    fun buttonFor(device: Int, code: Int): Int? { pad(device); return input.buttonFor(code, device) }
    fun shortcutFor(device: Int, code: Int): Boolean = hotkeysEnabled && pad(device).shortcuts.isBound(code)

    data class KeyResult(val handled: Boolean, val shortcut: Boolean)
    fun key(device: Int, code: Int, down: Boolean, gameAllowed: Boolean = true): Boolean = routeKey(device, code, down, gameAllowed).handled

    fun routeKey(device: Int, code: Int, down: Boolean, gameAllowed: Boolean = true): KeyResult {
        val p = pad(device)
        if (down) p.keys += code else p.keys -= code
        val shortcut = update(device, p, gameAllowed)
        return KeyResult(shortcut || (hotkeysEnabled && p.shortcuts.isBound(code)) || input.buttonFor(code, device) != null, shortcut)
    }

    fun axes(device: Int, keys: Set<Int>, gameAllowed: Boolean = true): Boolean {
        val p = pad(device); p.axes = keys; return update(device, p, gameAllowed)
    }

    private fun update(device: Int, p: Pad, gameAllowed: Boolean): Boolean {
        val now = p.keys + p.axes
        val changes = (p.held - now).map { it to false } + (now - p.held).map { it to true }
        p.held = now
        val started = generation
        var consumed = false
        for ((key, down) in changes) {
            val r = if (hotkeysEnabled) p.shortcuts.key(key, down)
                else ShortcutRouter.Result(false, listOf(key to down))
            consumed = consumed || r.consumed
            // Dispatch releases before actions: opening UI may cancel this router reentrantly.
            if (gameAllowed) {
                r.game.forEach { input.onKey(it.first, it.second, device) }
                r.taps.forEach { input.tap(it, device) }
            } else if (!down) input.onKey(key, false, device)
            r.actions.forEach { emit(device, it) }
            if (generation != started || pads[device] !== p) break
        }
        return consumed
    }

    private fun emit(device: Int, e: Hotkeys.Event) {
        val owners = actionOwners.getOrPut(e.action) { hashSetOf() }
        if (e.pressed) { if (owners.add(device) && owners.size == 1) action(e) }
        else if (owners.remove(device) && owners.isEmpty()) action(e)
    }

    fun releaseDevice(device: Int) {
        generation++
        val p = pads.remove(device)
        p?.shortcuts?.cancel()?.forEach { emit(device, it) }
        input.releaseDevice(device)
    }
    fun releaseAll() { pads.keys.toList().forEach(::releaseDevice); input.releaseAll() }

    /** Opening a menu retains shortcut releases for the next press, but stops this batch's game input. */
    fun suspendGame() { generation++; input.releaseAll() }
}
