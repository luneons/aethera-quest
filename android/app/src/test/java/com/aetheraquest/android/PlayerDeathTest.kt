package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Death detection.
 *
 * The server has exactly one death signal: the `playerDeath` command naming the
 * victim by user id. Everything else in `ct` is combat chatter, and one shape is
 * actively misleading — a player entry with an empty object means the hit did
 * nothing:
 *
 *   {"cmd":"ct","p":{"mel e":{}},"sara":[{"actionResult":{"type":"dodge","hp":0}}]}
 *
 * Reading that as a death respawns a healthy character every time it dodges,
 * which is exactly the dead/alive flicker that showed up during combat.
 */
class PlayerDeathTest {
    private fun area(
        hp: Int? = 1600,
        maxHp: Int? = 1600,
        state: Int? = 1,
        mp: Int? = 100,
        maxMp: Int? = 100,
        dead: Boolean = false,
    ) = AreaSnapshot(
        self = PlayerState(
            username = "demo-user",
            userId = 46841,
            hp = hp,
            maxHp = maxHp,
            state = state,
            mp = mp,
            maxMp = maxMp,
            dead = dead,
        ),
    )

    @Test fun `death latch records the moment of death`() {
        val next = nextRespawnState(RespawnState(), area(hp = 0, dead = true), nowMs = 1_000)
        assertEquals(1_000L, next.deathAtMs)
        assertFalse(next.shouldSend)
    }

    @Test fun `full hp is alive even while the combat state is set`() {
        // intState is 2 during combat; it must never be read as "dead".
        val state = nextRespawnState(
            RespawnState(deathAtMs = 1_000, attempts = 1),
            area(hp = 1600, state = 2),
            nowMs = 99_000,
        )
        assertEquals(RespawnState(seenAlive = true), state)
        assertNull(state.deathAtMs)
    }

    @Test fun `death starts the ten second countdown`() {
        val dead = nextRespawnState(RespawnState(), area(hp = 0, dead = true), 1_000)
        val early = nextRespawnState(dead, area(hp = 0, dead = true), 10_999)
        assertFalse(early.shouldSend)
        val due = nextRespawnState(dead, area(hp = 0, dead = true), 11_000)
        assertTrue(due.shouldSend)
        assertEquals(46841, due.userId)
    }

    @Test fun `a respawn is retried while the avatar stays dead`() {
        val dead = RespawnState(deathAtMs = 0, seenAlive = true)
        val first = nextRespawnState(dead, area(hp = 0, state = 0, dead = true), 10_000)
        assertTrue(first.shouldSend)

        val afterOne = first.copy(shouldSend = false, attempts = 1)
        val tooSoon = nextRespawnState(afterOne, area(hp = 0, state = 0, dead = true), 11_000)
        assertFalse(tooSoon.shouldSend)
        val again = nextRespawnState(afterOne, area(hp = 0, state = 0, dead = true), 14_000)
        assertTrue(again.shouldSend)
    }

    @Test fun `retries stop at the attempt cap`() {
        val capped = RespawnState(deathAtMs = 0, seenAlive = true, attempts = RESPAWN_MAX_ATTEMPTS)
        val state = nextRespawnState(capped, area(hp = 0, state = 0, dead = true), 999_999)
        assertFalse(state.shouldSend)
    }

    @Test fun `coming back alive clears the death latch`() {
        val cleared = nextRespawnState(
            RespawnState(deathAtMs = 1_000, attempts = 2, seenAlive = true),
            area(hp = 1600, state = 1, dead = false),
            nowMs = 20_000,
        )
        assertEquals(RespawnState(seenAlive = true), cleared)
        assertNull(cleared.deathAtMs)
    }

    private fun reducer(): AreaReducer {
        val reducer = AreaReducer("demo-user", selfUserId = 46841)
        reducer.feed(
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"moveToArea","areaId":106071,
            "areaName":"doomwood-1","monBranch":[],
            "uoBranch":[{"uoName":"demo-user","entID":46841,"strUsername":"Demo User",
            "strFrame":"Enter","strPad":"Spawn","intState":1,
            "intHP":1600,"intHPMax":1600,"intMP":100,"intMPMax":100}]}}}""",
        )
        return reducer
    }

    @Test fun `reducer reads mana and id from the snapshot`() {
        val self = reducer().snapshot().self
        assertEquals(100, self?.mp)
        assertEquals(100, self?.maxMp)
        // `uoBranch` spells the id `entID`; without it respawn has no target.
        assertEquals(46841, self?.userId)
    }

    @Test fun `reducer treats an empty player object as a dodge, not a death`() {
        val reducer = reducer()
        val changed = reducer.feed(
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","p":{"demo-user":{}},
            "sara":[{"iRes":1,"actionResult":{"type":"dodge","hp":0,
            "cInf":"m:21","tInf":"p:46841"}}]}}}""",
        )
        assertFalse(changed)
        val self = reducer.snapshot().self
        assertEquals(false, self?.dead)
        assertEquals(1600, self?.hp)
    }

    @Test fun `repeated dodges never mark the player dead`() {
        val reducer = reducer()
        repeat(5) {
            reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","p":{"demo-user":{}}}}}""")
        }
        assertEquals(false, reducer.snapshot().self?.dead)
        assertEquals(1600, reducer.snapshot().self?.hp)
    }

    @Test fun `reducer handles playerDeath for self`() {
        val reducer = reducer()
        val changed = reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"playerDeath","userID":46841}}}""")
        assertTrue(changed)
        assertEquals(true, reducer.snapshot().self?.dead)
    }

    @Test fun `playerDeath for someone else is ignored`() {
        val reducer = reducer()
        val changed = reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"playerDeath","userID":999}}}""")
        assertFalse(changed)
        assertEquals(false, reducer.snapshot().self?.dead)
    }

    @Test fun `a live hp update clears the dead flag`() {
        val reducer = reducer()
        reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"playerDeath","userID":46841}}}""")
        assertEquals(true, reducer.snapshot().self?.dead)

        reducer.feed(
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"uotls","unm":"demo-user",
            "o":{"intHP":1600,"intMP":100}}}}""",
        )
        val self = reducer.snapshot().self
        assertEquals(false, self?.dead)
        assertEquals(1600, self?.hp)
    }
}
