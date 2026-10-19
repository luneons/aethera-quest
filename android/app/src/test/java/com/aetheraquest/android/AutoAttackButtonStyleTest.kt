package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Test

/** Prevents the auto-attack label from blending into its bright button. */
class AutoAttackButtonStyleTest {
    @Test fun `idle label uses dark text on pink`() {
        val style = autoAttackButtonStyle(active = false)
        assertEquals(NeonPink, style.container)
        assertEquals(SpaceNavy, style.content)
    }

    @Test fun `active label uses dark text on green`() {
        val style = autoAttackButtonStyle(active = true)
        assertEquals(HpGreen, style.container)
        assertEquals(SpaceNavy, style.content)
    }
}
