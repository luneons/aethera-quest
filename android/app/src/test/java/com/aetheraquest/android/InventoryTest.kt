package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `loadInventoryBig` is the only packet that lists what the character owns.
 *
 * Every item carries `sES`, the server's slot code, which is what decides
 * whether it belongs in the class, weapon, armor, helm or cape dropdown:
 *
 *   Weapon -> weapon, ar -> class, co -> armor, he -> helm, ba -> cape
 *
 * `bEquip` marks the one currently worn, so the dropdown can show the active
 * choice instead of making the player guess.
 */
class InventoryTest {
    private val packet = """
    {"t":"xt","b":{"r":-1,"o":{"cmd":"loadInventoryBig","items":[
      {"ItemID":15653,"sName":"Mage","sType":"Class","sES":"ar","bEquip":true,"iLvl":1},
      {"ItemID":15654,"sName":"Rogue","sType":"Class","sES":"ar","bEquip":false,"iLvl":1},
      {"ItemID":3,"sName":"Default Staff","sType":"Staff","sES":"Weapon","bEquip":true,"iLvl":1},
      {"ItemID":9,"sName":"Blade of Doom","sType":"Sword","sES":"Weapon","bEquip":false,"iLvl":20},
      {"ItemID":11,"sName":"Battle Armor","sType":"Armor","sES":"co","bEquip":true,"iLvl":10},
      {"ItemID":12,"sName":"Wizard Helm","sType":"Helm","sES":"he","bEquip":false,"iLvl":5},
      {"ItemID":13,"sName":"Traveler Cape","sType":"Cape","sES":"ba","bEquip":true,"iLvl":5},
      {"ItemID":14,"sName":"Random Quest Item","sType":"Quest Item","sES":"None","bEquip":false}
    ]}}}
    """.trimIndent()

    @Test fun `every slot is read from sES`() {
        val items = parseInventory(packet)
        assertEquals(8, items.size)
        assertEquals(EquipmentSlot.CLASS, items[0].slot)
        assertEquals(EquipmentSlot.WEAPON, items[2].slot)
        assertEquals(EquipmentSlot.ARMOR, items[4].slot)
        assertEquals(EquipmentSlot.HELM, items[5].slot)
        assertEquals(EquipmentSlot.CAPE, items[6].slot)
    }

    @Test fun `items outside the five slots are not offered`() {
        val items = parseInventory(packet)
        val questItem = items.firstOrNull { it.name == "Random Quest Item" }
        assertNull(questItem?.slot)
        assertEquals(5, equipmentSlots().size)
        // Only the seven wearable items across the five slots can be equipped.
        assertEquals(7, items.count { it.slot != null })
    }

    @Test fun `class dropdown lists every owned class`() {
        val classes = equipmentBySlot(parseInventory(packet), EquipmentSlot.CLASS)
        assertEquals(listOf("Mage", "Rogue"), classes.map { it.name })
    }

    @Test fun `the equipped item is flagged`() {
        val classes = equipmentBySlot(parseInventory(packet), EquipmentSlot.CLASS)
        assertEquals("Mage", classes.first { it.equipped }.name)
    }

    @Test fun `weapon armor helm cape are each offered`() {
        val items = parseInventory(packet)
        assertEquals(listOf("Blade of Doom", "Default Staff"),
            equipmentBySlot(items, EquipmentSlot.WEAPON).map { it.name })
        assertEquals(listOf("Battle Armor"), equipmentBySlot(items, EquipmentSlot.ARMOR).map { it.name })
        assertEquals(listOf("Wizard Helm"), equipmentBySlot(items, EquipmentSlot.HELM).map { it.name })
        assertEquals(listOf("Traveler Cape"), equipmentBySlot(items, EquipmentSlot.CAPE).map { it.name })
    }

    @Test fun `a malformed packet yields no items instead of crashing`() {
        assertTrue(parseInventory("{not json").isEmpty())
        assertTrue(parseInventory("""{"t":"xt","b":{"o":{"cmd":"loadInventoryBig"}}}""").isEmpty())
    }

    @Test fun `equip packet names the item id`() {
        val bytes = SfsProtocol.equipItem(room = 121047, itemId = 15654)
        assertEquals("%xt%zm%equipItem%121047%15654%", String(bytes, Charsets.UTF_8).trimEnd('\u0000'))
    }

    @Test fun `a new inventory replaces the old list`() {
        val first = parseInventory(packet)
        val second = parseInventory(
            """{"t":"xt","b":{"o":{"cmd":"loadInventoryBig","items":[
            {"ItemID":1,"sName":"Only Class","sType":"Class","sES":"ar","bEquip":true}]}}}""",
        )
        assertEquals(2, equipmentBySlot(first, EquipmentSlot.CLASS).size)
        assertEquals(1, equipmentBySlot(second, EquipmentSlot.CLASS).size)
    }

    @Test fun `local equip moves the check mark within one slot only`() {
        val items = parseInventory(packet)
        val rogue = items.first { it.name == "Rogue" }
        val next = applyEquipped(items, rogue)
        assertEquals("Rogue", equipmentBySlot(next, EquipmentSlot.CLASS).single { it.equipped }.name)
        assertEquals("Default Staff", equipmentBySlot(next, EquipmentSlot.WEAPON).single { it.equipped }.name)
    }
}
