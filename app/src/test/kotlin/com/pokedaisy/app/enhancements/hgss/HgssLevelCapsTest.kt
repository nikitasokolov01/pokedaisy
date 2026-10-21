package com.pokedaisy.app.enhancements.hgss

import java.security.MessageDigest
import com.pokedaisy.app.companion.data.HG_BOSSES_RAW
import org.junit.Assert.*
import org.junit.Test

class HgssLevelCapsTest {
    private val curve = HgssGrowth.MEDIUM_FAST
    private val soft = HgssSoftBoundary.START_OF_CAP_LEVEL
    private fun limit(exp: Long, award: Long, mode: HgssCapMode, cap: Int? = 10) =
        HgssLevelCaps.limit(exp, award, curve, mode, cap, soft)

    @Test fun everyThresholdMatchesIndependentHgssSourceDigest() {
        // SHA-256 of comma-separated levels 1..100, computed from the pinned pret growtbl.csv, not our tables.
        val expected = listOf(
            "f5943975513d5b5528f6a6863eb1b9aa5fef138599817f90d6c3aea6699ae2d8",
            "6dde0f59f76c5cd5cd80b0582d0a3dd12280b4a19b6989a4ca1ec7168bf4ba84",
            "f35df24527cf5c891568c8ea9b0a531931ba776a1d2b911514fde192580c31ca",
            "0958c966fbff1031ca5475e4ed8e86a4e3de792886ac232a9525ce616b20f397",
            "44057b438fc9ec4b8c17827cb362db54135c223a15edf20a20671352af98cf1e",
            "7529af7b58eaa449ef21c1287828f549db69894219193b48675c0c38de9fcb47",
        )
        HgssGrowth.entries.forEachIndexed { index, growth ->
            val csv = (1..100).joinToString(",") { growth.threshold(it).toString() }
            val hash = MessageDigest.getInstance("SHA-256").digest(csv.toByteArray()).joinToString("") { "%02x".format(it) }
            assertEquals(growth.name, expected[index], hash)
        }
    }
    @Test fun hardCapStopsAtLastPointBeforeNextLevelWithoutRemovingExistingExp() {
        assertEquals(330L, limit(1000, 500, HgssCapMode.HARD)) // level 11 begins at 1331
        assertEquals(0L, limit(1330, 1, HgssCapMode.HARD))
        assertEquals(0L, limit(2000, 500, HgssCapMode.HARD))
        assertEquals(7L, limit(0, 100, HgssCapMode.HARD, 1))
    }
    @Test fun softCapSplitsCrossingAwardBeforeDiscountAndDropsFractions() {
        assertEquals(24L, limit(990, 150, HgssCapMode.SOFT)) // 10 full + floor(140/10)
        assertEquals(15L, limit(1000, 159, HgssCapMode.SOFT))
        assertEquals(0L, limit(1000, 9, HgssCapMode.SOFT))
        assertEquals(0L, limit(1000, 9, HgssCapMode.SOFT)) // no stored fraction to spend next time
        assertEquals(100L, limit(800, 100, HgssCapMode.SOFT))
    }
    @Test fun unresolvedPolicyOrCapDisablesOnlyTheCapCalculation() {
        assertNull(HgssLevelCaps.limit(1000, 10, curve, HgssCapMode.SOFT, 10))
        assertNull(limit(1000, 10, HgssCapMode.HARD, null))
        assertNull(limit(1000, 10, HgssCapMode.HARD, 0))
        assertEquals(999L, limit(1000, 999, HgssCapMode.OFF, null))
        assertNull(limit(-1, 20, HgssCapMode.HARD))
        assertNull(limit(0, -1, HgssCapMode.HARD))
    }
    @Test fun allGrowthCurvesCapExactlyAndNeverOverflowOrAccumulateHiddenExp() {
        for (growth in HgssGrowth.entries) for (cap in 1..100) {
            val current = growth.threshold(cap)
            val ceiling = if (cap == 100) current else growth.threshold(cap + 1) - 1
            assertEquals(ceiling - current, HgssLevelCaps.limit(current, Long.MAX_VALUE, growth, HgssCapMode.HARD, cap))
            val award = HgssLevelCaps.limit(current, Long.MAX_VALUE, growth, HgssCapMode.SOFT, cap, soft)!!
            assertTrue(award >= 0 && award + current <= growth.threshold(100))
        }
        assertEquals(0L, limit(1_000_000, 100, HgssCapMode.HARD, 100))
        assertEquals(1L, limit(999_999, 100, HgssCapMode.SOFT, 100))
    }
    @Test fun shareAndCapOperateOnceOnTheFinalBoostedAward() {
        val party = listOf(HgssExpMon(0, true, false, 10, 9, true, HgssHeldExpEffect.NONE, HgssTrainerRelation.ORIGINAL),
            HgssExpMon(1, true, false, 10, 9, false, HgssHeldExpEffect.BOOST, HgssTrainerRelation.TRADED_OTHER_LANGUAGE))
        val award = HgssExperience.plan(100, 14, true, HgssBattleKind.SINGLE, party, true)!![1].selected
        assertEquals(382L, award) // 200 -> 300 -> 450 -> 765 -> floor(765/2)
        assertEquals(47L, limit(990, award, HgssCapMode.SOFT))
        assertEquals(340L, limit(990, award, HgssCapMode.HARD))
    }
}

class HgssProgressionTest {
    private fun world() = HgssGym.entries.associateWith { HgssGymState(HgssFact.NO, HgssFact.NO) }.toMutableMap()
    @Test fun aceLevelsMatchTheExistingIndependentlyGeneratedHgssTrainerTeams() {
        val teams = HG_BOSSES_RAW.flatMap { it.lineSequence().filter(String::isNotBlank).toList() }
            .associate { line ->
                val fields = line.split('|')
                fields[0] to fields[2].split(';').maxOf { it.substringBefore(' ').toInt() }
            }
        for (gym in HgssGym.entries) assertEquals(gym.name, teams[if (gym == HgssGym.SURGE) "LT_SURGE" else gym.name], gym.aceLevel)
    }
    @Test fun nonlinearJohtoChoosesLowestAccessibleAceNotBadgeCountOrGuideOrder() {
        val states = world()
        listOf(HgssGym.JASMINE, HgssGym.PRYCE).forEach { states[it] = HgssGymState(HgssFact.NO, HgssFact.YES) }
        assertEquals(HgssCapResolution.Known(34, setOf(HgssGym.PRYCE)), HgssProgression.resolve(states))
        states[HgssGym.CHUCK] = HgssGymState(HgssFact.NO, HgssFact.YES)
        assertEquals(HgssCapResolution.Known(31, setOf(HgssGym.CHUCK)), HgssProgression.resolve(states))
    }
    @Test fun kantoUsesAccessibleLeadersAndReturnsTiesWithoutInventingAnOrder() {
        val states = world()
        listOf(HgssGym.BROCK, HgssGym.MISTY).forEach { states[it] = HgssGymState(HgssFact.NO, HgssFact.YES) }
        assertEquals(HgssCapResolution.Known(54, setOf(HgssGym.BROCK, HgssGym.MISTY)), HgssProgression.resolve(states))
        states[HgssGym.JANINE] = HgssGymState(HgssFact.NO, HgssFact.YES)
        assertEquals(HgssCapResolution.Known(50, setOf(HgssGym.JANINE)), HgssProgression.resolve(states))
    }
    @Test fun unknownFlagsNeverBecomeFalseAndMissingDataShowsCandidates() {
        val states = world()
        states[HgssGym.JASMINE] = HgssGymState(HgssFact.NO, HgssFact.YES)
        states[HgssGym.CHUCK] = HgssGymState(HgssFact.NO, HgssFact.UNKNOWN)
        assertEquals(HgssCapResolution.Uncertain(setOf(HgssGym.JASMINE), setOf(HgssGym.CHUCK)), HgssProgression.resolve(states))
        states.remove(HgssGym.CLAIR)
        assertTrue((HgssProgression.resolve(states) as HgssCapResolution.Uncertain).unknown.contains(HgssGym.CLAIR))
    }
    @Test fun noReachableGymAndPostGymAreExplicitlyUnresolved() {
        assertEquals(HgssCapResolution.NoAccessibleGym, HgssProgression.resolve(world()))
        val states = HgssGym.entries.associateWith { HgssGymState(HgssFact.YES, HgssFact.UNKNOWN) }
        assertEquals(HgssCapResolution.PostGymPolicyRequired, HgssProgression.resolve(states))
    }
    @Test fun unknownDefeatCannotBeHiddenByAnInaccessibleGym() {
        val states = HgssGym.entries.associateWith { HgssGymState(HgssFact.YES, HgssFact.NO) }.toMutableMap()
        states[HgssGym.BLUE] = HgssGymState(HgssFact.UNKNOWN, HgssFact.NO)
        assertEquals(HgssCapResolution.Uncertain(emptySet(), setOf(HgssGym.BLUE)), HgssProgression.resolve(states))
    }
    @Test fun rewindOrResetDoesNotReuseALaterCap() {
        val old = world().apply { this[HgssGym.FALKNER] = HgssGymState(HgssFact.NO, HgssFact.YES) }
        val later = world().apply { this[HgssGym.BUGSY] = HgssGymState(HgssFact.NO, HgssFact.YES) }
        assertEquals(13, (HgssProgression.resolve(old) as HgssCapResolution.Known).level)
        assertEquals(17, (HgssProgression.resolve(later) as HgssCapResolution.Known).level)
        assertEquals(13, (HgssProgression.resolve(old) as HgssCapResolution.Known).level)
    }
}
