package com.pokedaisy.app

import com.pokedaisy.app.GbaControls.Btn
import com.pokedaisy.app.Hotkeys.Action
import org.junit.Assert.*
import org.junit.Test

class ControllerInputRouterTest {
    @Test fun anExplicitDiagonalBindingOverridesItsDefaultTwoDirections() {
        val router = router(buttons = mapOf(Btn.START2 to listOf("DPAD_UP_LEFT")))
        router.key(1, GbaControls.resolveKey("DPAD_UP_LEFT")!!, true)
        assertEquals(0x400, input.frameMask())
    }

    @Test fun shortcutReleasesRemainOwnedInsideMenusIncludingAnalogTriggers() {
        val router = router(mapOf(Action.QUICK_MENU to listOf("BUTTON_R2")))
        assertTrue(router.axes(1, setOf(105)))
        router.suspendGame()
        assertTrue(router.axes(1, emptySet(), gameAllowed = false))
        assertEquals(listOf(Hotkeys.Event(Action.QUICK_MENU, true), Hotkeys.Event(Action.QUICK_MENU, false)), events)
        assertEquals(0, input.frameMask())
        assertFalse(router.routeKey(1, 103, true, gameAllowed = false).shortcut)
        assertEquals(0, input.frameMask())
    }
    private val input = GbaInput().apply { ds = true }
    private val events = mutableListOf<Hotkeys.Event>()
    private fun router(hotkeys: Map<Action, List<String>> = emptyMap(), buttons: Map<Btn, List<String>> = GbaControls.defaults()) =
        ControllerInputRouter(input, { InputProfile(buttons, hotkeys) }, events::add)

    @Test fun allTwelveRemappedControlsReachTheDsFrameMask() {
        // Deliberately unrelated physical keyboard keys, with no default mapping left.
        val buttons = listOf(Btn.A, Btn.B, Btn.SELECT, Btn.START, Btn.RIGHT, Btn.LEFT, Btn.UP, Btn.DOWN, Btn.R, Btn.L, Btn.START2, Btn.SELECT2)
        val names = listOf("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12")
        val router = router(buttons = buttons.zip(names.map(::listOf)).toMap())
        names.forEachIndexed { bit, name ->
            val key = GbaControls.resolveKey(name)!!
            router.key(42, key, true)
            assertEquals("DS register bit $bit", 1 shl bit, input.frameMask())
            router.key(42, key, false)
            assertEquals(0, input.frameMask())
        }
        names.forEach { router.key(42, GbaControls.resolveKey(it)!!, true) }
        assertEquals(0xFFF, input.frameMask())
        router.releaseAll()
        assertEquals(0, input.frameMask())
    }

    @Test fun modifierFirstShortcutNeverLeaksAnyEdgeAndRepeatsOnce() {
        val router = router(mapOf(Action.SAVE_STATE to listOf("BUTTON_SELECT+BUTTON_R1")))
        router.key(1, 109, true)
        assertEquals(0, input.frameMask())
        repeat(4) { router.key(1, 103, true) }
        assertEquals(listOf(Hotkeys.Event(Action.SAVE_STATE, true)), events)
        router.key(1, 109, false)
        router.key(1, 103, false)
        assertEquals(listOf(Hotkeys.Event(Action.SAVE_STATE, true), Hotkeys.Event(Action.SAVE_STATE, false)), events)
        repeat(4) { assertEquals(0, input.frameMask()) }
    }

    @Test fun modifierAloneIsADeferredTwoFrameTapAndCancelNeverReplaysIt() {
        val router = router(mapOf(Action.SAVE_STATE to listOf("BUTTON_SELECT+BUTTON_R1")))
        router.key(1, 109, true)
        repeat(5) { assertEquals(0, input.frameMask()) }
        router.key(1, 109, false)
        assertEquals(4, input.frameMask()); assertEquals(4, input.frameMask()); assertEquals(0, input.frameMask())
        router.key(1, 109, true)
        router.releaseAll()
        router.key(1, 109, false)
        assertEquals(0, input.frameMask())
        assertTrue(events.isEmpty())
    }

    @Test fun shoulderFirstStaysImmediateAndCannotTriggerALateShortcut() {
        val router = router(mapOf(Action.SAVE_STATE to listOf("BUTTON_SELECT+BUTTON_R1")))
        router.key(1, 103, true)
        assertEquals(256, input.frameMask())
        router.key(1, 109, true)
        assertTrue(events.isEmpty())
        router.key(1, 103, false)
        router.key(1, 109, false)
        assertTrue(events.isEmpty())
    }

    @Test fun chordCannotCombineDifferentControllers() {
        val router = router(mapOf(Action.SAVE_STATE to listOf("BUTTON_SELECT+BUTTON_R1")))
        router.key(1, 109, true); router.key(2, 103, true)
        assertTrue(events.isEmpty())
        assertEquals(256, input.frameMask())
        router.releaseDevice(1)
        assertEquals(256, input.frameMask())
        router.releaseDevice(2)
        assertEquals(0, input.frameMask())
    }

    @Test fun eachControllerHasItsOwnMappingAndDisconnectReleasesOnlyItsHolds() {
        val router = ControllerInputRouter(input, { device -> InputProfile(mapOf(
            (if (device == 1) Btn.START2 else Btn.SELECT2) to listOf("BUTTON_A")), emptyMap()) }, events::add)
        router.key(1, 96, true); router.key(2, 96, true)
        assertEquals(0xC00, input.frameMask())
        router.releaseDevice(1)
        assertEquals(0x800, input.frameMask())
        router.key(2, 96, false)
        assertEquals(0, input.frameMask())
    }

    @Test fun digitalAndAnalogTriggerHoldOneActionUntilBothRelease() {
        val router = router(mapOf(Action.FF_HOLD to listOf("BUTTON_R2")))
        router.key(1, 105, true); router.axes(1, setOf(105))
        router.key(1, 105, false)
        assertEquals(listOf(Hotkeys.Event(Action.FF_HOLD, true)), events)
        router.axes(1, emptySet())
        assertEquals(listOf(Hotkeys.Event(Action.FF_HOLD, true), Hotkeys.Event(Action.FF_HOLD, false)), events)
    }

    @Test fun sameHoldOnTwoControllersReleasesAfterTheLastOwner() {
        val router = router(mapOf(Action.FF_HOLD to listOf("BUTTON_R2")))
        router.key(1, 105, true); router.key(2, 105, true)
        router.releaseDevice(1)
        assertEquals(listOf(Hotkeys.Event(Action.FF_HOLD, true)), events)
        router.releaseDevice(2)
        assertEquals(Hotkeys.Event(Action.FF_HOLD, false), events.last())
        assertEquals(2, events.size)
    }

    @Test fun remappedHatStickAndTriggersUseTheSameProfileAndNeutralReleasesAll() {
        val router = router(buttons = mapOf(Btn.START2 to listOf("STICK_LEFT"), Btn.SELECT2 to listOf("DPAD_UP"), Btn.A to listOf("BUTTON_L2")))
        router.axes(1, InputCodes.axes(0f, -1f, -1f, 0f, 1f, 0f))
        assertEquals(0xC01, input.frameMask())
        router.axes(1, InputCodes.axes(0f, 0f, 0f, 0f, 0f, 0f))
        assertEquals(0, input.frameMask())
    }

    @Test fun ambiguousImportedHotkeysAndButtonsFailClosed() {
        val raw = mapOf(Action.SAVE_STATE to listOf("BUTTON_SELECT+BUTTON_R1"), Action.FF_HOLD to listOf("BUTTON_SELECT"))
        val router = router(raw, mapOf(Btn.A to listOf("BUTTON_A"), Btn.B to listOf("BUTTON_A")))
        router.key(1, 109, true); router.key(1, 103, true); router.key(1, 96, true)
        assertTrue(events.isEmpty()); assertEquals(0, input.frameMask())
        assertTrue(Hotkeys.conflicted(raw, Action.SAVE_STATE))
    }

    @Test fun hotkeysOffReturnsBindingsToTheGameWithoutLeavingHolds() {
        val router = router(mapOf(Action.FF_HOLD to listOf("BUTTON_A")))
        router.key(1, 96, true)
        assertEquals(0, input.frameMask())
        router.hotkeysEnabled = false
        assertEquals(Hotkeys.Event(Action.FF_HOLD, false), events.last())
        router.key(1, 96, true)
        assertEquals(1, input.frameMask())
    }

    @Test fun menuOwnershipDropsGameKeysAndDeferredTaps() {
        val router = router(mapOf(Action.SAVE_STATE to listOf("BUTTON_SELECT+BUTTON_R1")))
        router.key(1, 109, true, gameAllowed = false)
        router.key(1, 109, false, gameAllowed = false)
        router.key(1, 96, true, gameAllowed = false)
        assertEquals(0, input.frameMask())
        router.key(1, 96, false)
        assertEquals(0, input.frameMask())
    }

    @Test fun reentrantUiActionStopsRemainingAxisKeysInTheBatch() {
        lateinit var router: ControllerInputRouter
        router = ControllerInputRouter(input, { InputProfile(GbaControls.defaults(), mapOf(Action.SWAP_SCREENS to listOf("BUTTON_R2"))) }, {
            if (it.pressed) router.releaseAll()
        })
        router.axes(1, linkedSetOf(105, InputCodes.STICK_UP))
        assertEquals(0, input.frameMask())
    }

    @Test fun gbaStillUsesXAndYAsExtraStartAndSelectThroughProfiles() {
        input.ds = false
        val router = router()
        router.key(1, 99, true); router.key(2, 100, true)
        assertEquals(12, input.frameMask())
        router.key(1, 108, true); router.key(1, 99, false)
        assertEquals(12, input.frameMask())
        router.releaseAll(); assertEquals(0, input.frameMask())
    }
}
