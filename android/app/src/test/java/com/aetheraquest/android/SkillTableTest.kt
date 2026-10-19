package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client must learn the equipped class and the skill table from the server,
 * never from a hardcoded guess. These tests use a real `sAct` payload captured
 * from a live session (Mage, four active skills plus Auto Attack).
 */
class SkillTableTest {

    // Trimmed from a real capture; field names and shapes are untouched.
    private val sActJson = """
        {"cmd":"sAct","actions":{"active":[
          {"id":89,"nam":"Auto Attack","typ":"aa","ref":"aa","range":301,"mp":0,"cd":2000,"isOK":true},
          {"id":115,"nam":"Fireball","typ":"m","ref":"a1","range":808,"mp":20,"cd":6000,"isOK":true},
          {"id":116,"nam":"Ice Shard","typ":"m","ref":"a2","range":808,"mp":15,"cd":6000,"isOK":true},
          {"id":117,"nam":"Explosion","typ":"m","ref":"a3","range":808,"mp":10,"cd":3000,"isOK":true},
          {"id":118,"nam":"Arcane Shield","typ":"m","ref":"a4","range":808,"mp":0,"cd":20000,"isOK":false}
        ],"passive":[
          {"id":119,"nam":"Hot Headed","typ":"passive","ref":"p1","isOK":true}
        ]}}
    """.trimIndent()

    @Test
    fun `parses the active skills only`() {
        val table = parseSkillTable(sActJson)
        assertEquals(setOf("aa", "a1", "a2", "a3", "a4"), table.keys)
    }

    @Test
    fun `reads cooldown mana and usability`() {
        val table = parseSkillTable(sActJson)
        val fireball = table.getValue("a1")
        assertEquals(6000L, fireball.cooldownMs)
        assertEquals(20, fireball.manaCost)
        assertTrue(fireball.usable)
        // The server marked Arcane Shield as not ok in this capture.
        assertFalse(table.getValue("a4").usable)
    }

    @Test
    fun `ignores passive entries`() {
        val table = parseSkillTable(sActJson)
        assertNull(table["p1"])
    }

    @Test
    fun `unknown refs are dropped`() {
        val json = """{"cmd":"sAct","actions":{"active":[
            {"ref":"zz","cd":1000,"mp":0,"isOK":true},
            {"ref":"a1","cd":1000,"mp":0,"isOK":true}
        ]}}"""
        assertEquals(setOf("a1"), parseSkillTable(json).keys)
    }

    @Test
    fun `malformed payload yields an empty table instead of throwing`() {
        assertTrue(parseSkillTable("not json").isEmpty())
        assertTrue(parseSkillTable("").isEmpty())
        assertTrue(parseSkillTable("""{"cmd":"sAct"}""").isEmpty())
    }

    @Test
    fun `class name is read from updateClass`() {
        val json = """{"cmd":"updateClass","uid":46637,"sClassName":"Void Highlord","sClassCat":"M1","iCP":302500}"""
        assertEquals("Void Highlord", parseClassName(json))
    }

    @Test
    fun `class name is read from the equipped class item`() {
        // sLink ("BaseMageRedesign3") is an internal asset key, not a display
        // name; the equipped inventory entry carries the real name in sName.
        val json = """{"cmd":"loadInventoryBig","items":[
            {"ItemID":15653,"sName":"Mage","sType":"Class","bEquip":true,"sLink":"BaseMageRedesign3"},
            {"ItemID":38259,"sName":"Void Highlord","sType":"Class","bEquip":false,"sLink":"VoidArmor"}
        ]}"""
        assertEquals("Mage", parseClassName(json))
    }

    @Test
    fun `class name is null for unrelated packets`() {
        assertNull(parseClassName("""{"cmd":"uotls","unm":"mel e"}"""))
    }

    @Test
    fun `inventory without an equipped class yields null`() {
        val json = """{"cmd":"loadInventoryBig","items":[
            {"ItemID":38259,"sName":"Void Highlord","sType":"Class","bEquip":false}
        ]}"""
        assertNull(parseClassName(json))
    }
}
