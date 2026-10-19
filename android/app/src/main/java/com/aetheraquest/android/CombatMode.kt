package com.aetheraquest.android

/** Result of pressing one manual combat button. */
internal sealed interface ManualCombatAction {
    data class Cast(val mapId: Int, val ref: String) : ManualCombatAction
}

/**
 * Manual combat buttons are numbered a0..a5 in the UI because that is what a
 * player counts, but the wire refs are AQW's own: a0 swings (`aa`), a1..a4 are
 * class skills, and a5 is the consumable/potion slot (`i1`).
 */
internal fun manualSkillRef(index: Int): String? = when (index) {
    0 -> "aa"
    in 1..4 -> "a$index"
    5 -> "i1"
    else -> null
}

/** A5 is only meaningful once the server reports a potion action. */
internal fun manualSkillEnabled(index: Int, skills: Map<String, SkillInfo>): Boolean {
    val ref = manualSkillRef(index) ?: return false
    if (ref == "i1") return skills.containsKey("i1")
    // Unknown class tables still allow the basic swing.
    return skills.isEmpty() || skills.containsKey(ref) || ref == "aa"
}

/**
 * Resolves a manual press against the live snapshot. A press without a living
 * monster must not fabricate a target, so it yields no action at all.
 */
internal fun manualCombatAction(area: AreaSnapshot, selected: Int?, index: Int): ManualCombatAction? {
    val ref = manualSkillRef(index) ?: return null
    val target = resolveAttackTarget(area, selected) ?: return null
    return ManualCombatAction.Cast(target, ref)
}

/** Combat mode is a full-screen control layout entered and left by the user. */
internal fun combatModeAfterOpen(current: Boolean): Boolean = true
internal fun combatModeAfterBack(current: Boolean): Boolean = false
