package com.pokedaisy.app.enhancements

import org.junit.Assert.*
import org.junit.Test

class EnhancementRuntimeTest {
    private val revision = RomVerification.revisions.first()
    private val rom = RomFingerprint("a".repeat(64), revision.sha1, 4096, true, revision.code, 0)
    private val feature = EnhancementFeature.EXP_SHARE
    private val on = EnhancementPreferences(true, mapOf(feature to "ON"))
    private val plan = EnhancementPlan(feature, "ON", revision.id, rom.sha256, "synthetic-hook", false,
        listOf(PatchSite(10, listOf(1, 2), listOf(3, 4)), PatchSite(20, listOf(1, 2), listOf(5, 6))))
    private class Core : EnhancementAdapter {
        override var loadedSha256: String? = "a".repeat(64)
        override var capabilities = setOf("synthetic-hook")
        override var safePoint = true
        override var progressionReady = true
        val memory = ByteArray(64).also { it[10] = 1; it[11] = 2; it[20] = 1; it[21] = 2 }
        var writes = 0; var failAt = -1; var failRestore = false; var throwAt = -1
        override fun read(address: Long, size: Int) = memory.copyOfRange(address.toInt(), address.toInt() + size)
        override fun write(address: Long, bytes: ByteArray): Boolean {
            writes++; bytes.copyInto(memory, address.toInt())
            if (writes == throwAt) throw LinkageError("synthetic bridge error")
            return writes != failAt && !(failRestore && bytes.first() == 1.toByte())
        }
    }
    @Test fun unknownRevisionOrLoadedImageMismatchNeverWrites() {
        val c = Core()
        EnhancementRuntime(rom.copy(sha1 = "b".repeat(40)), c, listOf(plan)).frame(on)
        c.loadedSha256 = "b".repeat(64)
        EnhancementRuntime(rom, c, listOf(plan)).frame(on)
        assertEquals(0, c.writes)
    }
    @Test fun everyProductionFeatureRemainsUnavailableForKnownRetailAndUnknownGames() {
        assertTrue(EnhancementRegistry.plans.isEmpty())
        val c = Core(); val r = EnhancementRuntime(rom, c)
        EnhancementFeature.entries.forEach { assertEquals("NOT IMPLEMENTED", r.reason(it)) }
        r.frame(on); assertEquals(0, c.writes)
    }
    @Test fun optInCoreCapabilityAndProgressionAreAllRequired() {
        val c = Core(); val r = EnhancementRuntime(rom, c, listOf(plan.copy(needsProgression = true)))
        r.frame(on.copy(allowed = false)); assertEquals(0, c.writes)
        c.capabilities = emptySet(); r.frame(on); assertEquals("CORE UNAVAILABLE", r.reason(feature))
        c.capabilities = setOf("synthetic-hook"); c.progressionReady = false
        r.frame(on); assertEquals("PROGRESSION UNKNOWN", r.reason(feature)); assertEquals(0, c.writes)
    }
    @Test fun unsafePointDefersAndRepeatedFramesDoNotStackPatches() {
        val c = Core(); val original = c.memory.copyOf(); val r = EnhancementRuntime(rom, c, listOf(plan))
        c.safePoint = false; r.frame(on); assertEquals(0, c.writes)
        c.safePoint = true; r.frame(on); r.frame(on); assertEquals(2, c.writes)
        c.safePoint = false; r.frame(EnhancementPreferences()); assertEquals(2, c.writes)
        c.safePoint = true; r.frame(EnhancementPreferences()); assertArrayEquals(original, c.memory)
    }
    @Test fun everySignatureIsCheckedBeforeFirstWriteAndFailureLatches() {
        val c = Core(); c.memory[20] = 9
        val r = EnhancementRuntime(rom, c, listOf(plan)); r.frame(on)
        assertEquals("SIGNATURE FAILED", r.failure); assertEquals(0, c.writes)
        c.memory[20] = 1; r.frame(on); assertEquals(0, c.writes)
    }
    @Test fun failedPartialWriteRollsBackAllAttemptedSites() {
        val c = Core(); val original = c.memory.copyOf(); c.failAt = 2
        val r = EnhancementRuntime(rom, c, listOf(plan)); r.frame(on)
        assertEquals("PATCH FAILED", r.failure); assertArrayEquals(original, c.memory); assertTrue(r.active.isEmpty())
    }
    @Test(expected = EnhancementRecoveryRequired::class) fun rollbackFailureRequiresCoreStop() {
        val c = Core(); c.failAt = 2; c.failRestore = true
        EnhancementRuntime(rom, c, listOf(plan)).frame(on)
    }
    @Test fun stateBoundaryStripsPatchesAndRechecksLoadedStateSignatures() {
        val c = Core(); val original = c.memory.copyOf(); val r = EnhancementRuntime(rom, c, listOf(plan))
        r.frame(on); c.safePoint = false; assertFalse(r.beforeStateChange())
        c.safePoint = true; assertTrue(r.beforeStateChange()); assertArrayEquals(original, c.memory)
        r.afterStateChange(); c.memory[20] = 7; r.frame(on)
        assertEquals("SIGNATURE FAILED", r.failure); assertTrue(r.active.isEmpty())
    }
    @Test fun resetAndFailedLoadBothRequireFreshChecksAndNeverDoubleApply() {
        val c = Core(); val r = EnhancementRuntime(rom, c, listOf(plan))
        repeat(3) { r.frame(on); assertTrue(r.beforeStateChange()); r.afterStateChange() }
        assertEquals(12, c.writes); assertTrue(r.active.isEmpty())
        r.frame(on); assertEquals(14, c.writes)
    }
    @Test fun stateActionOnlyRunsAfterRemovalAndFailedLoadsStillInvalidateActiveState() {
        val c = Core(); val r = EnhancementRuntime(rom, c, listOf(plan)); val original = c.memory.copyOf()
        r.frame(on); c.safePoint = false
        var called = 0
        assertEquals(EnhancementStateResult.BLOCKED, r.stateChange { called++; true }); assertEquals(0, called)
        c.safePoint = true
        assertEquals(EnhancementStateResult.FAILED, r.stateChange { called++; assertArrayEquals(original, c.memory); false })
        assertEquals(1, called); assertTrue(r.active.isEmpty())
        r.frame(on); assertEquals(setOf(feature), r.active)
        assertThrows(IllegalStateException::class.java) { r.stateChange { error("synthetic load error") } }
        assertArrayEquals(original, c.memory); assertTrue(r.active.isEmpty())
    }
    @Test fun changedCoreCannotBeRestoredUsingOldGameAddresses() {
        val c = Core(); val r = EnhancementRuntime(rom, c, listOf(plan)); r.frame(on)
        c.loadedSha256 = "b".repeat(64)
        assertThrows(EnhancementRecoveryRequired::class.java) { r.beforeStateChange() }
        assertEquals(2, c.writes)
    }
    @Test fun conflictingOrInvalidSitesFailBeforeWriting() {
        for (site in listOf(PatchSite(10, listOf(1), listOf(3)), PatchSite(-1, listOf(1), listOf(3)),
            PatchSite(30, emptyList(), emptyList()), PatchSite(30, listOf(1), listOf(3, 4)))) {
            val c = Core(); val r = EnhancementRuntime(rom, c, listOf(plan.copy(sites = plan.sites + site)))
            r.frame(on); assertEquals(0, c.writes); assertEquals("SIGNATURE FAILED", r.failure)
        }
    }
    @Test fun nativeBridgeErrorsAlsoRollbackBeforeTheCoreCanContinue() {
        val c = Core(); val original = c.memory.copyOf(); c.throwAt = 2
        val r = EnhancementRuntime(rom, c, listOf(plan)); r.frame(on)
        assertArrayEquals(original, c.memory); assertEquals("PATCH FAILED", r.failure)
    }
}
