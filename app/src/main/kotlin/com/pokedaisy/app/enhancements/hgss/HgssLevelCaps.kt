package com.pokedaisy.app.enhancements.hgss

import com.pokedaisy.app.companion.data.gen3ExperienceTables

/** HGSS's six curves match the existing tables at levels 2..100; HGSS level 1 is zero. */
internal enum class HgssGrowth(private val table: Int) {
    MEDIUM_FAST(0), ERRATIC(1), FLUCTUATING(2), MEDIUM_SLOW(3), FAST(4), SLOW(5);
    fun threshold(level: Int): Long {
        require(level in 1..100)
        return if (level == 1) 0 else gen3ExperienceTables[table][level].toLong()
    }
}

internal enum class HgssCapMode { OFF, HARD, SOFT }
/** Candidate policy only until product approval; callers must opt in explicitly. */
internal enum class HgssSoftBoundary { START_OF_CAP_LEVEL }

internal object HgssLevelCaps {
    /** Null disables this modifier, rather than choosing an arbitrary cap or altering an invalid EXP value. */
    fun limit(currentExp: Long, award: Long, growth: HgssGrowth, mode: HgssCapMode, cap: Int?,
              softBoundary: HgssSoftBoundary? = null): Long? {
        if (award < 0 || currentExp !in 0..growth.threshold(100)) return null
        if (mode == HgssCapMode.OFF) return award
        if (cap == null || cap !in 1..100) return null
        val roomToMaximum = growth.threshold(100) - currentExp
        return when (mode) {
            HgssCapMode.OFF -> award
            HgssCapMode.HARD -> {
                val ceiling = if (cap == 100) growth.threshold(100) else growth.threshold(cap + 1) - 1
                minOf(award, (ceiling - currentExp).coerceAtLeast(0), roomToMaximum)
            }
            HgssCapMode.SOFT -> {
                if (softBoundary != HgssSoftBoundary.START_OF_CAP_LEVEL) return null
                val normal = minOf(award, (growth.threshold(cap) - currentExp).coerceAtLeast(0))
                // Divide only the portion after reaching the cap; no fractional credit survives an award.
                minOf(normal + (award - normal) / 10, roomToMaximum)
            }
        }
    }
}

internal enum class HgssFact { YES, NO, UNKNOWN }
internal enum class HgssGym(val badge: Int, val aceLevel: Int) {
    FALKNER(0, 13), BUGSY(1, 17), WHITNEY(2, 19), MORTY(3, 25), CHUCK(4, 31),
    JASMINE(5, 35), PRYCE(6, 34), CLAIR(7, 41), BROCK(8, 54), MISTY(9, 54),
    SURGE(10, 53), ERIKA(11, 56), JANINE(12, 50), SABRINA(13, 55), BLAINE(14, 59), BLUE(15, 60),
}
internal data class HgssGymState(val defeated: HgssFact, val accessible: HgssFact)
internal sealed interface HgssCapResolution {
    data class Known(val level: Int, val leaders: Set<HgssGym>) : HgssCapResolution
    data class Uncertain(val accessible: Set<HgssGym>, val unknown: Set<HgssGym>) : HgssCapResolution
    data object NoAccessibleGym : HgssCapResolution
    data object PostGymPolicyRequired : HgssCapResolution
}

/** Resolves a complete observed world, never a badge count or guide order. No live flag decoder is certified yet. */
internal object HgssProgression {
    fun resolve(observed: Map<HgssGym, HgssGymState>): HgssCapResolution {
        val accessible = mutableSetOf<HgssGym>()
        val unknown = mutableSetOf<HgssGym>()
        for (gym in HgssGym.entries) {
            val state = observed[gym]
            when {
                state == null -> unknown += gym
                state.defeated == HgssFact.YES -> Unit
                state.defeated == HgssFact.UNKNOWN || state.accessible == HgssFact.UNKNOWN -> unknown += gym
                state.accessible == HgssFact.NO -> Unit
                else -> accessible += gym
            }
        }
        if (unknown.isNotEmpty()) return HgssCapResolution.Uncertain(accessible, unknown)
        if (accessible.isNotEmpty()) {
            val level = accessible.minOf { it.aceLevel }
            return HgssCapResolution.Known(level, accessible.filterTo(mutableSetOf()) { it.aceLevel == level })
        }
        return if (HgssGym.entries.all { observed[it]?.defeated == HgssFact.YES })
            HgssCapResolution.PostGymPolicyRequired else HgssCapResolution.NoAccessibleGym
    }
}
