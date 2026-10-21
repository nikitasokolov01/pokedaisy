package com.pokedaisy.app.enhancements.hgss

import org.junit.Assert.*
import org.junit.Test
import com.pokedaisy.app.companion.data.flySpotsHeartGold
import java.security.MessageDigest

class HgssFieldMovesTest {
    private class Port : HgssFieldPort {
        var context: HgssFieldContext? = HgssFieldContext(7, HgssFieldRequest(7, HgssHm.CUT), true, HgssFact.YES,
            (420..427).toSet(), 0xffff, HgssFact.YES, HgssFact.YES, HgssFact.YES)
        var staleCheck: HgssFieldRequest? = null
        var inspectCount = 0
        var requests = mutableListOf<HgssFieldRequest>()
        var accepts = true
        override fun inspect(request: HgssFieldRequest): HgssFieldContext? {
            inspectCount++
            return context?.copy(checkedRequest = staleCheck ?: request)
        }
        override fun invoke(request: HgssFieldRequest, context: HgssFieldContext): Boolean {
            requests += request
            return accepts
        }
    }
    private fun request(hm: HgssHm = HgssHm.CUT) = HgssFieldRequest(7, hm, if (hm == HgssHm.FLY) 1 else null)
    private fun gate(port: Port) = HgssFieldMoves(port, HgssHmPossession.CURRENT_BAG)

    @Test fun hmConstantsMatchIndependentlyExtractedNativeMapping() {
        // Produced from pinned item.c + items.h + field_move.c + badge.h by verify_hgss_rule_sources.py.
        val canonical = HgssHm.entries.joinToString("\n", postfix = "\n") { "${it.name}:${it.item}:${it.badge}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("62f4ce905000401e12bf47972e5b0e0b05bb259b0c8528c0191b5611ca1baa39", digest)
    }

    @Test fun everyHmUsesItsOwnHgssItemAndBadgeForBothEntryPaths() {
        val expected = listOf(Triple(HgssHm.CUT, 420, 1), Triple(HgssHm.FLY, 421, 4), Triple(HgssHm.SURF, 422, 3),
            Triple(HgssHm.STRENGTH, 423, 2), Triple(HgssHm.WHIRLPOOL, 424, 6), Triple(HgssHm.ROCK_SMASH, 425, 0),
            Triple(HgssHm.WATERFALL, 426, 7), Triple(HgssHm.ROCK_CLIMB, 427, 15))
        for ((hm, item, badge) in expected) for (source in HgssFieldSource.entries) {
            val port = Port()
            port.context = port.context!!.copy(items = setOf(item), badges = 1 shl badge)
            assertEquals(HgssFieldResult.STARTED, gate(port).use(source, request(hm)))
            assertEquals(listOf(request(hm)), port.requests)
            port.requests.clear()
            port.context = port.context!!.copy(badges = (1 shl badge) xor 0xffff)
            assertEquals(HgssFieldResult.NEED_BADGE, gate(port).use(source, request(hm)))
            assertTrue(port.requests.isEmpty())
        }
    }
    @Test fun absentItemCannotBeReplacedByBadgeOrKnownPartyMove() {
        val port = Port().apply { context = context!!.copy(items = emptySet()) }
        for (source in HgssFieldSource.entries)
            assertEquals(HgssFieldResult.NEED_ITEM, gate(port).use(source, request()))
        assertTrue(port.requests.isEmpty())
        // The API intentionally has no party-knows-move or progression-write parameter.
    }
    @Test fun storyLocationAndNativeRestrictionsAreNeverBypassedByTheShortcut() {
        val cases = listOf(
            HgssFieldResult.STORY_BLOCKED to { c: HgssFieldContext -> c.copy(storyAllowed = HgssFact.NO) },
            HgssFieldResult.LOCATION_BLOCKED to { c: HgssFieldContext -> c.copy(nativeCheck = HgssFact.NO) },
            HgssFieldResult.BUSY to { c: HgssFieldContext -> c.copy(idle = HgssFact.NO) },
            HgssFieldResult.DISABLED to { c: HgssFieldContext -> c.copy(enabled = false) },
        )
        for ((expected, change) in cases) for (source in HgssFieldSource.entries) {
            val port = Port().apply { context = change(context!!) }
            assertEquals(expected, gate(port).use(source, request()))
            assertTrue(port.requests.isEmpty())
        }
    }
    @Test fun missingOrUnknownMetadataNeverLooksLikePermission() {
        val changes = listOf<(HgssFieldContext) -> HgssFieldContext>(
            { it.copy(items = null) }, { it.copy(badges = null) }, { it.copy(badges = -1) },
            { it.copy(badges = 65536) }, { it.copy(idle = HgssFact.UNKNOWN) },
            { it.copy(storyAllowed = HgssFact.UNKNOWN) }, { it.copy(nativeCheck = HgssFact.UNKNOWN) },
        )
        for (change in changes) {
            val port = Port().apply { context = change(context!!) }
            assertEquals(HgssFieldResult.UNKNOWN, gate(port).use(HgssFieldSource.WORLD, request()))
            assertTrue(port.requests.isEmpty())
        }
        val missing = Port().apply { context = null }
        assertEquals(HgssFieldResult.UNKNOWN, gate(missing).use(HgssFieldSource.COMPANION, request()))
    }
    @Test fun policyMustBeExplicitAndPermissionMustBeRecheckedAtUseTime() {
        val port = Port()
        assertEquals(HgssFieldResult.POLICY_REQUIRED, HgssFieldMoves(port, null).use(HgssFieldSource.WORLD, request()))
        val moves = gate(port)
        assertEquals(HgssFieldResult.STARTED, moves.use(HgssFieldSource.WORLD, request()))
        port.context = port.context!!.copy(items = emptySet())
        assertEquals(HgssFieldResult.NEED_ITEM, moves.use(HgssFieldSource.COMPANION, request()))
        assertEquals(1, port.requests.size)
        assertEquals(3, port.inspectCount)
    }
    @Test fun staleRequestAfterMapLoadResetOrStateLoadCannotInvoke() {
        val port = Port().apply { context = context!!.copy(generation = 8) }
        assertEquals(HgssFieldResult.STALE, gate(port).use(HgssFieldSource.COMPANION, request()))
        assertEquals(HgssFieldResult.STALE, gate(port).use(HgssFieldSource.WORLD, request().copy(generation = -1)))
        assertTrue(port.requests.isEmpty())
    }
    @Test fun flyRequiresVerifiedDestinationAndOtherMovesRejectOne() {
        for (destination in listOf(null, 0, -1, flySpotsHeartGold.size + 1, Int.MAX_VALUE)) {
            val port = Port()
            assertEquals(HgssFieldResult.DESTINATION_BLOCKED,
                gate(port).use(HgssFieldSource.COMPANION, request(HgssHm.FLY).copy(destination = destination)))
            assertTrue(port.requests.isEmpty())
            assertEquals(0, port.inspectCount)
        }
        val port = Port().apply { context = context!!.copy(destinationUnlocked = HgssFact.NO) }
        assertEquals(HgssFieldResult.DESTINATION_BLOCKED, gate(port).use(HgssFieldSource.WORLD, request(HgssHm.FLY)))
        port.context = port.context!!.copy(destinationUnlocked = HgssFact.UNKNOWN)
        assertEquals(HgssFieldResult.UNKNOWN, gate(port).use(HgssFieldSource.WORLD, request(HgssHm.FLY)))
        assertEquals(HgssFieldResult.DESTINATION_BLOCKED, gate(port).use(HgssFieldSource.WORLD, request().copy(destination = 1)))
        assertTrue(port.requests.isEmpty())
    }
    @Test fun eligibilityForAnotherMoveOrDestinationCannotBeReused() {
        val port = Port()
        port.staleCheck = request(HgssHm.FLY).copy(destination = 2)
        assertEquals(HgssFieldResult.STALE, gate(port).use(HgssFieldSource.COMPANION, request(HgssHm.FLY)))
        port.staleCheck = request(HgssHm.SURF)
        assertEquals(HgssFieldResult.STALE, gate(port).use(HgssFieldSource.WORLD, request()))
        assertTrue(port.requests.isEmpty())
    }
    @Test fun nativeRefusalCannotBeReportedAsSuccessOrRetriedAutomatically() {
        val port = Port().apply { accepts = false }
        assertEquals(HgssFieldResult.NATIVE_REFUSED, gate(port).use(HgssFieldSource.WORLD, request()))
        assertEquals(1, port.requests.size)
    }
}
