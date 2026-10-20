package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focus from Scroll of Enrage lasts ~6s and the scroll has a ~10s cooldown, so a
 * single character cannot hold it. The rotation exists to hand over before the
 * buff drops; these tests pin that hand-over contract.
 */
class TauntLoopTest {
    private fun taunter(id: Int, role: CompanionRole) =
        AuxiliaryAccount(id = id, username = "demo-$id", role = role, enabled = true, consumableItemId = 99001)

    private val a = taunter(1, CompanionRole.TAUNTER_A)
    private val b = taunter(2, CompanionRole.TAUNTER_B)

    @Test fun `taunter a goes first`() {
        val decision = nextTaunt(TauntRotation(), listOf(a, b), nowMs = 1_000)
        assertEquals(1, decision?.accountId)
        assertEquals(1, decision?.nextIndex)
    }

    @Test fun `rotation waits until focus is nearly gone`() {
        var rotation = recordTaunt(TauntRotation(), TauntDecision(1, 1), nowMs = 1_000)
        // 3s later Focus is still healthy; a second cast would waste its own buff.
        assertNull(nextTaunt(rotation, listOf(a, b), nowMs = 4_000))
        // Just before expiry the next taunter must take over.
        val handover = nextTaunt(rotation, listOf(a, b), nowMs = 1_000 + TauntTiming().refreshAtMs)
        assertEquals(2, handover?.accountId)
    }

    @Test fun `the same taunter is not reused before its scroll cooldown`() {
        var rotation = recordTaunt(TauntRotation(), TauntDecision(1, 1), nowMs = 1_000)
        rotation = recordTaunt(rotation, TauntDecision(2, 0), nowMs = 5_000)
        // A cast at 1s and B at 5s: neither scroll is ready at 10s, so the loop
        // must wait rather than spam a cooldown-locked consumable.
        assertNull(nextTaunt(rotation, listOf(a, b), nowMs = 10_000))
        // A's own 10s cooldown expires at 11s and it takes the next turn.
        assertEquals(1, nextTaunt(rotation, listOf(a, b), nowMs = 11_000)?.accountId)
    }

    @Test fun `a taunter on cooldown is skipped instead of stalling the loop`() {
        val onlyA = listOf(a)
        val rotation = recordTaunt(TauntRotation(), TauntDecision(1, 0), nowMs = 1_000)
        assertNull(nextTaunt(rotation, onlyA, nowMs = 5_000))
        assertEquals(1, nextTaunt(rotation, onlyA, nowMs = 11_000)?.accountId)
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
