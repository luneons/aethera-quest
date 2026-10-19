package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cell changes are explicit: without the map SWF's trigger geometry the
 * neighbouring cell cannot be derived from the stick direction, so the UI offers
 * the cells the server has actually revealed.
 */
class CellTriggerTest {

    private fun area(selfCell: String?, vararg cells: String): AreaSnapshot {
        val monsters = cells.mapIndexed { index, cell ->
            (index + 1) to MonsterState(mapId = index + 1, cell = cell)
        }.toMap()
        return AreaSnapshot(
            self = selfCell?.let { PlayerState("demo-user", cell = it) },
            monsters = monsters,
        )
    }

    @Test
    fun knownCellsComeFromTheLiveAreaSnapshot() {
        val snapshot = AreaSnapshot(
            self = PlayerState("demo-user", cell = "r2"),
            monsters = mapOf(
                1 to MonsterState(mapId = 1, cell = "r3"),
                2 to MonsterState(mapId = 2, cell = "r3"),
            ),
            players = mapOf("other" to PlayerState("other", cell = "Enter")),
        )
        assertEquals(listOf("Enter", "r2", "r3"), knownCells(snapshot))
    }

    @Test
    fun theCurrentCellIsNotOfferedAsAJumpTarget() {
        assertEquals(listOf("Enter", "r3"), jumpableCells(area("r2", "r2", "r3", "Enter")))
    }

    @Test
    fun noSelfCellMeansEveryKnownCellIsOffered() {
        assertEquals(listOf("Enter", "r3"), jumpableCells(area(null, "r3", "Enter")))
    }

    @Test
    fun aSingleCellMapOffersNoTargets() {
        assertEquals(emptyList<String>(), jumpableCells(area("r2", "r2")))
    }

    @Test
    fun blankCellsAreIgnored() {
        val snapshot = AreaSnapshot(
            self = PlayerState("demo-user", cell = "r2"),
            monsters = mapOf(1 to MonsterState(mapId = 1, cell = "  ")),
        )
        assertEquals(listOf("r2"), knownCells(snapshot))
    }
}
