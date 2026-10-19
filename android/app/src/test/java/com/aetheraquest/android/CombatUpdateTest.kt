package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server reports combat results in `ct` packets, keyed by MonMapID:
 *
 *   {"cmd":"ct","m":{"21":{"intState":2,"intHP":12967}}}
 *
 * Ignoring that command leaves every monster frozen at the HP it had when the
 * map loaded, which is why attacking looked like it did nothing.
 */
class CombatUpdateTest {

    private fun reducerWithMonster(hp: Int = 13000): AreaReducer {
        val reducer = AreaReducer("demo-user")
        reducer.feed(
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"moveToArea","areaId":106071,
            "areaName":"doomwood-1","strMapName":"doomwood",
            "monmap":[{"MonMapID":21,"MonID":609,"strFrame":"r10"}],
            "mondef":[{"MonID":609,"strMonName":"Undead Paladin","intHP":$hp,"intHPMax":$hp}],
            "monBranch":[{"MonMapID":21,"MonID":609,"intState":1,"intHP":$hp}],
            "uoBranch":[]}}}""",
        )
        return reducer
    }

    @Test
    fun aCombatPacketUpdatesTheMonsterHp() {
        val reducer = reducerWithMonster()
        assertEquals(13000, reducer.snapshot().monsters[21]?.hp)

        val changed = reducer.feed(
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","m":{"21":{"intState":2,"intHP":12967}}}}}""",
        )

        assertTrue(changed)
        assertEquals(12967, reducer.snapshot().monsters[21]?.hp)
        assertTrue(reducer.snapshot().monsters[21]?.alive == true)
    }

    @Test
    fun theMonsterStaysVisibleWhileItHasHp() {
        val reducer = reducerWithMonster()
        reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","m":{"21":{"intHP":500}}}}}""")
        assertEquals(1, reducer.snapshot().monsters.size)
        assertTrue(reducer.snapshot().monsters.getValue(21).alive)
    }

    @Test
    fun aMonsterAtZeroHpIsNoLongerAlive() {
        val reducer = reducerWithMonster()
        reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","m":{"21":{"intState":0,"intHP":0}}}}}""")

        val monster = reducer.snapshot().monsters.getValue(21)
        assertEquals(0, monster.hp)
        assertFalse(monster.alive)
        // It stays in the snapshot so respawnMon can revive the same MonMapID.
        assertEquals(1, reducer.snapshot().monsters.size)
    }

    @Test
    fun respawnBringsTheSameMonsterBackAtFullHp() {
        val reducer = reducerWithMonster()
        reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","m":{"21":{"intHP":0}}}}}""")
        assertFalse(reducer.snapshot().monsters.getValue(21).alive)

        reducer.feed("%xt%zm%respawnMon%106071%21%")

        val revived = reducer.snapshot().monsters.getValue(21)
        assertTrue(revived.alive)
        assertEquals(13000, revived.hp)
    }

    @Test
    fun combatPacketsAlsoUpdateOurOwnHp() {
        val reducer = AreaReducer("demo-user")
        reducer.feed(
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"moveToArea","areaId":1,"areaName":"x-1",
            "uoBranch":[{"uoName":"demo-user","strUsername":"Demo User","entID":7,
            "strFrame":"Enter","strPad":"Spawn","intHP":1602,"intHPMax":1602}]}}}""",
        )
        reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","p":{"demo-user":{"intHP":1500}}}}}""")
        assertEquals(1500, reducer.snapshot().self?.hp)
    }

    @Test
    fun anUnknownMonsterIdIsIgnoredRatherThanInvented() {
        val reducer = reducerWithMonster()
        reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","m":{"99":{"intHP":5}}}}}""")
        assertEquals(setOf(21), reducer.snapshot().monsters.keys)
    }
}
