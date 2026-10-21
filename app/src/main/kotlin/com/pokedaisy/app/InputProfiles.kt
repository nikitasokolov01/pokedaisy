package com.pokedaisy.app

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import java.util.Properties

data class InputProfileChoice(val id: String?, val label: String)
data class InputProfileScope(val game: String? = null, val device: String? = null)
data class InputProfile(
    val buttons: Map<GbaControls.Btn, List<String>>,
    val hotkeys: Map<Hotkeys.Action, List<String>>,
)

/** Sparse overrides: legacy global -> global controller -> game -> game/controller.
 * Runtime Android device IDs never enter storage. No game/save data is written here. */
class InputProfiles(private val root: File) {
    private val folder get() = File(root, "input-profiles/v1")
    private fun file(scope: InputProfileScope) = File(folder, "${scope.game?.let(::hash) ?: "all"}-${scope.device?.let(::hash) ?: "all"}.properties")

    fun resolve(scope: InputProfileScope = InputProfileScope()): InputProfile {
        val buttons = GbaControls.rawBindings(root, persistDefaults = false).toMutableMap()
        val hotkeys = Hotkeys.load(root, persistDefaults = false).rawBindings.toMutableMap()
        for (layer in layers(scope)) {
            val p = read(layer)
            for (b in GbaControls.Btn.entries) p.getProperty("button.${b.prop}")?.let { buttons[b] = values(it) }
            for (a in Hotkeys.Action.entries) p.getProperty("hotkey.${a.prop}")?.let { hotkeys[a] = values(it) }
        }
        return InputProfile(buttons, hotkeys)
    }

    private fun layers(scope: InputProfileScope): Set<InputProfileScope> {
        val layers = linkedSetOf(InputProfileScope())
        scope.device?.let { layers += InputProfileScope(device = it) }
        scope.game?.let { layers += InputProfileScope(game = it) }
        layers += scope
        return layers
    }

    fun unreadable(scope: InputProfileScope): Boolean = layers(scope).any { runCatching { read(it, editing = true) }.isFailure }

    fun buttonClashes(scope: InputProfileScope, button: GbaControls.Btn, key: String): List<GbaControls.Btn> =
        resolve(scope).buttons.filter { (other, keys) -> other != button && keys.any { it.equals(key, true) } }.keys.toList()

    @Synchronized fun bind(scope: InputProfileScope, button: GbaControls.Btn, key: String?) {
        require(key == null || GbaControls.resolveKey(key) != null) { "Unknown input" }
        val p = read(scope, editing = true)
        if (key != null) for ((other, keys) in resolve(scope).buttons) {
            if (other != button && keys.any { it.equals(key, true) })
                p.setProperty("button.${other.prop}", keys.filterNot { it.equals(key, true) }.joinToString(","))
        }
        p.setProperty("button.${button.prop}", key.orEmpty())
        write(scope, p)
    }

    @Synchronized fun bind(scope: InputProfileScope, action: Hotkeys.Action, keys: List<String>?, takeFrom: List<Hotkeys.Action> = emptyList()) {
        require(keys.orEmpty().all { GbaControls.resolveKey(it) != null }) { "Unknown input" }
        val p = read(scope, editing = true)
        val chord = keys.orEmpty().map(String::uppercase).toSet()
        for (other in takeFrom) if (other != action) {
            val kept = resolve(scope).hotkeys[other].orEmpty().filterNot { Hotkeys.overlap(it.split('+').map(String::trim).map(String::uppercase).toSet(), chord) }
            p.setProperty("hotkey.${other.prop}", kept.joinToString(","))
        }
        p.setProperty("hotkey.${action.prop}", keys.orEmpty().joinToString("+"))
        write(scope, p)
    }

    /** Global RESET restores factory settings; a narrower RESET returns to its inherited settings. */
    @Synchronized fun reset(scope: InputProfileScope) {
        read(scope, editing = true) // Never overwrite an unknown version.
        val p = Properties()
        if (scope == InputProfileScope()) {
            GbaControls.defaults().forEach { (b, keys) -> p.setProperty("button.${b.prop}", keys.joinToString(",")) }
            Hotkeys.defaults().forEach { (a, chords) -> p.setProperty("hotkey.${a.prop}", chords.joinToString(",")) }
        }
        write(scope, p)
    }

    private fun read(scope: InputProfileScope, editing: Boolean = false): Properties {
        val p = Properties()
        val f = file(scope)
        if (!f.exists()) return p
        val loaded = runCatching { f.inputStream().use(p::load); p.getProperty("version") == "1" }.getOrDefault(false)
        if (!loaded) {
            check(!editing) { "Profile could not be read. Original file kept." }
            return Properties() // Fail safely to inherited controls; don't guess a new schema.
        }
        return p
    }

    private fun write(scope: InputProfileScope, p: Properties) {
        check(folder.isDirectory || folder.mkdirs()) { "Cannot create controller profile folder" }
        p.setProperty("version", "1")
        val target = file(scope)
        val temp = File.createTempFile("profile-", ".tmp", folder)
        try {
            FileOutputStream(temp).use { p.store(it, "PokeDaisy input profile v1"); it.fd.sync() }
            try { Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), target.toPath(), REPLACE_EXISTING) }
            // Supported on Android/Linux; Windows may reject opening a directory. The file itself is synced above.
            runCatching { java.nio.channels.FileChannel.open(folder.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) } }
        } finally { temp.delete() }
    }

    companion object {
        private fun values(s: String) = s.split(',').map(String::trim).filter(String::isNotEmpty)
        fun hash(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        /** Empty/duplicated descriptors cannot distinguish physical pads; use shared settings. */
        fun deviceKey(descriptor: String?, virtual: Boolean, matchingDescriptors: Int): String? =
            descriptor?.takeIf { it.isNotBlank() && !virtual && matchingDescriptors == 1 }?.let(::hash)
    }
}
