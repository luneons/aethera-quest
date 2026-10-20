package com.aetheraquest.android

/**
 * Timing rules for a taunt rotation.
 *
 * AQW's Focus from Scroll of Enrage lasts about 6 seconds, and the scroll itself
 * is on a 10 second cooldown. One character therefore cannot hold Focus alone:
 * the next taunt must land *before* the previous one expires, but not so early
 * that its own duration is wasted. Two characters alternating cover the gap.
 */
data class TauntTiming(
    val focusDurationMs: Long = 6_000L,
    val scrollCooldownMs: Long = 10_000L,
    /**
     * How early to refresh. Casting at the last millisecond risks a visible gap
     * when the packet is late, so the rotation starts this far before expiry.
     */
    val refreshLeadMs: Long = 1_200L,
) {
    /** The moment a new taunt must be applied, relative to the last one. */
    val refreshAtMs: Long get() = (focusDurationMs - refreshLeadMs).coerceAtLeast(0L)
}

/** Which companion should taunt next, or null while the current one still holds. */
data class TauntDecision(
    val accountId: Int,
    val nextIndex: Int,
)

/** Mutable-by-copy rotation state: who taunted last and when. */
data class TauntRotation(
    val lastCastMs: Map<Int, Long> = emptyMap(),
    val activeAccountId: Int? = null,
    val index: Int = 0,
)

/**
 * Picks the next taunter.
 *
 * Order of checks, each one able to answer "not yet":
 * 1. No configured taunter → nothing to do.
 * 2. Someone is holding Focus and it is not near expiry → wait.
 * 3. Walk the rotation from the cursor and take the first account whose own
 *    scroll cooldown has elapsed. A taunter that is still on cooldown is skipped
 *    instead of blocking the whole rotation, which is what makes a two-character
 *    rotation survive a dropped cast.
 *
 * Returns null when nobody can taunt right now; the caller simply ticks again.
 */
internal fun nextTaunt(
    rotation: TauntRotation,
    taunters: List<AuxiliaryAccount>,
    nowMs: Long,
    timing: TauntTiming = TauntTiming(),
): TauntDecision? {
    val order = tauntOrder(taunters)
    if (order.isEmpty()) return null

    val active = rotation.activeAccountId
    if (active != null) {
        val castAt = rotation.lastCastMs[active]
        if (castAt != null && nowMs - castAt < timing.refreshAtMs) return null
    }

    repeat(order.size) { step ->
        val slot = (rotation.index + step) % order.size
        val candidate = order[slot]
        val last = rotation.lastCastMs[candidate.id]
        val ready = last == null || nowMs - last >= timing.scrollCooldownMs
        if (ready) return TauntDecision(candidate.id, (slot + 1) % order.size)
    }
    return null
}

/** Applies a confirmed taunt so the next decision knows who holds Focus. */
internal fun recordTaunt(
    rotation: TauntRotation,
    decision: TauntDecision,
    nowMs: Long,
): TauntRotation = rotation.copy(
    lastCastMs = rotation.lastCastMs + (decision.accountId to nowMs),
    activeAccountId = decision.accountId,
    index = decision.nextIndex,
)

/**
 * The rotation must stop when there is nothing to hold: no living monster, or
 * the primary is no longer in a map. Continuing would burn scrolls on nothing.
 */
internal fun shouldKeepTaunting(area: AreaSnapshot, primaryInMap: Boolean): Boolean {
    if (!primaryInMap) return false
    return area.monsters.values.any { it.alive }
}
