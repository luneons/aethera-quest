package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttackTargetTest {
    private fun monster(mapId: Int, hp: Int, state: Int? = 1) =
        MonsterState(mapId = mapId, monsterId = mapId + 100, name = "Monster $mapId", cell = "Enter", hp = hp, maxHp = 100, state = state)

    @Test fun usesTheSelectedMonsterWhenItIsAlive() {
        val area = AreaSnapshot(monsters = mapOf(7 to monster(7, 100), 9 to monster(9, 100)))
        assertEquals(9, resolveAttackTarget(area, 9))
    }

    @Test fun fallsBackToTheFirstAliveMonsterWhenNothingIsSelected() {
        val area = AreaSnapshot(monsters = mapOf(7 to monster(7, 0, state = 0), 9 to monster(9, 100)))
        assertEquals(9, resolveAttackTarget(area, null))
    }

    @Test fun reportsNoTargetWhenEveryMonsterIsDead() {
        val area = AreaSnapshot(monsters = mapOf(7 to monster(7, 0, state = 0)))
        assertNull(resolveAttackTarget(area, null))
    }

    @Test fun aStaleSelectionFallsBackToALivingMonster() {
        val area = AreaSnapshot(monsters = mapOf(7 to monster(7, 100)))
        assertEquals(7, resolveAttackTarget(area, 999))
    }
}
