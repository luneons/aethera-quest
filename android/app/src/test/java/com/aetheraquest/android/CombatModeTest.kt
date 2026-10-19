package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatModeTest {
    @Test fun `skill button labels map to AQW wire refs`() {
        assertEquals("aa", manualSkillRef(0))
        assertEquals("a1", manualSkillRef(1))
        assertEquals("a2", manualSkillRef(2))
        assertEquals("a3", manualSkillRef(3))
        assertEquals("a4", manualSkillRef(4))
        assertEquals("i1", manualSkillRef(5))
        assertNull(manualSkillRef(6))
    }

    @Test fun `manual skill requires a living target`() {
        val area = AreaSnapshot(monsters = mapOf(
            8 to MonsterState(8, name = "Demo Monster", cell = "r2", hp = 100, maxHp = 100, state = 1),
        ))
        assertEquals(ManualCombatAction.Cast(8, "a3"), manualCombatAction(area, 8, 3))
        assertNull(manualCombatAction(area.copy(monsters = emptyMap()), null, 3))
    }

    @Test fun `combat mode opens and back closes it`() {
        assertTrue(combatModeAfterOpen(false))
        assertFalse(combatModeAfterBack(true))
    }

    @Test fun `a5 is disabled when potion slot is unavailable`() {
        val skills = mapOf("aa" to SkillInfo("aa"), "a1" to SkillInfo("a1"))
        assertFalse(manualSkillEnabled(5, skills))
    }

    @Test fun `a5 is enabled when potion slot is live`() {
        val skills = mapOf("i1" to SkillInfo("i1"))
        assertTrue(manualSkillEnabled(5, skills))
    }
}
