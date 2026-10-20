package com.aetheraquest.android

/**
 * Where a companion should stand, and whether taunting may start.
 *
 * A taunt only makes sense when the party is already standing in the monster's
 * cell: a taunter in another cell cannot be targeted by that monster, so casting
 * there just burns a scroll. The rule is therefore "same cell as the primary,
 * primary in the monster's cell", not "somewhere in the map".
 */
data class CellFollowPlan(
    val cell: String,
    val pad: String,
)

/**
 * The cell a companion should move to, or null when it is already there.
 *
 * Prefer the primary's own cell: the player picked that spot, and joining them is
 * exactly what "one cell with the primary" means. Only when the primary's cell is
 * unknown does it fall back to the target monster's cell.
 */
internal fun followCellPlan(
    primaryCell: String?,
    primaryPad: String?,
    companionCell: String?,
): CellFollowPlan? {
    val wanted = primaryCell?.trim().orEmpty()
    if (wanted.isEmpty()) return null
    if (wanted.equals(companionCell?.trim(), ignoreCase = true)) return null
    return CellFollowPlan(wanted, primaryPad?.trim().orEmpty().ifEmpty { "Spawn" })
}

/**
 * True only when the primary is standing in the same cell as a living target.
 *
 * This is the gate for starting a taunt: until the primary has walked into the
 * monster's cell, the rotation must stay idle instead of casting into empty air.
 */
internal fun primaryReadyToTaunt(area: AreaSnapshot, targetMapId: Int?): Boolean {
    val self = area.self ?: return false
    val primaryCell = self.cell?.trim().orEmpty()
    if (primaryCell.isEmpty()) return false
    val target = targetMapId?.let { area.monsters[it] } ?: return false
    if (!target.alive) return false
    val monsterCell = target.cell?.trim().orEmpty()
    if (monsterCell.isEmpty()) return false
    return monsterCell.equals(primaryCell, ignoreCase = true)
}

/** Picks the living monster the primary should focus, preferring an explicit pick. */
internal fun tauntTarget(area: AreaSnapshot, selected: Int?): Int? {
    selected?.let { id ->
        area.monsters[id]?.takeIf { it.alive }?.let { return it.mapId }
    }
    return area.monsters.values.firstOrNull { it.alive }?.mapId
}
