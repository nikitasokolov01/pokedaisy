package com.pokedaisy.app.enhancements

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EnhancementView(val identity: String = "CHECKING ROM", val preferences: EnhancementPreferences = EnhancementPreferences(),
    val reasons: Map<EnhancementFeature, String?> = EnhancementFeature.entries.associateWith { "CHECKING ROM" },
    val editable: Boolean = false, val error: String? = null, val active: Set<EnhancementFeature> = emptySet())

/** Shared by library/companion. Inspection is off the UI thread; only the emulator invokes runtime methods. */
class EnhancementSession(private val store: EnhancementSettings) {
    private val updates = MutableStateFlow(EnhancementView())
    val changes = updates.asStateFlow()
    var view: EnhancementView
        get() = updates.value
        private set(value) { updates.value = value }
    private var key: String? = null
    private var fingerprint: RomFingerprint? = null
    @Volatile private var preferences = EnhancementPreferences()
    private var runtime = EnhancementRuntime(null, null)

    @Synchronized fun inspect(rom: RomFingerprint?) {
        check(runtime.active.isEmpty()) { "Stop the previous enhancement session before changing ROMs" }
        fingerprint = rom
        key = rom?.sha256
        val loaded = key?.let(store::read) ?: EnhancementSettingsRead(error = true)
        preferences = loaded.preferences
        runtime = EnhancementRuntime(rom, null)
        view = EnhancementView(RomVerification.status(rom), preferences,
            EnhancementFeature.entries.associateWith(runtime::reason), key != null && !loaded.error,
            if (loaded.error && key != null) "SETTINGS UNREADABLE" else null)
    }
    @Synchronized fun allow(on: Boolean) = persist(preferences.copy(allowed = on))
    @Synchronized fun choose(feature: EnhancementFeature, choice: String): Boolean {
        if (choice !in feature.options || (choice != "OFF" && (runtime.reason(feature) != null || !preferences.allowed))) return false
        return persist(preferences.copy(choices = preferences.choices + (feature to choice)))
    }
    @Synchronized fun reset() = persist(EnhancementPreferences())
    private fun persist(next: EnhancementPreferences): Boolean {
        val id = key ?: return false
        if (!view.editable) return false
        if (!store.write(id, next, expected = preferences)) { view = view.copy(error = "SETTINGS NOT SAVED"); return false }
        preferences = next
        view = view.copy(preferences = next, error = null)
        return true
    }
    @Synchronized fun frame() {
        if (EnhancementRegistry.plans.isEmpty()) return
        runtime.frame(preferences)
        val next = view.copy(active = runtime.active, reasons = EnhancementFeature.entries.associateWith(runtime::reason))
        if (next != view) view = next
    }
    @Synchronized fun beforeStateChange() = runtime.beforeStateChange()
    @Synchronized fun afterStateChange() = runtime.afterStateChange()
    @Synchronized fun stateChange(action: () -> Boolean) = runtime.stateChange(action)
    /** Phase 4 supplies a reviewed native adapter here; null never grants runtime capabilities. */
    @Synchronized fun coreStarted(adapter: EnhancementAdapter? = null) {
        check(runtime.active.isEmpty())
        runtime = EnhancementRuntime(fingerprint, adapter)
    }
    /** Only after core destruction: no old addresses or installed-patch journal survive a restart. */
    @Synchronized fun coreStopped() {
        runtime = EnhancementRuntime(fingerprint, null)
        view = view.copy(active = emptySet(), reasons = EnhancementFeature.entries.associateWith(runtime::reason))
    }
}
