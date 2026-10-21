package com.pokedaisy.app.enhancements.hgss

import org.junit.Assert.*
import org.junit.Test

class HgssExperienceTest {
    private fun mon(slot: Int, participated: Boolean = false) = HgssExpMon(slot, true, false, 20, 12,
        participated, HgssHeldExpEffect.NONE, HgssTrainerRelation.ORIGINAL)
    private fun awards(party: List<HgssExpMon>, share: Boolean = true, trainer: Boolean = false,
                       yield: Int = 100, level: Int = 14, kind: HgssBattleKind = HgssBattleKind.SINGLE) =
        HgssExperience.plan(yield, level, trainer, kind, party, share)!!

    @Test fun participantsKeepNativeSplitAndOthersGetHalfUnsplit() {
        val party = listOf(mon(0, true), mon(1, true), mon(2))
        assertEquals(listOf(100L, 100L, 100L), awards(party).map { it.selected })
        assertEquals(listOf(100L, 100L, 0L), awards(party, share = false).map { it.selected })
    }
    @Test fun heldShareUsesHigherAwardInsteadOfAddingTwo() {
        val party = listOf(mon(0, true), mon(1).copy(heldEffect = HgssHeldExpEffect.SHARE),
            mon(2).copy(heldEffect = HgssHeldExpEffect.SHARE), mon(3))
        val result = awards(party)
        assertEquals(listOf(100L, 50L, 50L, 0L), result.map { it.vanilla })
        assertEquals(listOf(100L, 100L, 100L, 100L), result.map { it.selected })
        val participantHolder = awards(listOf(party[0].copy(heldEffect = HgssHeldExpEffect.SHARE), mon(1)))
        assertEquals(200L, participantHolder[0].selected)
    }
    @Test fun nativeHeldAwardCanExceedRoundedModernAward() {
        // The original minimum-one rule on a held Share is retained, not replaced by floor(1 / 2).
        val result = awards(listOf(mon(0, true), mon(1).copy(heldEffect = HgssHeldExpEffect.SHARE)), yield = 1, level = 7)
        assertEquals(1L, result[1].vanilla)
        assertEquals(1L, result[1].selected)
    }
    @Test fun boostsKeepNativeOrderAndFloorAtEachStepBeforeModernHalving() {
        val party = listOf(mon(0, true), mon(1).copy(heldEffect = HgssHeldExpEffect.BOOST,
            trainer = HgssTrainerRelation.TRADED_OTHER_LANGUAGE),
            mon(2).copy(trainer = HgssTrainerRelation.TRADED_SAME_LANGUAGE))
        // Base 5: lucky 7 -> trainer 10 -> foreign 17 -> share 8; trainer 7 -> same-language 10 -> share 5.
        val result = awards(party, trainer = true, yield = 5, level = 7)
        assertEquals(listOf(7L, 8L, 5L), result.map { it.selected })
    }
    @Test fun faintedEggEmptyAndMaxLevelNeverEarn() {
        val party = listOf(mon(0, true), mon(1).copy(hp = 0), mon(2).copy(egg = true),
            mon(3).copy(present = false), mon(4).copy(level = 100))
        assertEquals(listOf(200L, 0L, 0L, 0L, 0L), awards(party).map { it.selected })
    }
    @Test fun livingMaxLevelParticipantStillCountsInVanillaDenominator() {
        val result = awards(listOf(mon(0, true), mon(1, true).copy(level = 100), mon(2)))
        assertEquals(listOf(100L, 0L, 100L), result.map { it.selected })
    }
    @Test fun doubleKnockoutsAreIndependentPurePlansWithNoRetainedAward() {
        val party = listOf(mon(0, true), mon(1), mon(2, true))
        val first = awards(party, kind = HgssBattleKind.DOUBLE)
        val second = awards(party.map { it.copy(participated = it.slot == 1) }, yield = 30, kind = HgssBattleKind.DOUBLE)
        assertEquals(listOf(30L, 60L, 30L), second.map { it.selected })
        assertEquals(first, awards(party, kind = HgssBattleKind.DOUBLE))
        assertEquals(listOf(100L, 100L, 100L), first.map { it.selected })
    }
    @Test fun unknownSpecialBattlesAndMalformedSnapshotsDeferToNative() {
        for (kind in HgssBattleKind.entries.filter { it !in setOf(HgssBattleKind.SINGLE, HgssBattleKind.DOUBLE) })
            assertNull(HgssExperience.plan(100, 14, false, kind, listOf(mon(0, true)), true))
        for (party in listOf(emptyList(), listOf(mon(0)), listOf(mon(0, true), mon(0)),
            listOf(mon(0, true).copy(egg = true)), listOf(mon(6, true)), listOf(mon(0, true), mon(2)),
            listOf(mon(1, true)), listOf(mon(0, true).copy(hp = -1))))
            assertNull(HgssExperience.plan(100, 14, false, HgssBattleKind.SINGLE, party, true))
        assertNull(HgssExperience.plan(Int.MAX_VALUE, 100, false, HgssBattleKind.SINGLE, listOf(mon(0, true)), true))
    }
    @Test fun partyReorderingNeverChangesSlotAwardsAndDisablingMatchesVanilla() {
        for (count in 1..6) {
            val party = (0 until count).map { mon(it, it % 2 == 0).copy(
                heldEffect = if (it % 3 == 0) HgssHeldExpEffect.SHARE else HgssHeldExpEffect.NONE) }
            val result = awards(party)
            assertEquals(result.sortedBy { it.slot }, awards(party.reversed()).sortedBy { it.slot })
            assertTrue(awards(party, share = false).all { it.vanilla == it.selected })
        }
    }
}
