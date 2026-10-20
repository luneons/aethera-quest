package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A wrong consumable ID is silently ignored by the server, so the app must say
 * "this scroll is not in your inventory" instead of pretending the taunt fired.
 * The Enrage ItemID is a fixed live-verified constant, not a wiki number: wiki
 * item ids live in a different namespace and would be wrong here.
 */
class ConsumableCheckTest {
    private val scroll = OwnedItem(SCROLL_OF_ENRAGE_ITEM_ID, "Scroll of Enrage", null, false)
    private val other = OwnedItem(46784, "Scroll of Enrage", null, false)

    @Test fun `enrage id is the live verified item id`() {
        assertEquals(12917, SCROLL_OF_ENRAGE_ITEM_ID)
    }

    @Test fun `a newly added companion already has the enrage id`() {
        val account = AuxiliaryAccount(id = 1, username = "demo-1")
        assertEquals(SCROLL_OF_ENRAGE_ITEM_ID, account.consumableItemId)
    }

    @Test fun `owned scroll is ready`() {
        val check = checkConsumable(SCROLL_OF_ENRAGE_ITEM_ID, listOf(scroll, other))
        assertTrue(check is ConsumableCheck.Ready)
    }

    @Test fun `wiki id is reported as not owned instead of silently failing`() {
        val check = checkConsumable(46784, listOf(scroll))
        assertEquals(ConsumableCheck.NotOwned(46784), check)
        assertEquals("scroll 46784 not in inventory", consumableStatusText(check))
    }

    @Test fun `unconfigured and unknown inventory are distinguished`() {
        assertEquals(ConsumableCheck.NotConfigured, checkConsumable(0, listOf(scroll)))
        assertEquals(ConsumableCheck.Unknown, checkConsumable(SCROLL_OF_ENRAGE_ITEM_ID, emptyList()))
    }
}
