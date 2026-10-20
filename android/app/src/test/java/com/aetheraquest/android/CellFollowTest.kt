package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellFollowTest {
    @Test fun `companion follows primary cell and pad`() {
        assertEquals(
            CellFollowPlan("r2", "Left"),
            followCellPlan("r2", "Left", "Enter"),
        )
    }

    @Test fun `already aligned companion does not resend movement`() {
        assertNull(followCellPlan("r2", "Left", "r2"))
    }

    @Test fun `blank primary cell produces no movement plan`() {
        assertNull(followCellPlan(null, "Spawn", "Enter"))
        assertNull(followCellPlan("", "Spawn", "Enter"))
    }

    @Test fun `primary may taunt only inside target monster cell`() {
        val monster = MonsterState(mapId = 21, cell = "r2", hp = 500, maxHp = 1_000, state = 1)
        val area = AreaSnapshot(
            self = PlayerState("demo", cell = "r2", pad = "Left", hp = 1_000, maxHp = 1_000),
            monsters = mapOf(21 to monster),
        )
        assertTrue(primaryReadyToTaunt(area, 21))
    }

    @Test fun `primary outside target cell blocks taunt`() {
        val monster = MonsterState(mapId = 21, cell = "r2", hp = 500, maxHp = 1_000, state = 1)
        val area = AreaSnapshot(
            self = PlayerState("demo", cell = "Enter", hp = 1_000, maxHp = 1_000),
            monsters = mapOf(21 to monster),
        )
        assertFalse(primaryReadyToTaunt(area, 21))
    }

    @Test fun `dead or cellless targets block taunt`() {
        val dead = MonsterState(mapId = 21, cell = "r2", hp = 0, maxHp = 1_000, state = 0)
        val noCell = MonsterState(mapId = 22, cell = null, hp = 500, maxHp = 1_000, state = 1)
        val area = AreaSnapshot(
            self = PlayerState("demo", cell = "r2", hp = 1_000, maxHp = 1_000),
            monsters = mapOf(21 to dead, 22 to noCell),
        )
        assertFalse(primaryReadyToTaunt(area, 21))
        assertFalse(primaryReadyToTaunt(area, 22))
    }

    @Test fun `explicit target is preferred when alive`() {
        val first = MonsterState(mapId = 21, cell = "r2", hp = 500, maxHp = 1_000, state = 1)
        val selected = MonsterState(mapId = 22, cell = "r2", hp = 500, maxHp = 1_000, state = 1)
        val area = AreaSnapshot(monsters = mapOf(21 to first, 22 to selected))
        assertEquals(22, tauntTarget(area, 22))
        assertEquals(21, tauntTarget(area, 999))
    }
}
