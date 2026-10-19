package com.aetheraquest.android

/**
 * One row in the entity list (self, other players, or monsters) shown in the
 * left column of the map screen.
 *
 * A row's identity cannot be its display name: a single cell can hold several
 * monsters with the same name (e.g. three "Water Draconian" in one room). The
 * area snapshot keys monsters by `MonMapID`, which is unique per spawn, so that
 * value carries identity for monsters while a player's username does for players.
 */
data class EntityRow(
    val cell: String,
    val kind: String,
    val name: String,
    val hp: Int?,
    val maxHp: Int?,
    val mapId: Int?,
)

/**
 * Stable, unique list keys for [rows], in order.
 *
 * Duplicate keys make `LazyColumn` throw
 * `IllegalArgumentException: Key ... was already used`, which only surfaces when
 * the offending row scrolls into the viewport — so a key must never be built
 * from the display name alone, and identical rows must still get distinct keys.
 */
internal fun entityKeys(rows: List<EntityRow>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return rows.map { row ->
        val base = "${row.kind}|${row.mapId ?: row.name}|${row.cell}"
        val count = seen.getOrElse(base) { 0 }
        seen[base] = count + 1
        if (count == 0) base else "$base#$count"
    }
}

/** Which kinds of entity the list should show. */
enum class EntityFilter { ALL, MONSTERS, PLAYERS }

/**
 * Applies [filter] to [rows]. `Self` counts as a player: the point of the filter
 * is to separate who is a character from what is a monster, and hiding yourself
 * would leave the "players" view without a reference point.
 */
internal fun filterEntityRows(rows: List<EntityRow>, filter: EntityFilter): List<EntityRow> = when (filter) {
    EntityFilter.ALL -> rows
    EntityFilter.MONSTERS -> rows.filter { it.kind == "Monster" }
    EntityFilter.PLAYERS -> rows.filter { it.kind == "Player" || it.kind == "Self" }
}

/**
 * Explains an empty filtered list, or null when there is nothing worth saying.
 *
 * A filter that silently shows an empty screen looks broken, so an empty bucket
 * is named: no monsters in the map versus no players in the room are different
 * facts and the player should not have to guess which one they are looking at.
 */
internal fun nextFilterLabel(
    filter: EntityFilter,
    hasPlayers: Boolean,
    hasMonsters: Boolean,
): String? = when {
    filter == EntityFilter.MONSTERS && !hasMonsters -> "No monsters in this map"
    filter == EntityFilter.PLAYERS && !hasPlayers -> "No players here"
    else -> null
}
