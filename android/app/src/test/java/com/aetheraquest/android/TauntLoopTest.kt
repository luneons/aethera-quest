package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live scroll reports `cd: 20000` on cast and its Focus runs for 10 s, so one
 * character can never hold Focus alone and two must alternate.
 *
 * These tests pin the hand-over contract, including the fact that the gap between
 * two taunts is nearly 9 s: casting sooner wastes the previous Focus instead of
 * extending it, which is the mistake the old 4.8 s timing made.
 */
class TauntLoopTest {
    private fun taunter(id: Int, role: CompanionRole) =
        AuxiliaryAccount(id = id, username = "demo-$id", role = role, enabled = true, consumableItemId = 99001)

    private val a = taunter(1, CompanionRole.TAUNTER_A)
    private val b = taunter(2, CompanionRole.TAUNTER_B)

    @Test fun `scroll numbers come from the live packet not a guess`() {
        assertEquals(20_000L, TauntTiming().scrollCooldownMs)
        assertEquals(10_000L, TauntTiming().focusDurationMs)
        assertEquals(7_800L, TauntTiming().refreshAtMs)
    }

    @Test fun `taunter a goes first`() {
        val decision = nextTaunt(TauntRotation(), listOf(a, b), nowMs = 1_000)
        assertEquals(1, decision?.accountId)
        assertEquals(1, decision?.nextIndex)
    }

    @Test fun `rotation waits until focus is nearly gone`() {
        val rotation = recordTaunt(TauntRotation(), TauntDecision(1, 1), nowMs = 1_000)
        // 4s later Focus is still healthy; the old timing handed over here and
        // threw away more than half of the first scroll's effect.
        assertNull(nextTaunt(rotation, listOf(a, b), nowMs = 5_000))
        // The hand-over is 7.8s after the previous cast: quick enough that Focus
        // never lapses, late enough that the previous scroll is not wasted.
        assertNull(nextTaunt(rotation, listOf(a, b), nowMs = 8_000))
        val handover = nextTaunt(rotation, listOf(a, b), nowMs = 1_000 + TauntTiming().refreshAtMs)
        assertEquals(2, handover?.accountId)
    }

    @Test fun `a taunter is never reused before its own scroll cooldown`() {
        var rotation = recordTaunt(TauntRotation(), TauntDecision(1, 1), nowMs = 1_000)
        rotation = recordTaunt(rotation, TauntDecision(2, 0), nowMs = 9_500)
        // B's Focus expires at 19.5s but A's scroll is not ready until 21s, so the
        // loop must wait instead of spamming a cooldown-locked consumable.
        assertNull(nextTaunt(rotation, listOf(a, b), nowMs = 18_000))
        assertEquals(1, nextTaunt(rotation, listOf(a, b), nowMs = 21_000)?.accountId)
    }

    @Test fun `a taunter on cooldown is skipped instead of stalling the loop`() {
        val onlyA = listOf(a)
        val rotation = recordTaunt(TauntRotation(), TauntDecision(1, 0), nowMs = 1_000)
        assertNull(nextTaunt(rotation, onlyA, nowMs = 5_000))
        assertNull(nextTaunt(rotation, onlyA, nowMs = 12_000))
        assertEquals(1, nextTaunt(rotation, onlyA, nowMs = 21_000)?.accountId)
    }

    @Test fun `attacks pause just before a taunt is due`() {
        val rotation = recordTaunt(TauntRotation(), TauntDecision(1, 1), nowMs = 1_000)
        // Far from the hand-over: companions are free to attack.
        assertFalse(shouldHoldAttacks(rotation, nowMs = 3_000))
        // 7s in, the hand-over is 0.8s away — already inside the 2s reserve.
        assertTrue(shouldHoldAttacks(rotation, nowMs = 7_000))
        // Still outside the reserve earlier in the rotation.
        assertFalse(shouldHoldAttacks(rotation, nowMs = 5_000))
        assertNull(tauntDueIn(TauntRotation(), 7_000, TauntTiming()))
    }

    @Test fun `disabled and non taunt roles never taunt`() {
        val disabled = a.copy(enabled = false)
        val support = AuxiliaryAccount(id = 3, username = "demo-3", role = CompanionRole.SUPPORT, enabled = true)
        val dps = AuxiliaryAccount(id = 4, username = "demo-4", role = CompanionRole.DPS, enabled = true)
        assertNull(nextTaunt(TauntRotation(), listOf(disabled, support, dps), nowMs = 1_000))
        assertTrue(tauntOrder(listOf(disabled, support, dps)).isEmpty())
    }

    @Test fun `taunt stops when no monster is alive`() {
        val alive = MonsterState(mapId = 21, hp = 500, maxHp = 500, state = 1)
        val dead = MonsterState(mapId = 22, hp = 0, maxHp = 500, state = 0)
        assertTrue(shouldKeepTaunting(AreaSnapshot(monsters = mapOf(21 to alive)), primaryInMap = true))
        assertFalse(shouldKeepTaunting(AreaSnapshot(monsters = mapOf(22 to dead)), primaryInMap = true))
        assertFalse(shouldKeepTaunting(AreaSnapshot(monsters = emptyMap()), primaryInMap = true))
    }

    @Test fun `taunt stops when the primary leaves the map`() {
        val alive = MonsterState(mapId = 21, hp = 500, maxHp = 500, state = 1)
        assertFalse(shouldKeepTaunting(AreaSnapshot(monsters = mapOf(21 to alive)), primaryInMap = false))
    }

    @Test fun `three taunters rotate in order without repeating`() {
        val c = AuxiliaryAccount(id = 3, username = "demo-3", role = CompanionRole.TAUNTER_B, enabled = true)
        val order = tauntOrder(listOf(c, b, a))
        assertEquals(listOf(1, 2, 3), order.map { it.id })
    }
}
