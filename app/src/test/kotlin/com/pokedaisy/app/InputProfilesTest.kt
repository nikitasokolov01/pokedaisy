package com.pokedaisy.app

import com.pokedaisy.app.GbaControls.Btn
import com.pokedaisy.app.Hotkeys.Action
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InputProfilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun legacySettingsMigrateAndFourLayersResolveInOrderAcrossRelaunch() {
        val root = tmp.newFolder()
        File(root, "controls.properties").writeText("start2=BUTTON_C\n")
        val legacy = File(root, "controls.properties").readBytes()
        val store = InputProfiles(root)
        val game = InputProfileScope(game = "test-game")
        val device = InputProfileScope(device = "thor-descriptor")
        val both = InputProfileScope(game.game, device.device)
        assertEquals(listOf("BUTTON_C"), store.resolve(both).buttons[Btn.START2])
        store.bind(device, Btn.START2, "F5")
        store.bind(game, Btn.START2, "F6")
        store.bind(both, Btn.START2, "F7")
        assertEquals(listOf("F7"), InputProfiles(root).resolve(both).buttons[Btn.START2])
        store.reset(both)
        assertEquals(listOf("F6"), store.resolve(both).buttons[Btn.START2])
        store.reset(game)
        assertEquals(listOf("F5"), store.resolve(both).buttons[Btn.START2])
        store.reset(device)
        assertEquals(listOf("BUTTON_C"), store.resolve(both).buttons[Btn.START2])
        store.reset(InputProfileScope())
        assertEquals(GbaControls.defaults(), store.resolve().buttons)
        assertArrayEquals(legacy, File(root, "controls.properties").readBytes())
    }

    @Test fun independentParentChangesExposeCombinedConflictsWithoutChoosingAWinner() {
        val store = InputProfiles(tmp.newFolder())
        store.bind(InputProfileScope(device = "thor"), Btn.B, "Z")
        store.bind(InputProfileScope(game = "game"), Btn.START2, "Z")
        val combined = store.resolve(InputProfileScope("game", "thor"))
        assertTrue(GbaControls.conflicted(combined.buttons, Btn.B))
        assertTrue(GbaControls.conflicted(combined.buttons, Btn.START2))
        assertNull(GbaControls.mapping(combined.buttons)[GbaControls.resolveKey("Z")])
        store.bind(InputProfileScope("game", "thor"), Btn.START2, "Z")
        assertFalse(GbaControls.conflicted(store.resolve(InputProfileScope("game", "thor")).buttons, Btn.START2))
    }

    @Test fun movingAnInheritedButtonDoesNotChangeItsParentOrOtherDevice() {
        val root = tmp.newFolder(); val store = InputProfiles(root)
        val scope = InputProfileScope("game", "thor")
        assertEquals(listOf(Btn.START2), store.buttonClashes(scope, Btn.A, "BUTTON_X"))
        store.bind(scope, Btn.A, "BUTTON_X")
        val saved = InputProfiles(root).resolve(scope)
        assertEquals(listOf("BUTTON_X"), saved.buttons[Btn.A])
        assertEquals(emptyList<String>(), saved.buttons[Btn.START2])
        assertEquals(listOf("BUTTON_X"), store.resolve(InputProfileScope("game", "usb")).buttons[Btn.START2])
        store.bind(scope, Btn.A, null)
        assertEquals(emptyList<String>(), InputProfiles(root).resolve(scope).buttons[Btn.A])
    }

    @Test fun movingSubsetShortcutClearsConflictingInheritedChordsOnly() {
        val root = tmp.newFolder(); val store = InputProfiles(root)
        val scope = InputProfileScope(device = "thor")
        val taken = Hotkeys.clashes(store.resolve(scope).hotkeys, Action.FF_HOLD, listOf("BUTTON_SELECT"))
        assertEquals(setOf(Action.SAVE_STATE, Action.LOAD_STATE, Action.EXIT_GAME), taken.toSet())
        store.bind(scope, Action.FF_HOLD, listOf("BUTTON_SELECT"), taken)
        val saved = InputProfiles(root).resolve(scope)
        assertEquals(listOf("F1"), saved.hotkeys[Action.SAVE_STATE])
        assertEquals(listOf("BUTTON_SELECT"), saved.hotkeys[Action.FF_HOLD])
        assertFalse(Hotkeys.conflicted(saved.hotkeys, Action.FF_HOLD))
        assertEquals(2, store.resolve().hotkeys[Action.SAVE_STATE]!!.size)
    }

    @Test fun unknownSchemaIsPreservedAndReportedWithInheritedFallback() {
        val root = tmp.newFolder(); val store = InputProfiles(root)
        val scope = InputProfileScope()
        store.bind(scope, Btn.A, "F5")
        val file = File(root, "input-profiles/v1").listFiles()!!.single()
        file.writeText("version=900\nbutton.a=F12\n")
        val original = file.readBytes()
        assertTrue(store.unreadable(scope))
        assertEquals(GbaControls.defaults(), store.resolve(scope).buttons)
        assertTrue(runCatching { store.bind(scope, Btn.A, "F6") }.isFailure)
        assertTrue(runCatching { store.reset(scope) }.isFailure)
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun atomicReplacementLeavesOneCompleteProfileAndNoTemporaryFiles() {
        val root = tmp.newFolder(); val store = InputProfiles(root)
        repeat(10) { store.bind(InputProfileScope(), Btn.START2, if (it % 2 == 0) "F5" else "F6") }
        assertEquals(listOf("F6"), InputProfiles(root).resolve().buttons[Btn.START2])
        assertEquals(1, File(root, "input-profiles/v1").listFiles()!!.size)
    }

    @Test fun stableDescriptorsSurviveRuntimeIdChangesButAmbiguousDevicesUseSharedDefaults() {
        assertEquals(InputProfiles.deviceKey("thor-physical", false, 1), InputProfiles.deviceKey("thor-physical", false, 1))
        assertNotEquals(InputProfiles.deviceKey("thor-physical", false, 1), InputProfiles.deviceKey("usb-physical", false, 1))
        assertNull(InputProfiles.deviceKey("thor-physical", false, 2))
        assertNull(InputProfiles.deviceKey("", false, 1))
        assertNull(InputProfiles.deviceKey("virtual", true, 1))
        val root = tmp.newFolder(); val store = InputProfiles(root)
        val scope = InputProfileScope(device = InputProfiles.deviceKey("thor-physical", false, 1))
        store.bind(scope, Btn.START2, "BUTTON_C")
        val input = GbaInput().apply { ds = true }
        val router = ControllerInputRouter(input, { store.resolve(scope) }, {})
        router.key(7, 98, true); assertEquals(0x400, input.frameMask())
        router.releaseDevice(7)
        router.key(32, 98, true); assertEquals(0x400, input.frameMask())
    }
}
