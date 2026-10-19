package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Death detection.
 *
 * AQW reports a killing blow as an empty player object inside `ct`:
 *
 *   {"cmd":"ct","p":{"mel e":{}}}
 *
 * That packet carries no HP at all, so a client that waits for `intHP:0` never
 * notices the death and never respawns. `playerDeath` is the other signal, and
 * it names the victim by user id.
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

    @Test fun `empty player object marks self dead`() {
        val next = nextRespawnState(RespawnState(), area(hp = 0, state = 0, dead = true), nowMs = 1_000)
        assertEquals(1_000L, next.deathAtMs)
        assertFalse(next.shouldSend)
    }

    @Test fun `empty player object starts the ten second countdown`() {
        val dead = nextRespawnState(RespawnState(), area(hp = 0, state = 0, dead = true), 1_000)
        val early = nextRespawnState(dead, area(hp = 0, state = 0, dead = true), 10_999)
        assertFalse(early.shouldSend)
        val due = nextRespawnState(dead, area(hp = 0, state = 0, dead = true), 11_000)
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

    @Test fun `reducer reads mana from the snapshot`() {
        val self = reducer().snapshot().self
        assertEquals(100, self?.mp)
        assertEquals(100, self?.maxMp)
    }

    @Test fun `reducer treats an empty player object as death`() {
        val reducer = reducer()
        val changed = reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","p":{"demo-user":{}}}}}""")
        assertTrue(changed)
        val self = reducer.snapshot().self
        assertEquals(true, self?.dead)
        assertEquals(0, self?.hp)
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
        reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","p":{"demo-user":{}}}}}""")
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
