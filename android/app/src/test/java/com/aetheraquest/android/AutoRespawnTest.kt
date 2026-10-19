package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRespawnTest {
    private fun area(hp: Int?, maxHp: Int?, state: Int?, userId: Int? = 42) = AreaSnapshot(
        self = PlayerState(
            username = "demo-user",
            userId = userId,
            hp = hp,
            maxHp = maxHp,
            state = state,
        ),
    )

    @Test fun `authoritative zero hp starts death timer`() {
        val next = nextRespawnState(
            current = RespawnState(),
            area = area(hp = 0, maxHp = 1600, state = 0),
            nowMs = 1_000,
        )
        assertEquals(1_000L, next.deathAtMs)
        assertFalse(next.shouldSend)
    }

    @Test fun `respawn packet is sent after ten seconds`() {
        val next = nextRespawnState(
            current = RespawnState(deathAtMs = 1_000),
            area = area(hp = 0, maxHp = 1600, state = 0),
            nowMs = 11_000,
        )
        assertTrue(next.shouldSend)
        assertEquals(42, next.userId)
    }

    @Test fun `default zero without hp snapshot is not treated as death`() {
        val next = nextRespawnState(
            current = RespawnState(),
            area = area(hp = 0, maxHp = null, state = 0),
            nowMs = 11_000,
        )
        assertEquals(null, next.deathAtMs)
        assertFalse(next.shouldSend)
    }

    @Test fun `positive hp clears death latch`() {
        val next = nextRespawnState(
            current = RespawnState(deathAtMs = 1_000, sent = true),
            area = area(hp = 100, maxHp = 1600, state = 1),
            nowMs = 20_000,
        )
        assertEquals(RespawnState(seenAlive = true), next)
    }

    @Test fun `death is ignored until the character has been seen alive`() {
        val first = nextRespawnState(
            current = RespawnState(),
            area = area(hp = 0, maxHp = null, state = 0),
            nowMs = 1_000,
        )
        assertEquals(null, first.deathAtMs)
        assertFalse(first.shouldSend)
        // A later live sighting arms the latch, so the next death is caught.
        val armed = nextRespawnState(
            current = first,
            area = area(hp = 1200, maxHp = null, state = 1),
            nowMs = 2_000,
        )
        assertTrue(armed.seenAlive)
        val dead = nextRespawnState(
            current = armed,
            area = area(hp = 0, maxHp = null, state = 0),
            nowMs = 3_000,
        )
        assertEquals(3_000L, dead.deathAtMs)
    }

    @Test fun `dead player without uid waits instead of fabricating target`() {
        val next = nextRespawnState(
            current = RespawnState(deathAtMs = 1_000),
            area = area(hp = 0, maxHp = 1600, state = 0, userId = null),
            nowMs = 11_000,
        )
        assertFalse(next.shouldSend)
    }
}
