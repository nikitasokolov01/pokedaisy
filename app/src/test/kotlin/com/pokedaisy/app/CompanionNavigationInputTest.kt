package com.pokedaisy.app

import com.pokedaisy.app.companion.ui.MenuKey
import org.junit.Assert.*
import org.junit.Test

class CompanionNavigationInputTest {
    private val sent = mutableListOf<Pair<MenuKey, Boolean>>()
    private val nav = CompanionNavigationInput { key, repeat -> sent += key to repeat }

    @Test fun directionRepeatsWithoutMoreMotionEventsButActionsDoNot() {
        nav.edge(1, -102, MenuKey.DOWN, true, 0)
        nav.edge(1, 96, MenuKey.A, true, 0)
        nav.tick(349); assertEquals(2, sent.size)
        nav.tick(350); nav.tick(439); nav.tick(440)
        assertEquals(listOf(MenuKey.DOWN to false, MenuKey.A to false, MenuKey.DOWN to true, MenuKey.DOWN to true), sent)
        nav.edge(1, -102, null, false, 450)
        nav.tick(1000); assertEquals(4, sent.size)
        assertFalse(nav.repeating)
    }

    @Test fun twoControllersCannotReleaseEachOthersCursor() {
        nav.edge(1, 20, MenuKey.DOWN, true, 0)
        nav.edge(2, 20, MenuKey.DOWN, true, 0)
        nav.releaseDevice(1)
        nav.tick(350)
        assertEquals(3, sent.size)
        nav.releaseDevice(2); nav.tick(1000)
        assertEquals(3, sent.size)
    }

    @Test fun ownershipChangeStopsTheRepeatBatchReentrantly() {
        lateinit var n: CompanionNavigationInput
        n = CompanionNavigationInput { _, repeat -> if (repeat) n.clear() }
        n.edge(1, 19, MenuKey.UP, true, 0)
        n.edge(2, 20, MenuKey.DOWN, true, 0)
        n.tick(350)
        assertFalse(n.repeating)
    }
}
