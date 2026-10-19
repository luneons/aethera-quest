package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Test

class EntityKeyTest {
    private fun monster(name: String, cell: String, mapId: Int) =
        EntityRow(cell = cell, kind = "Monster", name = name, hp = 100, maxHp = 100, mapId = mapId)

    @Test fun distinctMonstersKeepDistinctKeys() {
        val rows = listOf(monster("Water Draconian", "Enter", 3), monster("Undead Paladin", "Enter", 21))
        val keys = entityKeys(rows)
        assertEquals(2, keys.toSet().size)
    }

    @Test fun twoMonstersWithTheSameNameInTheSameCellStillGetUniqueKeys() {
        // This is the live crash: LazyColumn rejects the duplicate key
        // "Monster-Water Draconian-Enter" as soon as the row scrolls into view.
        val rows = listOf(
            monster("Water Draconian", "Enter", 3),
            monster("Water Draconian", "Enter", 4),
            monster("Water Draconian", "Enter", 5),
        )
        val keys = entityKeys(rows)
        assertEquals(3, keys.toSet().size)
        assertEquals(rows.size, keys.size)
    }

    @Test fun keysStayStableForTheSameInput() {
        val rows = listOf(monster("Water Draconian", "Enter", 3), monster("Water Draconian", "Enter", 4))
        assertEquals(entityKeys(rows), entityKeys(rows))
    }

    @Test fun everyRowGetsAKey() {
        val rows = listOf(
            EntityRow("Enter", "Self", "mel e", 1602, 1602, null),
            EntityRow("Enter", "Player", "stormslayer14", 405, 405, null),
            monster("Water Draconian", "Enter", 3),
        )
        assertEquals(3, entityKeys(rows).toSet().size)
    }
}