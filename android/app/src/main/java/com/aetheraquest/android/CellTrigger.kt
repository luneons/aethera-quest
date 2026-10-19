package com.aetheraquest.android

/**
 * AQW movement has two layers, and they are not interchangeable:
 *
 * - `mv` slides the sprite inside the *current* cell and never changes cell.
 * - `moveToCell` is the only packet that actually changes cell.
 *
 * A stick built on `mv` alone therefore pushes the sprite to the map border and
 * stops there, which is exactly the "the next cell never triggers" symptom.
 *
 * The real Flash client resolves the neighbouring cell from trigger zones stored
 * in the map SWF. That geometry is not available here, so deriving a destination
 * from the stick direction would send the player into an arbitrary cell. Cell
 * changes are therefore explicit: the UI lists the cells the server has actually
 * revealed and the player taps one.
 */

/**
 * Cells the live area snapshot proves exist: our own cell, every other player's
 * cell, and every monster's cell. AQW never sends the full cell list, so this is
 * the only server-observed set available without downloading the map SWF.
 */
internal fun knownCells(area: AreaSnapshot): List<String> {
    val found = LinkedHashSet<String>()
    area.self?.cell?.let { found.add(it) }
    area.players.values.forEach { player -> player.cell?.let { found.add(it) } }
    area.monsters.values.forEach { monster -> monster.cell?.let { found.add(it) } }
    return found.filter { it.isNotBlank() }.sortedWith(String.CASE_INSENSITIVE_ORDER)
}

/** Cells worth offering as jump targets: every known cell except the current one. */
internal fun jumpableCells(area: AreaSnapshot): List<String> {
    val current = area.self?.cell
    return knownCells(area).filter { !it.equals(current, ignoreCase = true) }
}
