package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatHudTest {
    @Test fun `bar fraction is clamped and survives missing values`() {
        assertEquals(0.5f, barFraction(50, 100), 0.0001f)
        assertEquals(0f, barFraction(null, 100), 0.0001f)
        assertEquals(1f, barFraction(150, 100), 0.0001f)
        assertEquals(0f, barFraction(50, 0), 0.0001f)
        assertEquals(0f, barFraction(50, null), 0.0001f)
    }

    @Test fun `cooldown counts down from the live class cd`() {
        val skills = mapOf("a1" to SkillInfo("a1", cooldownMs = 6_000, manaCost = 20))
        val readyAt = mapOf("a1" to 16_000L)

        val waiting = manualSkillReadiness(1, skills, readyAt, nowMs = 10_000, mana = 100)
        assertEquals(6_000L, waiting.remainingMs)
        assertFalse(waiting.enabled)

        val ready = manualSkillReadiness(1, skills, readyAt, nowMs = 16_000, mana = 100)
        assertEquals(0L, ready.remainingMs)
        assertTrue(ready.enabled)
        assertNull(ready.reason)
    }

    @Test fun `not enough mana blocks the skill`() {
        val skills = mapOf("a2" to SkillInfo("a2", cooldownMs = 6_000, manaCost = 40))
        val readiness = manualSkillReadiness(2, skills, emptyMap(), nowMs = 10_000, mana = 15)
        assertFalse(readiness.enabled)
        assertEquals("Not enough mana", readiness.reason)
    }

    @Test fun `a5 stays hidden until the server reports the potion slot`() {
        val skills = mapOf("aa" to SkillInfo("aa"), "a1" to SkillInfo("a1"))
        val readiness = manualSkillReadiness(5, skills, emptyMap(), nowMs = 10_000, mana = 100)
        assertFalse(readiness.enabled)
        assertEquals("Not available", readiness.reason)
    }

    @Test fun `a0 swings even without a class table`() {
        val readiness = manualSkillReadiness(0, emptyMap(), emptyMap(), nowMs = 10_000, mana = null)
        assertTrue(readiness.enabled)
    }

    @Test fun `cd label shows tenths and whole seconds`() {
        assertEquals("3.2s", cooldownLabel(3_200))
        assertEquals("6s", cooldownLabel(6_000))
        assertEquals("", cooldownLabel(0))
    }
}
