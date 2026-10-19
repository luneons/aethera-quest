package com.aetheraquest.android

import kotlin.math.roundToInt

/** Fraction of a resource bar, clamped so a stale server value cannot overflow it. */
internal fun barFraction(value: Int?, max: Int?): Float {
    val top = max ?: return 0f
    if (top <= 0) return 0f
    val current = value ?: return 0f
    return (current.toFloat() / top.toFloat()).coerceIn(0f, 1f)
}

/**
 * One manual skill button's live state.
 *
 * `enabled` is false whenever the server would reject the cast, so a disabled
 * button is a truthful "you cannot press this now" rather than decoration.
 */
data class ManualSkillReadiness(
    val enabled: Boolean,
    val remainingMs: Long = 0L,
    val reason: String? = null,
)

/**
 * Computes whether a manual button can fire.
 *
 * Cooldown comes from the live `sAct` `cd` field for that exact ref, which is
 * the class's own cooldown — nothing is hardcoded. `readyAt` holds the last
 * local send time plus that cooldown, so the countdown shown on the button is
 * the same number the button obeys.
 */
internal fun manualSkillReadiness(
    index: Int,
    skills: Map<String, SkillInfo>,
    readyAt: Map<String, Long>,
    nowMs: Long,
    mana: Int?,
): ManualSkillReadiness {
    val ref = manualSkillRef(index) ?: return ManualSkillReadiness(false, reason = "Not available")
    val info = skills[ref]
    if (info == null) {
        // A0 is always legal; any other slot needs the server to describe it.
        return if (ref == "aa") ManualSkillReadiness(true) else ManualSkillReadiness(false, reason = "Not available")
    }
    if (!info.usable) return ManualSkillReadiness(false, reason = "Locked")
    if (info.manaCost > 0 && mana != null && mana < info.manaCost) {
        return ManualSkillReadiness(false, reason = "Not enough mana")
    }
    val remaining = ((readyAt[ref] ?: 0L) - nowMs).coerceAtLeast(0L)
    return if (remaining > 0) {
        ManualSkillReadiness(false, remainingMs = remaining, reason = "Cooling down")
    } else ManualSkillReadiness(true)
}

/** Short label for the cooldown overlay: `6s`, `3.2s`, or nothing. */
internal fun cooldownLabel(remainingMs: Long): String {
    if (remainingMs <= 0L) return ""
    return if (remainingMs >= 1_000L && remainingMs % 1_000L == 0L) {
        "${remainingMs / 1_000L}s"
    } else {
        val tenths = (remainingMs / 100.0).roundToInt() / 10.0
        if (tenths >= 1.0) "${tenths}s" else "${remainingMs}ms"
    }
}
