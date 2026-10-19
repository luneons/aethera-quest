package com.aetheraquest.android

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the brand palette to the launcher artwork: a deep navy ground with
 * electric cyan, neon green, and hot pink accents. Changing these values is a
 * deliberate brand decision, not an incidental refactor.
 */
class AetheraThemeTest {

    @Test
    fun backgroundMatchesTheLauncherArtworkGround() {
        assertEquals(Color(0xFF00040E), SpaceNavy)
    }

    @Test
    fun primaryIsTheDragonGlowCyan() {
        assertEquals(Color(0xFF00BFFF), NeonCyan)
    }

    @Test
    fun accentsCoverTheThreeLogoHues() {
        assertEquals(Color(0xFF1DE9B6), NeonGreen)
        assertEquals(Color(0xFFFF3D7F), NeonPink)
    }

    @Test
    fun panelsAreDarkNavyAndDistinctFromBackground() {
        assertEquals(Color(0xFF0A1628), DarkPanel)
        assertEquals(Color(0xFF0F1E33), DarkSurface)
        // Panels must stay lighter than the ground or cards vanish into it.
        assertEquals(true, DarkPanel.red > SpaceNavy.red)
        assertEquals(true, DarkSurface.red > DarkPanel.red)
    }

    @Test
    fun hpBarsEscalateFromGreenThroughAmberToRed() {
        assertEquals(Color(0xFF00E676), HpGreen)
        assertEquals(Color(0xFFFFEA00), HpYellow)
        assertEquals(Color(0xFFFF1744), HpRed)
    }
}
