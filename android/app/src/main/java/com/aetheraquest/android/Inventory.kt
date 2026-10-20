package com.aetheraquest.android

import org.json.JSONObject

/**
 * The five equipment slots the game actually lets a player swap.
 *
 * The values are the server's own `sES` codes. Keeping the wire code next to
 * the label means a dropdown can never drift from what the packet says.
 */
enum class EquipmentSlot(val code: String, val label: String) {
    CLASS("ar", "Class"),
    WEAPON("Weapon", "Weapon"),
    ARMOR("co", "Armor"),
    HELM("he", "Helm"),
    CAPE("ba", "Cape"),
}

/** Every slot, in the order the UI shows them. */
internal fun equipmentSlots(): List<EquipmentSlot> = EquipmentSlot.entries

/** One owned item that can be equipped. */
data class OwnedItem(
    val itemId: Int,
    val name: String,
    val slot: EquipmentSlot?,
    val equipped: Boolean,
    val level: Int = 0,
)

/**
 * Parses `loadInventoryBig` into the items a dropdown may offer.
 *
 * Items whose `sES` is not one of the five equipment slots (quest items,
 * resources, houses) are kept but carry `slot = null`, so a caller can never
 * accidentally offer something un-equippable.
 */
internal fun parseInventory(payload: String): List<OwnedItem> =
    runCatching { JSONObject(payload) }.getOrNull()?.let(::parseInventory) ?: emptyList()

/** JSONObject overload for callers that already decoded the packet. */
internal fun parseInventory(root: JSONObject): List<OwnedItem> {
    val inner = root.optJSONObject("b")?.optJSONObject("o") ?: root
    if (inner.optString("cmd") != "loadInventoryBig") return emptyList()
    val items = inner.optJSONArray("items") ?: return emptyList()
    val result = mutableListOf<OwnedItem>()
    for (i in 0 until items.length()) {
        val raw = items.optJSONObject(i) ?: continue
        val itemId = raw.optInt("ItemID", 0)
        val name = raw.optString("sName").trim()
        if (itemId <= 0 || name.isEmpty()) continue
        result += OwnedItem(
            itemId = itemId,
            name = name,
            slot = slotForCode(raw.optString("sES")),
            equipped = raw.optBoolean("bEquip", false),
            level = raw.optInt("iLvl", 0),
        )
    }
    return result
}

/** Maps an `sES` code to its slot; `null` means "not equippable here". */
internal fun slotForCode(code: String): EquipmentSlot? {
    val wanted = code.trim()
    return EquipmentSlot.entries.firstOrNull { it.code.equals(wanted, ignoreCase = true) }
}

/** The items a dropdown for [slot] should list. */
internal fun equipmentBySlot(items: List<OwnedItem>, slot: EquipmentSlot): List<OwnedItem> =
    items.filter { it.slot == slot }.sortedBy { it.name.lowercase() }

/**
 * Marks [chosen] as the only equipped item in its slot.
 *
 * A live server does not always answer `retrieveInventory` after `equipItem`,
 * so the dropdown would keep showing the previous class while combat already
 * uses the new rotation. This applies the swap locally; the next real
 * `loadInventoryBig` still wins because it replaces the whole list.
 */
internal fun applyEquipped(items: List<OwnedItem>, chosen: OwnedItem): List<OwnedItem> =
    items.map { owned ->
        when {
            owned.itemId == chosen.itemId -> owned.copy(equipped = true)
            owned.slot == chosen.slot -> owned.copy(equipped = false)
            else -> owned
        }
    }
