package com.pokedaisy.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HotkeyLifecycleTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun lostKeyUpReleasesHoldAndAllowsASecondPress() {
        val hotkeys = Hotkeys.load(tmp.newFolder())
        val press = Hotkeys.Event(Hotkeys.Action.FF_HOLD, true)
        assertEquals(listOf(press), hotkeys.onKey(105, true)) // R2
        assertTrue(hotkeys.onKey(105, true).isEmpty())
        assertEquals(listOf(press.copy(pressed = false)), hotkeys.releaseAll())
        assertTrue(hotkeys.releaseAll().isEmpty())
        assertTrue(hotkeys.onKey(105, false).isEmpty())
        assertEquals(listOf(press), hotkeys.onKey(105, true))
    }

    @Test fun cancellationForgetsAnIncompleteChord() {
        val hotkeys = Hotkeys.load(tmp.newFolder())
        hotkeys.onKey(109, true) // SELECT, before disconnect
        assertTrue(hotkeys.releaseAll().isEmpty())
        assertFalse(hotkeys.consumes(103))
        assertTrue(hotkeys.onKey(103, true).isEmpty()) // R1 alone must not save
    }

    @Test fun cancellationNeverRetriggersACompletedOneShot() {
        val hotkeys = Hotkeys.load(tmp.newFolder())
        hotkeys.onKey(109, true)
        assertEquals(listOf(Hotkeys.Event(Hotkeys.Action.SAVE_STATE, true)), hotkeys.onKey(103, true))
        assertEquals(listOf(Hotkeys.Event(Hotkeys.Action.SAVE_STATE, false)), hotkeys.releaseAll())
        assertTrue(hotkeys.onKey(109, false).isEmpty())
        assertTrue(hotkeys.onKey(103, false).isEmpty())
    }

    @Test fun twoBindingsOfOneHoldProduceOneCancellation() {
        val hotkeys = Hotkeys.load(tmp.newFolder())
        hotkeys.onKey(105, true) // R2
        hotkeys.onKey(62, true) // SPACE
        assertEquals(listOf(Hotkeys.Event(Hotkeys.Action.FF_HOLD, false)), hotkeys.releaseAll())
    }
}
