package com.aetheraquest.android

import org.junit.Assert.*
import org.junit.Test

class AreaReducerTest {
    private val area = """{"t":"xt","b":{"r":42,"o":{"cmd":"moveToArea","areaId":42,"areaName":"lair-100000",
      "uoBranch":[{"uoName":"alice","uid":1,"strFrame":"Enter","strPad":"Spawn","intHP":900,"intHPMax":900,"intState":1}],
      "mondef":[{"MonID":100,"strMonName":"Water Draconian"}],
      "monmap":[{"MonMapID":1,"MonID":100,"strFrame":"Enter"}],
      "monBranch":[{"MonMapID":1,"MonID":100,"intHP":500,"intHPMax":500,"intState":1}]}}}"""

    @Test fun moveToAreaCreatesGenerationAndEntities() {
        val reducer = AreaReducer("alice", 1)
        assertTrue(reducer.feed(area))
        val snapshot = reducer.snapshot()
        assertEquals(1L, snapshot.generation)
        assertEquals("lair-100000", snapshot.mapName)
        assertEquals("Enter", snapshot.self?.cell)
        assertEquals("Water Draconian", snapshot.monsters[1]?.name)
    }

    @Test fun incrementalUpdatesRespawnAndGenerationGuard() {
        val reducer = AreaReducer("alice", 1)
        reducer.feed(area)
        assertTrue(reducer.feed("%xt%mtls%-1%1%intHP:0,intState:0%", 1))
        assertEquals(0, reducer.snapshot().monsters[1]?.hp)
        assertTrue(reducer.feed("%xt%respawnMon%-1%1%", 1))
        assertEquals(500, reducer.snapshot().monsters[1]?.hp)
        assertTrue(reducer.feed("%xt%uotls%-1%alice%strFrame:Boss,intHP:700%", 1))
        assertEquals("Boss", reducer.snapshot().self?.cell)
        assertFalse(reducer.feed("%xt%mtls%-1%1%intHP:1%", 0))
    }

    @Test fun newAreaDropsStaleEntities() {
        val reducer = AreaReducer("alice", 1)
        reducer.feed(area)
        reducer.feed("""{"t":"xt","b":{"r":77,"o":{"cmd":"moveToArea","areaId":77,"areaName":"yulgar-1","uoBranch":[],"mondef":[],"monmap":[],"monBranch":[]}}}""")
        assertEquals(2L, reducer.snapshot().generation)
        assertTrue(reducer.snapshot().monsters.isEmpty())
    }
}
