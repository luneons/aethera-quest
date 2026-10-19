package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapInteractionTest {
    @Test fun `brand header is only shown before login`() {
        assertTrue(showBrandHeader(Screen.LOGIN))
        assertFalse(showBrandHeader(Screen.SERVER))
        assertFalse(showBrandHeader(Screen.JOIN))
        assertFalse(showBrandHeader(Screen.MAP))
        assertFalse(showBrandHeader(Screen.HEADLESS))
    }

    @Test fun `double tapping a monster jumps to its cell`() {
        val monster = EntityRow("r4", "Monster", "Demo Monster", 100, 100, 17)
        assertEquals(EntityTapAction.Jump("r4"), entityTapAction(monster, doubleTap = true))
    }

    @Test fun `single tapping a monster selects it`() {
        val monster = EntityRow("r4", "Monster", "Demo Monster", 100, 100, 17)
        assertEquals(EntityTapAction.Select(17), entityTapAction(monster, doubleTap = false))
    }

    @Test fun `double tapping a player does not teleport`() {
        val player = EntityRow("r4", "Player", "Demo Player", 100, 100, null)
        assertEquals(EntityTapAction.Select(null), entityTapAction(player, doubleTap = true))
    }

    @Test fun `map location label keeps map away from combat controls`() {
        assertEquals("doomwood · Room 93006", mapLocationLabel("doomwood", 93006))
        assertEquals("Unknown map · Room –", mapLocationLabel(null, null))
    }
}
