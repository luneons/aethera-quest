package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatPlanTest {
    private fun monster(mapId: Int, cell: String, hp: Int = 100) =
        MonsterState(mapId = mapId, monsterId = mapId + 100, name = "Monster $mapId", cell = cell, hp = hp, maxHp = 100, state = 1)

    private fun area(selfCell: String?, monsters: Map<Int, MonsterState>) = AreaSnapshot(
        self = PlayerState("mel e", x = 0, y = 0, cell = selfCell),
        monsters = monsters,
    )

    @Test fun noTargetYieldsNoPlan() {
        assertNull(combatPlan(area("Enter", emptyMap()), null))
    }

    @Test fun attackOnlyWhenAlreadyInTheMonstersCell() {
        val plan = combatPlan(area("r10", mapOf(7 to monster(7, "r10"))), 7)
        assertEquals(7, plan?.targetMapId)
        assertFalse(plan!!.moveFirst)
    }

    @Test fun moveFirstWhenSelfIsInAnotherCell() {
        val plan = combatPlan(area("Enter", mapOf(7 to monster(7, "r10"))), 7)
        assertEquals(7, plan?.targetMapId)
        assertTrue(plan!!.moveFirst)
        assertEquals("r10", plan.cell)
        assertEquals("Left", plan.pad)
    }

    @Test fun fallsBackToFirstAliveMonster() {
        val plan = combatPlan(
            area("Enter", mapOf(1 to monster(1, "r2", hp = 0), 2 to monster(2, "r3"))),
            null,
        )
        assertEquals(2, plan?.targetMapId)
        assertTrue(plan!!.moveFirst)
        assertEquals("r3", plan.cell)
    }
}