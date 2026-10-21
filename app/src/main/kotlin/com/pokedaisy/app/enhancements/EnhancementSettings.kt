package com.pokedaisy.app.enhancements

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.util.Properties

enum class EnhancementFeature(val label: String, val options: List<String>) {
    EXP_SHARE("MODERN EXP SHARE", listOf("OFF", "ON")),
    FIELD_UPGRADES("FIELD UPGRADES", listOf("OFF", "ON")),
    LEVEL_CAP("LEVEL CAPS", listOf("OFF", "HARD", "SOFT")),
    TEXT_SPEED("TEXT DISPLAY", listOf("OFF", "VERY FAST", "INSTANT")),
}

data class EnhancementPreferences(val allowed: Boolean = false, val choices: Map<EnhancementFeature, String> = emptyMap()) {
    fun choice(feature: EnhancementFeature) = choices[feature] ?: "OFF"
}
data class EnhancementSettingsRead(val preferences: EnhancementPreferences = EnhancementPreferences(), val error: Boolean = false)

/** Separate opt-in preferences for the exact ROM bytes. Never opens a save or edits a ROM. */
class EnhancementSettings(private val root: File) {
    internal fun file(sha256: String): File {
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        return File(root, "enhancements/v1/$sha256.properties")
    }
    @Synchronized fun read(key: String): EnhancementSettingsRead = runCatching {
        val f = file(key)
        if (!f.exists()) return EnhancementSettingsRead()
        check(f.length() <= 16 * 1024)
        val p = Properties().apply { f.inputStream().use(::load) }
        check(p.getProperty("version") == "1" && p.getProperty("rom") == key)
        val names = setOf("version", "rom", "allowed") + EnhancementFeature.entries.map { it.name }
        check(p.stringPropertyNames().all { it in names })
        val allowed = p.getProperty("allowed").toBooleanStrict()
        val choices = EnhancementFeature.entries.associateWith { feature ->
            (p.getProperty(feature.name) ?: "OFF").also { check(it in feature.options) }
        }
        EnhancementSettingsRead(EnhancementPreferences(allowed, choices))
    }.getOrElse { EnhancementSettingsRead(error = true) }

    @Synchronized fun write(key: String, preferences: EnhancementPreferences, expected: EnhancementPreferences? = null): Boolean = synchronized(writeLock) { runCatching {
        val current = read(key)
        check(!current.error) // Unknown schemas/corrupt originals are never overwritten by RESET either.
        if (expected != null) check(current.preferences.allowed == expected.allowed &&
            EnhancementFeature.entries.all { current.preferences.choice(it) == expected.choice(it) })
        check(preferences.choices.all { (feature, choice) -> choice in feature.options })
        val target = file(key)
        val dir = target.parentFile!!
        check(dir.isDirectory || dir.mkdirs())
        val p = Properties().apply {
            setProperty("version", "1"); setProperty("rom", key); setProperty("allowed", preferences.allowed.toString())
            EnhancementFeature.entries.forEach { setProperty(it.name, preferences.choice(it)) }
        }
        val temp = File.createTempFile("enhancements-", ".tmp", dir)
        try {
            FileOutputStream(temp).use { p.store(it, "PokeDaisy enhancements v1"); it.fd.sync() }
            // No non-atomic fallback: a failed write preserves the last good configuration.
            Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            runCatching { java.nio.channels.FileChannel.open(dir.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) } }
        } finally { temp.delete() }
    }.isSuccess }
    private companion object { val writeLock = Any() }
}
