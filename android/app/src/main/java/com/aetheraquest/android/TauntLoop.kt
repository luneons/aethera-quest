package com.aetheraquest.android

/**
 * Timing rules for a taunt rotation.
 *
 * Both numbers come from the live scroll, not from a guess: the `seia` reply for
 * Scroll of Enrage reports `cd: 20000`, and the item description says the target
 * "focuses attacks on you ... for 10 seconds".
 *
 * With a 20 s scroll cooldown and a 10 s Focus, a single character can never hold
 * it; two taunters have to alternate. Handing over right before expiry keeps the
 * debuff up while still letting each scroll do its full work.
 */
data class TauntTiming(
    val focusDurationMs: Long = 10_000L,
    val scrollCooldownMs: Long = 20_000L,
    /**
     * How early to refresh, chosen so the two-taunter rotation has the smallest
     * possible worst gap.
     *
     * With a 20 s cooldown and two scrolls, the gaps alternate as `r` and
     * `20 - r`. The worst gap is minimised when `r` is as large as possible, but
     * it can never drop below 10 s, so 8.5 s (worst gap 11.4 s) is the sweet spot:
     * going faster, as an earlier 7.8 s did, only widened the following gap to
     * 12.2 s and made the stutter more obvious.
     */
    val refreshLeadMs: Long = 1_500L,
    /**
     * Stop attacking this long before a taunt comes due. Extra attacks are
     * worthless if they push the scroll past the expiry and the party loses
     * Focus, so the taunt always wins the race.
     *
     * Kept short on purpose: this is dead time added to every rotation, and a
     * 2 s pause was itself visible as a stutter in the fight rhythm.
     */
    val attackReserveMs: Long = 900L,
) {
    /** The moment a new taunt must be applied, relative to the last one. */
    val refreshAtMs: Long get() = (focusDurationMs - refreshLeadMs).coerceAtLeast(0L)
}

/**
 * Milliseconds until the next taunt is due, or null when nobody is holding Focus
 * yet (in which case a taunt is due immediately).
 */
internal fun tauntDueIn(rotation: TauntRotation, nowMs: Long, timing: TauntTiming): Long? {
    val active = rotation.activeAccountId ?: return null
    val castAt = rotation.lastCastMs[active] ?: return null
    return (castAt + timing.refreshAtMs) - nowMs
}

/**
 * True while companions must hold their attacks so the upcoming taunt lands on
 * time. Without this an attack burst can delay the scroll past the Focus expiry.
 */
internal fun shouldHoldAttacks(
    rotation: TauntRotation,
    nowMs: Long,
    timing: TauntTiming = TauntTiming(),
): Boolean {
    val due = tauntDueIn(rotation, nowMs, timing) ?: return false
    return due <= timing.attackReserveMs
}

/** Which companion should taunt next, or null while the current one still holds. */
data class TauntDecision(
    val accountId: Int,
    val nextIndex: Int,
)

/**
 * How many taunters are needed to keep Focus up with no gap at all.
 *
 * A scroll is on a 20 s cooldown and its Focus lasts 10 s, so one character can
 * cover at most half the timeline. Two alternate 7.8 s and 12.1 s gaps — the
 * 12.1 s one is longer than Focus, which is exactly the stutter the player sees
 * as "loop, pause, loop". Three taunters cover 3 x 7.8 s = 23.4 s > 20 s and the
 * rotation becomes seamless.
 */
internal const val TAUNTERS_FOR_SEAMLESS_FOCUS = 3

/**
 * Why the rotation is not seamless, for the status line.
 *
 * This is information, not an error: two taunters still work, the debuff just
 * drops briefly between the second and first cast. Telling the player the reason
 * beats letting them think the loop is broken.
 */
internal fun focusGapWarning(taunterCount: Int): String? = when {
    taunterCount <= 0 -> "no taunter configured"
    taunterCount < TAUNTERS_FOR_SEAMLESS_FOCUS ->
        "$taunterCount taunter(s): Focus drops briefly, add one more for a seamless loop"
    else -> null
}

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
