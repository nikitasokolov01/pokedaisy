package com.pokedaisy.app.enhancements.hgss

/** Pure award planning, not a hook. See docs/fork/HGSS_MECHANICS.md for source provenance and admission gates. */
internal enum class HgssHeldExpEffect { NONE, SHARE, BOOST }
internal enum class HgssTrainerRelation { ORIGINAL, TRADED_SAME_LANGUAGE, TRADED_OTHER_LANGUAGE }
internal enum class HgssBattleKind { SINGLE, DOUBLE, LINK, SAFARI, FRONTIER, PAL_PARK, MULTI, UNKNOWN }

internal data class HgssExpMon(
    val slot: Int,
    val present: Boolean,
    val egg: Boolean,
    val hp: Int,
    val level: Int,
    val participated: Boolean,
    val heldEffect: HgssHeldExpEffect,
    val trainer: HgssTrainerRelation,
)

internal data class HgssExpAward(val slot: Int, val vanilla: Long, val selected: Long)

internal object HgssExperience {
    /**
     * One defeated enemy and the player's party at Task_GetExp's START boundary. Null means use native behavior.
     * This never changes EXP, EVs, party data or a task cursor. A future verified hook must substitute the result
     * exactly once inside the native task, retaining its level-up, move-learning and evolution flow.
     * Unsupported multi/trainer-partner battles require a separate ownership audit.
     */
    fun plan(baseYield: Int, enemyLevel: Int, trainerBattle: Boolean, kind: HgssBattleKind,
             party: List<HgssExpMon>, share: Boolean): List<HgssExpAward>? {
        if (kind != HgssBattleKind.SINGLE && kind != HgssBattleKind.DOUBLE) return null
        if (baseYield !in 0..255 || enemyLevel !in 1..100 || party.size !in 1..6) return null
        if (party.map { it.slot }.toSet() != (0 until party.size).toSet() || party.any {
                it.slot !in 0..5 || it.hp !in 0..65535 || it.level !in 1..100 ||
                    ((!it.present || it.egg) && it.participated)
            }) return null
        val eligible = party.filter { it.present && !it.egg && it.hp > 0 }
        // The retail denominator includes living level-100 participants and held-item recipients.
        val participants = eligible.count { it.participated }
        val holders = eligible.count { it.heldEffect == HgssHeldExpEffect.SHARE }
        if (participants == 0) return null // Never divide by zero or invent a participant.
        val base = baseYield.toLong() * enemyLevel / 7
        val participantBase = ((if (holders > 0) base / 2 else base) / participants).coerceAtLeast(1)
        val heldBase = if (holders > 0) (base / 2 / holders).coerceAtLeast(1) else 0
        return party.map { mon ->
            if (mon !in eligible || mon.level == 100) HgssExpAward(mon.slot, 0, 0)
            else {
                val nativeBase = (if (mon.participated) participantBase else 0) +
                    (if (mon.heldEffect == HgssHeldExpEffect.SHARE) heldBase else 0)
                val vanilla = boosted(nativeBase, mon, trainerBattle)
                // Approved policy: half the individually boosted, unsplit award, with floor rounding.
                // Participants keep vanilla; a held Share never stacks two awards for the same knockout.
                val selected = if (!share || mon.participated) vanilla
                    else maxOf(vanilla, boosted(base, mon, trainerBattle) / 2)
                HgssExpAward(mon.slot, vanilla, selected)
            }
        }
    }

    private fun boosted(base: Long, mon: HgssExpMon, trainerBattle: Boolean): Long {
        var amount = base
        if (mon.heldEffect == HgssHeldExpEffect.BOOST) amount = amount * 150 / 100
        if (trainerBattle) amount = amount * 150 / 100
        amount = when (mon.trainer) {
            HgssTrainerRelation.ORIGINAL -> amount
            HgssTrainerRelation.TRADED_SAME_LANGUAGE -> amount * 150 / 100
            HgssTrainerRelation.TRADED_OTHER_LANGUAGE -> amount * 170 / 100
        }
        return amount
    }
}
