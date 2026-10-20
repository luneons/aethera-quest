package com.aetheraquest.android

/** Confirmation sent by the server after an equip request is accepted. */
data class EquipConfirmation(
    val userId: Int,
    val itemId: Int,
    val slot: EquipmentSlot,
)

/**
 * Parses the live `equipItem` broadcast:
 *
 *   {"cmd":"equipItem","uid":...,"strES":"ba","ItemID":75592,...}
 *
 * Sending the packet only proves it left the client. The broadcast proves the
 * server accepted the change, which matters for rapid weapon/cape swaps that
 * otherwise race each other and appear random.
 */
internal fun parseEquipConfirmation(root: org.json.JSONObject): EquipConfirmation? {
    val inner = root.optJSONObject("b")?.optJSONObject("o") ?: root
    if (inner.optString("cmd") != "equipItem") return null
    val itemId = inner.optInt("ItemID", 0)
    val slot = slotForCode(inner.optString("strES")) ?: return null
    if (itemId <= 0) return null
    return EquipConfirmation(inner.optInt("uid", 0), itemId, slot)
}
