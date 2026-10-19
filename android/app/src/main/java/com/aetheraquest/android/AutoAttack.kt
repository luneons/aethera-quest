package com.aetheraquest.android

/**
 * One entry of the live `sAct` action list.
 *
 * Everything here is reported by the server; the client never invents a
 * cooldown, a mana cost or an unlock state. `usable` mirrors the server's
 * `isOK` flag, which is how AQW says "this button is greyed out right now".
 */
data class SkillInfo(
    val ref: String,
    val cooldownMs: Long = 0,
    val manaCost: Int = 0,
    val usable: Boolean = true,
)

/**
 * Rotation cursor for auto attack.
 *
 * `targetMapId` is part of the state because the rotation is per fight: a new
 * monster starts at the first skill again, which is what the reference client
 * does when you double-click a different monster.
 */
data class AutoAttackState(
    val className: String = "",
    val targetMapId: Int? = null,
    val index: Int = 0,
    val enabled: Boolean = false,
)

/** What the loop should send, plus the cursor to carry into the next tick. */
data class AutoAttackDecision(
    val ref: String,
    val nextIndex: Int,
)

/**
 * Chooses the next combat action.
 *
 * Rules, in order:
 * 1. No target → nothing to do.
 * 2. A new target or a new class resets the cursor, so the first skill of the
 *    rotation always fires instead of inheriting an offset from another fight.
 * 3. Walk the rotation from the cursor and take the first skill the server
 *    currently allows (unlocked, off cooldown, affordable). Blocked skills are
 *    skipped rather than retried, otherwise a locked `a1` would stall the loop.
 * 4. When nothing in the rotation is usable, fall back to `aa`: a basic swing
 *    is always available and keeps damage flowing while cooldowns recover.
 *
 * Returns null only when there is no target.
 */
internal fun nextAutoAction(
    state: AutoAttackState,
    rotation: List<String>?,
    skills: Map<String, SkillInfo>,
    nowMs: Long,
    lastUsed: Map<String, Long>,
    mana: Int,
    targetMapId: Int?,
    previousClassName: String? = null,
): AutoAttackDecision? {
    if (targetMapId == null) return null

    val classChanged = previousClassName != null && !previousClassName.equals(state.className, ignoreCase = true)
    val targetChanged = state.targetMapId != targetMapId
    var cursor = if (classChanged || targetChanged) 0 else state.index

    val order = rotation?.takeIf { it.isNotEmpty() }
    if (order != null) {
        repeat(order.size) {
            val slot = cursor % order.size
            val ref = order[slot]
            val next = (slot + 1) % order.size
            val info = skills[ref]
            val blocked = info != null && (
                !info.usable ||
                    info.manaCost > mana ||
                    nowMs - (lastUsed[ref] ?: Long.MIN_VALUE / 2) < info.cooldownMs
                )
            if (!blocked) return AutoAttackDecision(ref, next)
            cursor = next
        }
    }
    // Nothing in the rotation is available: keep swinging.
    return AutoAttackDecision("aa", cursor)
}

/**
 * Resolves the rotation for a class name from the bundled table.
 *
 * Lookup is case-insensitive because the game reports `ArchMage` in one place
 * and players type `archmage` in another.
 */
internal fun rotationFor(
    className: String,
    table: Map<String, List<String>> = ClassRotations.byName,
): List<String>? {
    val wanted = className.trim()
    if (wanted.isEmpty()) return null
    table[wanted]?.let { return it }
    return table.entries.firstOrNull { it.key.equals(wanted, ignoreCase = true) }?.value
}
