package com.aetheraquest.android

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A hardcoded colour inside a TextStyle overrides the contentColor a component
 * sets, so a button whose container and label colours come from typography
 * renders its label invisible. Every style must leave colour unspecified and
 * inherit from LocalContentColor.
 */
class ThemeTextColorTest {

    private fun styles(): List<Pair<String, TextStyle>> = with(AetheraTypographyStyles) {
        listOf(
            "displayLarge" to displayLarge,
            "displayMedium" to displayMedium,
            "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge,
            "headlineMedium" to headlineMedium,
            "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge,
            "titleMedium" to titleMedium,
            "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge,
            "bodyMedium" to bodyMedium,
            "bodySmall" to bodySmall,
            "labelLarge" to labelLarge,
            "labelMedium" to labelMedium,
            "labelSmall" to labelSmall,
        )
    }

    @Test
    fun `no text style pins a colour`() {
        val pinned = styles().filter { (_, style) -> style.color != Color.Unspecified }.map { it.first }
        assertEquals(emptyList<String>(), pinned)
    }
}
