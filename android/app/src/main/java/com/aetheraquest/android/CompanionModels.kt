package com.aetheraquest.android

/**
 * Automation role for a companion account.
 *
 * TAUNTER_A / TAUNTER_B take turns keeping Focus up with a taunt consumable.
 * SUPPORT keeps the party alive. DPS just attacks whatever the primary picked.
 */
enum class CompanionRole(val label: String) {
    TAUNTER_A("Taunter A"),
    TAUNTER_B("Taunter B"),
    SUPPORT("Support"),
    DPS("DPS"),
}

/**
 * One saved companion account.
 *
 * [password] never leaves the device: it is stored encrypted, never logged and
 * never written into a status message. It is held here only so the coordinator
 * can log back in after a drop.
 */
data class AuxiliaryAccount(
    val id: Int,
    val username: String,
    val password: String = "",
    val role: CompanionRole = CompanionRole.TAUNTER_A,
    val enabled: Boolean = false,
    /** ItemID of the taunt consumable (Scroll of Enrage). */
    val consumableItemId: Int = SCROLL_OF_ENRAGE_ITEM_ID,
)

/**
 * Scroll of Enrage as observed on the live wire (`%xt%zm%geia%<room>%scroll%<effectId>%12917%`).
 * The trailing number is the inventory ItemID the `i1` cast must carry; the
 * earlier number in that packet is the scroll's effect id, not an item.
 */
internal const val SCROLL_OF_ENRAGE_ITEM_ID = 12917

/** What a companion is currently doing, for the one-line status list. */
enum class CompanionPhase(val label: String) {
    IDLE("idle"),
    CONNECTING("connecting"),
    IN_MAP("in map"),
    TAUNTING("taunting"),
    ERROR("error"),
    STOPPED("stopped"),
}

/** One status row shown under the primary dashboard. */
data class CompanionStatus(
    val id: Int,
    val username: String,
    val role: CompanionRole,
    val phase: CompanionPhase = CompanionPhase.IDLE,
    val detail: String = "",
)

/**
 * The taunt rotation order: Taunter A first, then Taunter B, each by id.
 * Non-taunt roles never appear here, so a Support account cannot consume a
 * taunt turn.
 */
internal fun tauntOrder(accounts: List<AuxiliaryAccount>): List<AuxiliaryAccount> =
    accounts
        .filter { it.enabled && (it.role == CompanionRole.TAUNTER_A || it.role == CompanionRole.TAUNTER_B) }
        .sortedWith(compareBy({ it.role != CompanionRole.TAUNTER_A }, { it.id }))
