package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto attack replaces the single-shot Attack button: it keeps hitting the
 * current target and interleaves the class's own skill rotation.
 *
 * These tests exercise the pure decision layer, so no network or Android
 * runtime is involved. The rules encoded here come from the live `sAct`
 * payload (ref / cd / mp / isOK) and the bundled rotation table.
 */
class AutoAttackEngineTest {

    private fun skills(vararg entries: SkillInfo) = entries.associateBy { it.ref }

    private val archMageRotation = listOf("a1", "a4", "a2", "a3")

    private val readySkills = skills(
        SkillInfo("aa", cooldownMs = 2000, manaCost = 0, usable = true),
        SkillInfo("a1", cooldownMs = 6000, manaCost = 20, usable = true),
        SkillInfo("a2", cooldownMs = 6000, manaCost = 15, usable = true),
        SkillInfo("a3", cooldownMs = 3000, manaCost = 10, usable = true),
        SkillInfo("a4", cooldownMs = 20000, manaCost = 0, usable = true),
    )

    @Test
    fun `no target means no action`() {
        val decision = nextAutoAction(
            state = AutoAttackState(className = "ArchMage", targetMapId = null),
            rotation = archMageRotation,
            skills = readySkills,
            nowMs = 0,
            lastUsed = emptyMap(),
            mana = 100,
            targetMapId = null,
        )
        assertNull(decision)
    }

    @Test
    fun `unknown class falls back to plain auto attack`() {
        val decision = nextAutoAction(
            state = AutoAttackState(className = "NotAClass", targetMapId = 7),
            rotation = null,
            skills = readySkills,
            nowMs = 0,
            lastUsed = emptyMap(),
            mana = 100,
            targetMapId = 7,
        )
        assertEquals("aa", decision?.ref)
    }

    @Test
    fun `known class walks its rotation in order`() {
        var state = AutoAttackState(className = "ArchMage", targetMapId = 7)
        val fired = mutableListOf<String>()
        repeat(4) {
            val decision = nextAutoAction(
                state = state,
                rotation = archMageRotation,
                skills = readySkills,
                nowMs = 100_000L, // everything off cooldown
                lastUsed = emptyMap(),
                mana = 100,
                targetMapId = 7,
            )!!
            fired += decision.ref
            state = state.copy(index = decision.nextIndex)
        }
        assertEquals(archMageRotation, fired)
    }

    @Test
    fun `rotation wraps after the last skill`() {
        val decision = nextAutoAction(
            state = AutoAttackState(className = "ArchMage", targetMapId = 7, index = 4),
            rotation = archMageRotation,
            skills = readySkills,
            nowMs = 100_000L,
            lastUsed = emptyMap(),
            mana = 100,
            targetMapId = 7,
        )
        assertEquals("a1", decision?.ref)
        assertEquals(1, decision?.nextIndex)
    }

    @Test
    fun `skill on cooldown is skipped`() {
        val decision = nextAutoAction(
            state = AutoAttackState(className = "ArchMage", targetMapId = 7, index = 0),
            rotation = archMageRotation,
            skills = readySkills,
            nowMs = 10_000L,
            // a1 was used 1s ago and has a 6s cooldown
            lastUsed = mapOf("a1" to 9_000L),
            mana = 100,
            targetMapId = 7,
        )
        assertEquals("a4", decision?.ref)
    }

    @Test
    fun `skill that costs more mana than available is skipped`() {
        val decision = nextAutoAction(
            state = AutoAttackState(className = "ArchMage", targetMapId = 7, index = 0),
            rotation = archMageRotation,
            skills = readySkills,
            nowMs = 100_000L,
            lastUsed = emptyMap(),
            mana = 12, // a1 costs 20, a4 costs 0
            targetMapId = 7,
        )
        assertEquals("a4", decision?.ref)
    }

    @Test
    fun `skill the server marked not ok is skipped`() {
        val locked = readySkills + ("a1" to SkillInfo("a1", cooldownMs = 6000, manaCost = 20, usable = false))
        val decision = nextAutoAction(
            state = AutoAttackState(className = "ArchMage", targetMapId = 7, index = 0),
            rotation = archMageRotation,
            skills = locked,
            nowMs = 100_000L,
            lastUsed = emptyMap(),
            mana = 100,
            targetMapId = 7,
        )
        assertEquals("a4", decision?.ref)
    }

    @Test
    fun `falls back to auto attack when every skill is blocked`() {
        val blocked = skills(
            SkillInfo("aa", cooldownMs = 2000, manaCost = 0, usable = true),
            SkillInfo("a1", cooldownMs = 6000, manaCost = 20, usable = false),
            SkillInfo("a4", cooldownMs = 20000, manaCost = 0, usable = false),
            SkillInfo("a2", cooldownMs = 6000, manaCost = 15, usable = false),
            SkillInfo("a3", cooldownMs = 3000, manaCost = 10, usable = false),
        )
        val decision = nextAutoAction(
            state = AutoAttackState(className = "ArchMage", targetMapId = 7, index = 0),
            rotation = archMageRotation,
            skills = blocked,
            nowMs = 100_000L,
            lastUsed = emptyMap(),
            mana = 100,
            targetMapId = 7,
        )
        assertEquals("aa", decision?.ref)
    }

    @Test
    fun `switching target restarts the rotation`() {
        val decision = nextAutoAction(
            state = AutoAttackState(className = "ArchMage", targetMapId = 7, index = 3),
            rotation = archMageRotation,
            skills = readySkills,
            nowMs = 100_000L,
            lastUsed = emptyMap(),
            mana = 100,
            targetMapId = 9, // different monster
        )
        assertEquals("a1", decision?.ref)
        assertEquals(1, decision?.nextIndex)
    }

    @Test
    fun `class change resets the rotation`() {
        // StoneCrusher rotates a3,a2,a1,a4 — index 3 from the old class must not
        // carry over, or the first skill of the new class would be skipped.
        val decision = nextAutoAction(
            state = AutoAttackState(className = "StoneCrusher", targetMapId = 7, index = 3),
            rotation = listOf("a3", "a2", "a1", "a4"),
            skills = readySkills,
            nowMs = 100_000L,
            lastUsed = emptyMap(),
            mana = 100,
            targetMapId = 7,
            previousClassName = "ArchMage",
        )
        assertEquals("a3", decision?.ref)
        assertEquals(1, decision?.nextIndex)
    }

    @Test
    fun `range never blocks an attack`() {
        val area = AreaSnapshot(
            mapName = "doomwood",
            self = PlayerState(username = "mel e", cell = "Enter", pad = "Left"),
            monsters = mapOf(
                21 to MonsterState(mapId = 21, name = "Undead Paladin", cell = "r2", hp = 13000, maxHp = 13000),
            ),
        )
        val plan = combatPlan(area, selected = 21, unlimitedRange = true)
        assertEquals(21, plan?.targetMapId)
        // The monster is in another cell; with unlimited range the client must
        // still fire instead of teleporting into the monster's cell first.
        assertEquals(false, plan?.moveFirst)
    }

    @Test
    fun `without unlimited range the plan still moves first`() {
        val area = AreaSnapshot(
            mapName = "doomwood",
            self = PlayerState(username = "mel e", cell = "Enter", pad = "Left"),
            monsters = mapOf(
                21 to MonsterState(mapId = 21, name = "Undead Paladin", cell = "r2", hp = 13000, maxHp = 13000),
            ),
        )
        val plan = combatPlan(area, selected = 21, unlimitedRange = false)
        assertTrue(plan?.moveFirst == true)
    }
}
