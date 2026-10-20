package com.aetheraquest.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Status summary shown directly on the primary dashboard when companions are
 * running, so the player knows what accounts 2..4 are doing without opening
 * any dialog.
 */
@Composable
fun CompanionStatusStrip(ui: UiState, onOpenDialog: () -> Unit) {
    val statuses = ui.companionStatuses
    Row(
        Modifier
            .fillMaxWidth()
            .background(DarkPanel, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Ultra · ${if (ui.companionsRunning) "Running" else "Stopped"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (ui.companionsRunning) NeonCyan else DimWhite,
        )
        if (statuses.isNotEmpty()) {
            Text(
                text = statuses.joinToString(" · ") { "${it.username.ifBlank { "acc#${it.id}" }}: ${it.detail}" },
                style = MaterialTheme.typography.bodySmall,
                color = DimWhite,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
        } else {
            Text(
                text = "no companion running",
                style = MaterialTheme.typography.bodySmall,
                color = DimWhite,
                modifier = Modifier.weight(1f),
            )
        }
        SecondaryButton("Config", onOpenDialog)
    }
}

/**
 * Small configuration dialog. It only manages credentials and roles for accounts
 * 2..4; it deliberately does not offer a separate radar, map, or combat view.
 */
@Composable
fun CompanionConfigDialog(
    ui: UiState,
    vm: AetheraQuestViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Companion Accounts (Ultra Auto)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Accounts 2..4 run headless in the background to alternate taunts on the primary's target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DimWhite,
                )
                if (ui.companions.isEmpty()) {
                    Text("No companion account added yet.", style = MaterialTheme.typography.bodyMedium)
                }
                ui.companions.forEach { account ->
                    CompanionAccountRow(account = account, onUpdate = { vm.updateCompanion(account.id, it) }, onRemove = { vm.removeCompanion(account.id) })
                }
                if (ui.companions.size < MAX_COMPANIONS) {
                    SecondaryButton("+ Add Companion", vm::addCompanion)
                }
            }
        },
        confirmButton = {
            if (ui.companionsRunning) {
                AccentButton("Stop Ultra", vm::stopCompanions, accentColor = HpRed)
            } else {
                AccentButton("Start Ultra", vm::startCompanions, accentColor = NeonCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun CompanionAccountRow(
    account: AuxiliaryAccount,
    onUpdate: ((AuxiliaryAccount) -> AuxiliaryAccount) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SpaceNavy, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Checkbox(
                checked = account.enabled,
                onCheckedChange = { checked -> onUpdate { it.copy(enabled = checked) } },
            )
            Text(account.role.label, style = MaterialTheme.typography.labelSmall, color = NeonCyan)
            TextButton(onClick = {
                val nextRole = when (account.role) {
                    CompanionRole.TAUNTER_A -> CompanionRole.TAUNTER_B
                    CompanionRole.TAUNTER_B -> CompanionRole.SUPPORT
                    CompanionRole.SUPPORT -> CompanionRole.DPS
                    CompanionRole.DPS -> CompanionRole.TAUNTER_A
                }
                onUpdate { it.copy(role = nextRole) }
            }) {
                Text("Role: ${account.role.label}")
            }
            TextButton(onClick = onRemove) { Text("Remove", color = HpRed) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = account.username,
                onValueChange = { name -> onUpdate { it.copy(username = name) } },
                label = { Text("Username") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = account.password,
                onValueChange = { pass -> onUpdate { it.copy(password = pass) } },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        if (account.role == CompanionRole.TAUNTER_A || account.role == CompanionRole.TAUNTER_B) {
            OutlinedTextField(
                value = if (account.consumableItemId == 0) "" else account.consumableItemId.toString(),
                onValueChange = { v -> onUpdate { it.copy(consumableItemId = v.toIntOrNull() ?: 0) } },
                label = { Text("Scroll of Enrage ItemID") },
                singleLine = true,
            )
        }
    }
}
