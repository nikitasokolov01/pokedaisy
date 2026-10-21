package com.pokedaisy.app.enhancements

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class EnhancementSettingsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val a = "a".repeat(64); private val b = "b".repeat(64)
    @Test fun defaultOffAndExactGamesPersistIndependently() {
        val s = EnhancementSettings(tmp.root)
        assertFalse(s.read(a).preferences.allowed)
        val p = EnhancementPreferences(true, mapOf(EnhancementFeature.LEVEL_CAP to "SOFT"))
        assertTrue(s.write(a, p))
        assertEquals("SOFT", EnhancementSettings(tmp.root).read(a).preferences.choice(EnhancementFeature.LEVEL_CAP))
        assertFalse(s.read(b).preferences.allowed)
        assertTrue(s.write(a, EnhancementPreferences()))
        assertFalse(s.read(a).preferences.allowed)
    }
    @Test fun unknownCorruptAndMismatchedSettingsArePreservedAndNeverEnabled() {
        val s = EnhancementSettings(tmp.root)
        for (bad in listOf("version=2\nrom=$a\nallowed=true", "version=1\nrom=$b\nallowed=true",
            "version=1\nrom=$a\nallowed=maybe", "version=1\nrom=$a\nallowed=true\nTEXT_SPEED=FASTEST",
            "version=1\nrom=$a\nallowed=true\nFUTURE=yes")) {
            val file = s.file(a).apply { parentFile.mkdirs(); writeText(bad) }
            assertTrue(s.read(a).error); assertFalse(s.read(a).preferences.allowed)
            assertFalse(s.write(a, EnhancementPreferences()))
            assertEquals(bad, file.readText())
        }
    }
    @Test fun failedPersistenceCannotChangeSessionOrOverwriteOriginal() {
        val s = EnhancementSettings(tmp.root)
        val session = EnhancementSession(s)
        val r = RomVerification.revisions.first()
        session.inspect(RomFingerprint(a, r.sha1, 4096, true, r.code, r.revision))
        s.file(a).parentFile.mkdirs(); s.file(a).mkdir()
        assertFalse(session.allow(true)); assertFalse(session.view.preferences.allowed)
        assertEquals("SETTINGS NOT SAVED", session.view.error)
    }
    @Test fun unavailableFeaturesCannotBeEnabledEvenAfterPermissionAndReopen() {
        val s = EnhancementSettings(tmp.root); val session = EnhancementSession(s)
        val r = RomVerification.revisions.first()
        val rom = RomFingerprint(a, r.sha1, 4096, true, r.code, r.revision)
        session.inspect(rom); assertTrue(session.allow(true))
        for (f in EnhancementFeature.entries) {
            assertEquals("NOT IMPLEMENTED", session.view.reasons[f])
            assertFalse(session.choose(f, f.options.last()))
        }
        val reopened = EnhancementSession(s); reopened.inspect(rom)
        assertTrue(reopened.view.preferences.allowed); assertTrue(reopened.view.active.isEmpty())
        assertTrue(reopened.reset()); assertFalse(reopened.view.preferences.allowed)
    }
    @Test fun invalidIdentityNeverEscapesSettingsDirectory() {
        val s = EnhancementSettings(tmp.root)
        assertTrue(s.read("../other").error)
        assertFalse(s.write("../other", EnhancementPreferences(true)))
        assertTrue(tmp.root.listFiles().isNullOrEmpty())
    }
    @Test fun separateLibraryAndGameSessionsCannotOverwriteANewerPreference() {
        val rom = RomFingerprint(a, "b".repeat(40), 4096, true, "IPKE", 0)
        val library = EnhancementSession(EnhancementSettings(tmp.root)).apply { inspect(rom) }
        val game = EnhancementSession(EnhancementSettings(tmp.root)).apply { inspect(rom) }
        assertTrue(library.allow(true))
        assertFalse(game.reset())
        assertEquals("SETTINGS NOT SAVED", game.view.error)
        assertTrue(EnhancementSettings(tmp.root).read(a).preferences.allowed)
        game.inspect(rom); assertTrue(game.reset())
        assertFalse(EnhancementSettings(tmp.root).read(a).preferences.allowed)
    }
}
