package com.aetheraquest.android

/** Result of checking a configured taunt consumable against a live inventory. */
sealed interface ConsumableCheck {
    /** The configured ItemID is owned by this account. */
    data class Ready(val item: OwnedItem) : ConsumableCheck

    /** Nothing configured yet. */
    data object NotConfigured : ConsumableCheck

    /**
     * The configured ItemID is not in this account's inventory.
     *
     * AQW has both an ItemID and a per-character CharItemID, and the wiki's
     * numbers are a different namespace entirely, so a wrong value is common.
     * Saying so is far better than casting a scroll the server will ignore.
     */
    data class NotOwned(val configuredId: Int) : ConsumableCheck

    /** The inventory has not arrived yet; nothing can be concluded. */
    data object Unknown : ConsumableCheck
}

/**
 * Checks the configured taunt scroll against the items this account owns.
 *
 * Scrolls are `misc-items` in the wiki but arrive as ordinary inventory rows, so
 * the only trustworthy test is membership by ItemID.
 */
internal fun checkConsumable(configuredId: Int, inventory: List<OwnedItem>): ConsumableCheck = when {
    configuredId <= 0 -> ConsumableCheck.NotConfigured
    inventory.isEmpty() -> ConsumableCheck.Unknown
    else -> inventory.firstOrNull { it.itemId == configuredId }
        ?.let { ConsumableCheck.Ready(it) }
        ?: ConsumableCheck.NotOwned(configuredId)
}

/** Short status text for one check, used by the companion status strip. */
internal fun consumableStatusText(check: ConsumableCheck): String = when (check) {
    is ConsumableCheck.Ready -> "scroll ready"
    ConsumableCheck.NotConfigured -> "set Scroll ItemID"
    ConsumableCheck.Unknown -> "checking scroll"
    is ConsumableCheck.NotOwned -> "scroll ${check.configuredId} not in inventory"
}
