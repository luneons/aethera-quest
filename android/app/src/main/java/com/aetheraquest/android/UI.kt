package com.aetheraquest.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Every interactive text element uses dark-on-bright colour pairs so text stays
 * readable on the dark SpaceNavy background both at rest and while pressed.
 *
 * Usage: replace bare `Button(...)` / `OutlinedButton(...)` / `TextButton(...)`
 * with the helpers below.  Never omit an explicit text colour.
 */

// ── Button helpers ────────────────────────────────────────────────────────────

/** Primary action: cyan fill + dark text. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonCyan,
            contentColor = SpaceNavy,
            disabledContainerColor = NeonCyan.copy(alpha = 0.38f),
            disabledContentColor = SpaceNavy.copy(alpha = 0.6f),
        ),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

/** Full-width primary button matching the old login button. */
@Composable
fun PrimaryButtonWide(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PrimaryButton(text, onClick, modifier.fillMaxWidth().height(48.dp), enabled)
}

/** Secondary action: transparent fill + cyan border + cyan text. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
    ) { Text(text) }
}

/** Tertiary action: no border + cyan text, for compact inline controls. */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan),
    ) { Text(text) }
}

/** Bright pink/green fill + dark text — for Auto Attack toggle. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = SpaceNavy,
            disabledContainerColor = accentColor.copy(alpha = 0.38f),
            disabledContentColor = SpaceNavy.copy(alpha = 0.6f),
        ),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}
