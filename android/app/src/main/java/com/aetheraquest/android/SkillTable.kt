package com.aetheraquest.android

import org.json.JSONObject

/**
 * Parses the live `sAct` payload into the skill table the auto-attack loop
 * consults. Only `active` entries with a known combat ref are kept; passives
 * and unparsable payloads yield nothing rather than an error, because a class
 * without skills must simply fall back to `aa`.
 */
internal fun parseSkillTable(payload: String): Map<String, SkillInfo> =
    runCatching { JSONObject(payload) }.getOrNull()?.let(::parseSkillTable) ?: emptyMap()

/** JSONObject overload so callers that already decoded can reuse the logic. */
internal fun parseSkillTable(root: JSONObject): Map<String, SkillInfo> {
    // Handle both the full packet envelope and the inner object.
    val inner = root.optJSONObject("b")?.optJSONObject("o") ?: root
    val active = inner.optJSONObject("actions")?.optJSONArray("active") ?: return emptyMap()
    val result = mutableMapOf<String, SkillInfo>()
    for (i in 0 until active.length()) {
        val raw = active.optJSONObject(i) ?: continue
        val ref = raw.optString("ref")
        // `i1` is the consumable slot the server publishes alongside class
        // skills; keeping it lets A5 light up only when it really exists.
        if (ref !in setOf("aa", "a1", "a2", "a3", "a4", "i1")) continue
        result[ref] = SkillInfo(
            ref = ref,
            cooldownMs = raw.optLong("cd", 0L),
            manaCost = raw.optInt("mp", 0),
            usable = raw.optBoolean("isOK", true),
            name = raw.optString("nam").trim(),
        )
    }
    return result
}

/**
 * Extracts the currently equipped class name from the packet that carries it.
 *
 * The server announces a class change with `updateClass` (`sClassName`, the
 * display name) and reports the equipped gear with `loadInventoryBig`, where
 * the class item has `sType: "Class"` and `bEquip: true`. Both arrive on login
 * and on every swap, so watching both keeps the rotation in sync.
 *
 * `equipItem` is deliberately not used: its `sLink` is an internal asset key
 * ("BaseMageRedesign3" for the class named "Mage"), which matches nothing in
 * the rotation table.
 */
internal fun parseClassName(payload: String): String? =
    runCatching { JSONObject(payload) }.getOrNull()?.let(::parseClassName)

/** JSONObject overload so callers that already decoded can reuse the logic. */
internal fun parseClassName(root: JSONObject): String? {
    val inner = root.optJSONObject("b")?.optJSONObject("o") ?: root
    return when (inner.optString("cmd")) {
        "updateClass" -> inner.optString("sClassName").ifBlank { null }
        "loadInventoryBig" -> {
            val items = inner.optJSONArray("items") ?: return null
            (0 until items.length())
                .mapNotNull { items.optJSONObject(it) }
                .firstOrNull { it.optString("sType") == "Class" && it.optBoolean("bEquip", false) }
                ?.optString("sName")?.ifBlank { null }
        }
        else -> null
    }
}
