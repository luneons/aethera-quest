package com.aetheraquest.android

/** The full logo/header is useful for sign-in but wastes map viewport space. */
internal fun showBrandHeader(screen: Screen): Boolean = screen == Screen.LOGIN

/** Result of tapping an entity card. */
internal sealed interface EntityTapAction {
    data class Select(val mapId: Int?) : EntityTapAction
    data class Jump(val cell: String) : EntityTapAction
}

/**
 * Single tap selects a monster. Double tap jumps only for monsters whose cell
 * the server has revealed; players and self rows retain normal selection.
 */
internal fun entityTapAction(entity: EntityRow, doubleTap: Boolean): EntityTapAction =
    if (doubleTap && entity.kind == "Monster" && entity.cell.isNotBlank() && entity.cell != "Unknown") {
        EntityTapAction.Jump(entity.cell)
    } else {
        EntityTapAction.Select(entity.mapId)
    }

/** Compact map identity displayed with location info, not beside combat buttons. */
internal fun mapLocationLabel(mapName: String?, roomId: Int?): String =
    "${mapName?.takeIf { it.isNotBlank() } ?: "Unknown map"} · Room ${roomId ?: "–"}"
