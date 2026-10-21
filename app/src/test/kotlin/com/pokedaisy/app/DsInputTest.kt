package com.pokedaisy.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Synthetic Android keycodes and the DS hardware's twelve pressed-high bits; no ROM or JNI library. */
class DsInputTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun everyDsButtonReachesItsDistinctCoreBit() {
        val input = GbaInput().apply { ds = true; setControls(GbaControls.load(tmp.newFolder())) }
        // A, B, SELECT, START, RIGHT, LEFT, UP, DOWN, R, L, X, Y (Android keycodes).
        val keys = listOf(96, 97, 109, 108, 22, 21, 19, 20, 103, 102, 99, 100)
        for ((bit, key) in keys.withIndex()) {
            assertTrue(input.onKey(key, true))
            assertEquals("DS bit $bit", 1 shl bit, input.mask)
            assertTrue(input.onKey(key, false))
            assertEquals(0, input.mask)
        }
        keys.forEach { input.onKey(it, true) }
        assertEquals(0xFFF, input.mask)
        input.releaseAll()
        assertEquals(0, input.mask)
    }

    @Test fun savedCustomXAndYBindingsSurviveReloadAndStayIndependentOfStartSelect() {
        val dir = tmp.newFolder()
        File(dir, "controls.properties").writeText("start2=BUTTON_C\nselect2=BUTTON_Z\n")
        repeat(2) {
            val input = GbaInput().apply { ds = true; setControls(GbaControls.load(dir)) }
            input.onKey(98, true) // BUTTON_C remapped to DS X
            input.onKey(101, true) // BUTTON_Z remapped to DS Y
            assertEquals(0xC00, input.mask)
            input.onKey(108, true)
            input.onKey(109, true)
            assertEquals(0xC0C, input.mask)
            input.onKey(98, false)
            input.onKey(101, false)
            assertEquals(0x00C, input.mask)
            assertFalse(input.onKey(99, true)) // old physical X no longer bound
            assertFalse(input.onKey(100, true))
        }
    }

    @Test fun gbaExtraStartSelectStillShareTheirOriginalBits() {
        val input = GbaInput().apply { setControls(GbaControls.load(tmp.newFolder())) }
        input.onKey(99, true)
        input.onKey(100, true)
        assertEquals(0x00C, input.mask)
        input.onKey(108, true)
        input.onKey(99, false)
        assertEquals(0x00C, input.mask)
        input.onKey(100, false)
        assertEquals(0x008, input.mask)
        input.onKey(108, false)
        assertEquals(0, input.mask)
    }

    @Test fun replacingMappingsReleasesKeysEvenIfTheirBindingWasRemoved() {
        val input = GbaInput().apply { ds = true; setControls(mapOf(99 to GbaControls.Btn.START2.bit)) }
        input.onKey(99, true)
        input.setControls(mapOf(98 to GbaControls.Btn.START2.bit))
        assertEquals(0, input.mask)
        input.onKey(99, false)
        assertEquals(0, input.mask)
        input.onKey(98, true)
        assertEquals(0x400, input.mask)
    }

    @Test fun rebindReleasesTurboAndCopiesTheSuppliedMap() {
        val map = mutableMapOf(98 to GbaControls.Btn.TURBO_A.bit)
        val input = GbaInput().apply { setControls(map) }
        input.onKey(98, true)
        assertEquals(1, input.turboMask)
        input.setControls(map)
        assertEquals(0, input.turboMask)
        map.clear()
        assertTrue(input.onKey(98, true))
        assertEquals(1, input.turboMask)
    }

    @Test fun switchingCoreClearsOldXAndYButKeepsTheBindings() {
        val input = GbaInput().apply { ds = true; setControls(GbaControls.load(tmp.newFolder())) }
        input.onKey(99, true)
        input.ds = false
        assertEquals(0, input.mask)
        input.onKey(99, true)
        assertEquals(8, input.mask)
        input.ds = true
        assertEquals(0, input.mask)
        input.onKey(99, true)
        assertEquals(0x400, input.mask)
    }

    @Test fun cancellationClearsPlayerHoldsWithoutCancelingEngineScripts() {
        val input = GbaInput().apply { setControls(mapOf(96 to 1, 98 to GbaControls.Btn.TURBO_A.bit)) }
        input.onKey(96, true)
        input.onKey(98, true)
        input.onKey(19, true)
        input.setTouchBits(0x200, 2)
        input.setScript(0x100, exclusive = false)
        input.setPress(4)
        input.releaseAll()
        assertEquals(0x104, input.mask)
        assertEquals(0, input.turboMask)
        input.releaseAll()
        assertEquals(0x104, input.mask)
        input.setScript(0, exclusive = false)
        input.setPress(0)
        assertEquals(0, input.mask)
    }

    @Test fun dsLabelsNameXAndYWithoutChangingPersistedPropertyNames() {
        assertEquals("DS X", GbaControls.Btn.START2.title(true))
        assertEquals("DS Y", GbaControls.Btn.SELECT2.title(true))
        assertEquals("DS START", GbaControls.Btn.START.title(true))
        assertEquals("DS SELECT", GbaControls.Btn.SELECT.title(true))
        assertEquals("DS X / GBA START 2", GbaControls.Btn.START2.title())
        assertEquals("DS Y / GBA SELECT 2", GbaControls.Btn.SELECT2.title())
        assertEquals("GBA START 2", GbaControls.Btn.START2.title(false))
        assertEquals("start2", GbaControls.Btn.START2.prop)
        assertEquals("select2", GbaControls.Btn.SELECT2.prop)
    }
}
