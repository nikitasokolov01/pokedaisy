package com.pokedaisy.app

import com.pokedaisy.app.GbaControls.Btn
import org.junit.Assert.*
import org.junit.Test

class CompanionOwnershipTest {
    private val input = GbaInput().apply { ds = true }
    private val edges = mutableListOf<Triple<Int, Int, Boolean>>()
    private val actions = mutableListOf<Hotkeys.Event>()
    private val router = ControllerInputRouter(input, { InputProfile(GbaControls.defaults(),
        mapOf(Hotkeys.Action.COMPANION to listOf("BUTTON_SELECT+BUTTON_R1"))) }, actions::add,
        { device, code, down -> edges += Triple(device, code, down) })

    @Test fun ordinaryMenuKeysAndDeferredModifierTapsNeverReachTheCore() {
        router.key(1, 96, true, false); router.key(1, 96, false, false)
        router.key(1, 109, true, false); router.key(1, 109, false, false)
        assertEquals(listOf(Triple(1,96,true), Triple(1,96,false), Triple(1,109,true), Triple(1,109,false)), edges)
        repeat(3) { assertEquals(0, input.frameMask()) }
    }

    @Test fun completedShortcutReachesNeitherMenuNorGame() {
        router.key(1, 109, true, false); router.key(1, 103, true, false)
        router.key(1, 109, false, false); router.key(1, 103, false, false)
        assertTrue(edges.isEmpty()); assertEquals(2, actions.size)
        assertEquals(0, input.frameMask())
    }

    @Test fun heldKeyCannotCrossEitherOwnershipBoundary() {
        router.key(1, 96, true)
        assertEquals(1, input.frameMask())
        router.quarantineHeld()
        router.key(1, 96, true, false); router.key(1, 96, false, false)
        assertTrue(edges.isEmpty()); assertEquals(0, input.frameMask())
        router.key(1, 96, true, false)
        assertEquals(Triple(1,96,true), edges.single())
        router.quarantineHeld()
        router.key(1, 96, true); router.key(1, 96, false)
        assertEquals(0, input.frameMask())
        router.key(1, 96, true); assertEquals(1, input.frameMask())
    }

    @Test fun analogDirectionChangeAndDuplicateDigitalReportRequireNeutralAfterSwitch() {
        router.axes(1, setOf(19))
        router.quarantineHeld()
        router.key(1, 20, true, false)
        router.axes(1, setOf(20), false)
        assertTrue(edges.isEmpty())
        router.axes(1, emptySet(), false)
        router.key(1, 20, false, false)
        router.axes(1, setOf(20), false)
        assertEquals(listOf(Triple(1,20,true)), edges)
        assertEquals(0, input.frameMask())
    }

    @Test fun remappedAxesAndMergedTriggersUseMenuEdgesOnlyOnce() {
        val routed = mutableListOf<Int>()
        lateinit var r: ControllerInputRouter
        r = ControllerInputRouter(input, { InputProfile(mapOf(Btn.A to listOf("STICK_DOWN"), Btn.START2 to listOf("BUTTON_R2")), emptyMap()) }, {},
            { device, code, down -> if (down) routed += r.buttonFor(device, code)!! })
        r.axes(1, setOf(InputCodes.STICK_DOWN, 105), false)
        r.key(1, 105, true, false)
        assertEquals(listOf(1, 1024), routed)
        assertEquals(0, input.frameMask())
    }

    @Test fun closingCompanionDuringAnAxisBatchDoesNotActivateItsRemainingCommands() {
        var count = 0
        lateinit var r: ControllerInputRouter
        r = ControllerInputRouter(input, { InputProfile(GbaControls.defaults(), emptyMap()) }, {},
            { _, _, down -> if (down) { count++; r.quarantineHeld() } })
        r.axes(1, linkedSetOf(19, 20, 104), false)
        assertEquals(1, count)
        r.axes(1, linkedSetOf(19, 20, 104))
        assertEquals(0, input.frameMask())
    }
}
