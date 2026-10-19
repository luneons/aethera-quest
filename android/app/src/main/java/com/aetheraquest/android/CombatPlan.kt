package com.aetheraquest.android

/**
 * A combat action decided from the live area snapshot, so the client never
 * fabricates targets or distances.
 */
data class CombatPlan(
    val targetMapId: Int,
    /** True when the player must (re)join the monster's cell before attacking. */
    val moveFirst: Boolean,
    val cell: String,
    val pad: String,
)

/**
 * Plan combat against [selected] (or the first alive monster when none is
 * selected). Attacking across cells is meaningless in AQW — the live SWF
 * jumps to the target cell first — so the plan carries a `moveFirst` step
 * whenever self is not already in the monster's cell.
 *
 * The pad is derived from the monster's own pad when the snapshot carries one
 * and otherwise falls back to the client default ("Left").
 */
internal fun combatPlan(area: AreaSnapshot, selected: Int?, unlimitedRange: Boolean = false): CombatPlan? {
    val self = area.self ?: return null
    val target = resolveAttackTarget(area, selected) ?: return null
    val monster = area.monsters[target] ?: return null
    val cell = monster.cell ?: "Enter"
    val pad = self.pad?.takeIf { self.cell == cell } ?: "Left"
    val sameCell = self.cell != null && self.cell.equals(cell, ignoreCase = true)
    return CombatPlan(
        targetMapId = target,
        // The server resolves combat by MonMapID and does not validate distance,
        // so with unlimited range the client fires from wherever it stands
        // instead of walking into the monster's cell first.
        moveFirst = !sameCell && !unlimitedRange,
        cell = cell,
        pad = pad,
    )
}