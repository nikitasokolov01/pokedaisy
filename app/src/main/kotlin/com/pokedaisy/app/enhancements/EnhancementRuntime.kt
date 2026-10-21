package com.pokedaisy.app.enhancements

/** Implemented only by a reviewed, game-specific adapter. All calls run between frames on the core thread.
 * loadedSha256 must describe the core's pristine loaded image, never a cached filename/header guess. */
interface EnhancementAdapter {
    val loadedSha256: String?
    val capabilities: Set<String>
    val safePoint: Boolean
    val progressionReady: Boolean
    fun read(address: Long, size: Int): ByteArray?
    fun write(address: Long, bytes: ByteArray): Boolean
}

data class PatchSite(val address: Long, val original: List<Byte>, val replacement: List<Byte>)
data class EnhancementPlan(val feature: EnhancementFeature, val option: String, val revisionId: String,
    val sha256: String, val capability: String, val needsProgression: Boolean, val sites: List<PatchSite>)

/** No production hooks are certified in Phase 3. A SHA-1 catalog match alone cannot grant write access. */
object EnhancementRegistry { val plans: List<EnhancementPlan> = emptyList() }

class EnhancementRecoveryRequired : IllegalStateException("Enhancement rollback could not be verified; stop the core")
enum class EnhancementStateResult { BLOCKED, FAILED, SUCCEEDED }

/** Guarded reversible transactions. Settings/ROM files cannot supply plans, addresses or signatures. */
class EnhancementRuntime(private val rom: RomFingerprint?, private val adapter: EnhancementAdapter?,
    plans: List<EnhancementPlan> = EnhancementRegistry.plans) {
    private val revision = RomVerification.match(rom)
    private val plans = plans.map { it.copy(sites = it.sites.map { s -> s.copy(original = s.original.toList(), replacement = s.replacement.toList()) }) }
    private var installed = emptyList<EnhancementPlan>()
    var failure: String? = null; private set
    val active get() = installed.map { it.feature }.toSet()

    fun reason(feature: EnhancementFeature): String? {
        if (failure != null) return failure
        if (rom == null || revision == null) return RomVerification.status(rom)
        val candidates = plans.filter { it.feature == feature && it.revisionId == revision.id && it.sha256 == rom.sha256 }
        if (candidates.isEmpty()) return "NOT IMPLEMENTED"
        val a = adapter ?: return "CORE UNAVAILABLE"
        if (a.loadedSha256 != rom.sha256) return "ROM CHANGED"
        if (candidates.any { it.capability !in a.capabilities }) return "CORE UNAVAILABLE"
        if (candidates.any { it.needsProgression } && !a.progressionReady) return "PROGRESSION UNKNOWN"
        return null
    }

    fun frame(preferences: EnhancementPreferences) {
        checkOwnership()
        if (failure != null) return
        val desired = if (!preferences.allowed) emptyList() else EnhancementFeature.entries.mapNotNull { f ->
            if (preferences.choice(f) == "OFF" || reason(f) != null) null
            else plans.singleOrNull { it.feature == f && it.option == preferences.choice(f) &&
                it.revisionId == revision?.id && it.sha256 == rom?.sha256 }
        }
        if (desired == installed || adapter == null || !adapter.safePoint) return
        restore()
        if (desired.isEmpty()) return
        val sites = desired.flatMap { it.sites }
        val valid = sites.isNotEmpty() && sites.all { it.address >= 0 && it.original.isNotEmpty() &&
            it.original.size <= 256 && it.original.size == it.replacement.size && it.address <= Long.MAX_VALUE - it.original.size } &&
            sites.sortedBy { it.address }.zipWithNext().all { (a, b) -> a.address + a.original.size <= b.address }
        if (!valid || sites.any { !matches(it, false) }) { failure = "SIGNATURE FAILED"; return }
        // Journal before each write: a failing/throwing write may still have changed memory.
        val attempted = mutableListOf<PatchSite>()
        try {
            for (site in sites) {
                attempted += site
                check(adapter.write(site.address, site.replacement.toByteArray()) && matches(site, true))
            }
            installed = desired
        } catch (_: Throwable) {
            rollback(attempted)
            failure = "PATCH FAILED"
        }
    }

    /** Save/load/reset boundaries remove owned patches before any state can capture/reintroduce them.
     * Refuse the transition when the adapter cannot safely remove a hook yet. */
    fun beforeStateChange(): Boolean {
        checkOwnership()
        if (installed.isNotEmpty() && adapter?.safePoint != true) return false
        restore()
        return true
    }
    fun afterStateChange() { installed = emptyList() } // next frame rechecks identity, prerequisites and signatures

    fun stateChange(action: () -> Boolean): EnhancementStateResult {
        if (!beforeStateChange()) return EnhancementStateResult.BLOCKED
        return try { if (action()) EnhancementStateResult.SUCCEEDED else EnhancementStateResult.FAILED }
        finally { afterStateChange() }
    }

    private fun matches(site: PatchSite, patched: Boolean): Boolean = runCatching {
        adapter?.read(site.address, site.original.size)?.contentEquals((if (patched) site.replacement else site.original).toByteArray()) == true
    }.getOrDefault(false)

    private fun restore() {
        val sites = installed.flatMap { it.sites }
        // Never overwrite memory we no longer own (overlay/revision changed under a patch).
        if (sites.any { !matches(it, true) }) throw EnhancementRecoveryRequired()
        rollback(sites)
        installed = emptyList()
    }
    private fun checkOwnership() {
        if (installed.isNotEmpty() && adapter?.loadedSha256 != rom?.sha256) throw EnhancementRecoveryRequired()
    }
    private fun rollback(sites: List<PatchSite>) {
        var ok = true
        for (site in sites.asReversed()) {
            val restored = runCatching { adapter?.write(site.address, site.original.toByteArray()) == true && matches(site, false) }.getOrDefault(false)
            ok = restored && ok
        }
        if (!ok) throw EnhancementRecoveryRequired()
    }
}
