package com.pokedaisy.app.enhancements.hgss

import com.pokedaisy.app.companion.data.flySpotsHeartGold

/** HGSS numbering differs from other generations. Badge numbers are zero-based native bit indices. */
internal enum class HgssHm(val item: Int, val badge: Int) {
    CUT(420, 1), FLY(421, 4), SURF(422, 3), STRENGTH(423, 2),
    WHIRLPOOL(424, 6), ROCK_SMASH(425, 0), WATERFALL(426, 7), ROCK_CLIMB(427, 15),
}
/** Candidate policy, not an implicitly approved permanent unlock. No item-history storage is created. */
internal enum class HgssHmPossession { CURRENT_BAG }
internal enum class HgssFieldSource { WORLD, COMPANION }
internal data class HgssFieldRequest(val generation: Long, val action: HgssHm, val destination: Int? = null)
internal data class HgssFieldContext(
    val generation: Long,
    val checkedRequest: HgssFieldRequest,
    val enabled: Boolean,
    val idle: HgssFact,
    val items: Set<Int>?,
    val badges: Int?,
    val storyAllowed: HgssFact,
    /** Native FieldMove_Check*, with every map, avatar, follower, costume, tile and special-area condition intact. */
    val nativeCheck: HgssFact,
    val destinationUnlocked: HgssFact,
)
internal enum class HgssFieldResult {
    DISABLED, STALE, BUSY, POLICY_REQUIRED, UNKNOWN, NEED_ITEM, NEED_BADGE, STORY_BLOCKED,
    LOCATION_BLOCKED, DESTINATION_BLOCKED, STARTED, NATIVE_REFUSED,
}
internal interface HgssFieldPort {
    /** Core thread only; obtain fresh context for this exact action/destination immediately before invocation. */
    fun inspect(request: HgssFieldRequest): HgssFieldContext?
    /** Must atomically recheck the context generation and schedule the ordinary native action at most once. */
    fun invoke(request: HgssFieldRequest, context: HgssFieldContext): Boolean
}

/** Shared action gate for both paths. No production port or native-world hook is registered. */
internal class HgssFieldMoves(private val port: HgssFieldPort, private val possession: HgssHmPossession?) {
    fun use(source: HgssFieldSource, request: HgssFieldRequest): HgssFieldResult {
        invalid(request)?.let { return it } // Do not let a port index a native destination table first.
        // Both sources deliberately use the same eligibility and invocation path.
        return when (source) {
            HgssFieldSource.WORLD, HgssFieldSource.COMPANION -> {
                val context = port.inspect(request) ?: return HgssFieldResult.UNKNOWN
                val denied = blocked(request, context, possession)
                denied ?: if (port.invoke(request, context)) HgssFieldResult.STARTED else HgssFieldResult.NATIVE_REFUSED
            }
        }
    }

    companion object {
        private fun invalid(request: HgssFieldRequest): HgssFieldResult? {
            if (request.generation < 0) return HgssFieldResult.STALE
            if (request.action == HgssHm.FLY) {
                if (request.destination == null || request.destination !in 1..flySpotsHeartGold.size)
                    return HgssFieldResult.DESTINATION_BLOCKED
            } else if (request.destination != null) return HgssFieldResult.DESTINATION_BLOCKED
            return null
        }
        fun blocked(request: HgssFieldRequest, context: HgssFieldContext, possession: HgssHmPossession?): HgssFieldResult? {
            invalid(request)?.let { return it }
            if (!context.enabled) return HgssFieldResult.DISABLED
            if (request.generation != context.generation || request != context.checkedRequest) return HgssFieldResult.STALE
            when (possession) {
                null -> return HgssFieldResult.POLICY_REQUIRED
                HgssHmPossession.CURRENT_BAG -> Unit // Exhaustive: adding a policy requires handling it here.
            }
            if (context.idle == HgssFact.UNKNOWN) return HgssFieldResult.UNKNOWN
            if (context.idle != HgssFact.YES) return HgssFieldResult.BUSY
            val items = context.items ?: return HgssFieldResult.UNKNOWN
            val badges = context.badges ?: return HgssFieldResult.UNKNOWN
            if (badges !in 0..0xffff) return HgssFieldResult.UNKNOWN
            if (request.action.item !in items) return HgssFieldResult.NEED_ITEM
            if (badges and (1 shl request.action.badge) == 0) return HgssFieldResult.NEED_BADGE
            if (context.storyAllowed == HgssFact.UNKNOWN || context.nativeCheck == HgssFact.UNKNOWN) return HgssFieldResult.UNKNOWN
            if (context.storyAllowed != HgssFact.YES) return HgssFieldResult.STORY_BLOCKED
            if (context.nativeCheck != HgssFact.YES) return HgssFieldResult.LOCATION_BLOCKED
            if (request.action == HgssHm.FLY) {
                if (context.destinationUnlocked == HgssFact.UNKNOWN) return HgssFieldResult.UNKNOWN
                if (context.destinationUnlocked != HgssFact.YES) return HgssFieldResult.DESTINATION_BLOCKED
            }
            return null
        }
    }
}
