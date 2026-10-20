package com.aetheraquest.android

import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AetheraTheme { AetheraQuestApp() } }
    }
}

@Composable
fun AetheraQuestApp(vm: AetheraQuestViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Encrypted login storage is attached once; the store only keeps
    // applicationContext, so no Activity is leaked into the ViewModel.
    LaunchedEffect(Unit) {
        vm.attachCredentialStore(AndroidCredentialStore(context))
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val launch = vm.ui.value.startHeadless
        if (granted && launch != null) {
            HeadlessService.start(context, launch.login, launch.server, launch.map)
            vm.consumeHeadlessLaunch()
        } else if (!granted) vm.clearHeadlessLaunch("Notification permission is required for headless mode")
    }
    LaunchedEffect(ui.startHeadless) {
        val launch = ui.startHeadless ?: return@LaunchedEffect
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else {
            HeadlessService.start(context, launch.login, launch.server, launch.map)
            vm.consumeHeadlessLaunch()
        }
    }
    LaunchedEffect(Unit) {
        HeadlessRegistry.status.collect { status ->
            if (status != null) vm.updateHeadlessStatus(status)
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (showBrandHeader(ui.screen)) BrandHeader()
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                StatusLine(
                    text = if (ui.screen == Screen.HEADLESS) ui.headlessStatus.message else ui.status.message,
                    connected = ui.status.phase != ConnectionPhase.DISCONNECTED,
                )
                Spacer(Modifier.height(12.dp))
                ui.error?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = vm::clearError) { Text("Dismiss") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Box(Modifier.weight(1f)) {
                    when (ui.screen) {
                        Screen.LOGIN -> LoginScreen(ui, vm)
                        Screen.SERVER -> ServerScreen(ui, vm)
                        Screen.JOIN -> JoinScreen(ui, vm)
                        Screen.MAP -> MapScreen(ui, vm)
                        Screen.HEADLESS -> HeadlessScreen(ui, vm, onStop = {
                            context.stopService(android.content.Intent(context, HeadlessService::class.java))
                            HeadlessRegistry.clear()
                            vm.stopHeadlessUi()
                        })
                    }
                    if (ui.isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = NeonCyan)
                }
            }
        }
    }
}

/**
 * Brand bar: dark navy panel with a cyan rule underneath, echoing the neon
 * frame of the launcher icon.
 */
@Composable
private fun BrandHeader() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(DarkPanel, SpaceNavy)))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(R.drawable.brand_mark),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
            )
            Column {
                Text(
                    "AETHERA",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = PureWhite,
                )
                Text(
                    "QUEST",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 6.sp,
                    color = NeonCyan,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, NeonCyan, NeonGreen, Color.Transparent),
                    ),
                ),
        )
    }
}

/** Connection state as a coloured pill, so status is readable at a glance. */
@Composable
private fun StatusLine(text: String, connected: Boolean) {
    val dot = if (connected) HpGreen else NeonAmber
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dot))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = dot)
    }
}

@Composable
private fun LoginScreen(ui: UiState, vm: AetheraQuestViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in to enter the realm",
            style = MaterialTheme.typography.titleMedium,
            color = NeonCyan,
        )
        OutlinedTextField(ui.username, vm::updateUsername, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
        OutlinedTextField(
            ui.password, vm::updatePassword, Modifier.fillMaxWidth(), label = { Text("Password") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ui.rememberCredentials,
                onCheckedChange = vm::updateRememberCredentials,
                enabled = !ui.isLoading,
                colors = CheckboxDefaults.colors(
                    checkedColor = NeonCyan,
                    checkmarkColor = SpaceNavy,
                    uncheckedColor = NeonCyan,
                ),
            )
            Text("Save username & password", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButtonWide(
            text = "Login",
            onClick = vm::login,
            enabled = !ui.isLoading,
        )
    }
}

@Composable
private fun ServerScreen(ui: UiState, vm: AetheraQuestViewModel) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Choose server", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TertiaryButton("Refresh", vm::loadServers)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ui.servers, key = { it.name }) { server ->
                val selected = ui.selectedServer == server
                Card(
                    onClick = { vm.selectServer(server) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(server.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(if (server.full) "Full" else "Online")
                    }
                }
            }
        }
        PrimaryButtonWide(
            text = "Continue",
            onClick = vm::proceedToJoin,
            enabled = ui.selectedServer != null,
        )
    }
}

@Composable
private fun JoinScreen(ui: UiState, vm: AetheraQuestViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${ui.selectedServer?.name ?: "Server"} · Join map", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            ui.mapName, vm::updateMapName, Modifier.fillMaxWidth(), label = { Text("Map (for example: battleon-1234)") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Visual mode", vm::goToMap, Modifier.weight(1f), !ui.isLoading)
            SecondaryButton("Headless mode", vm::startHeadless, Modifier.weight(1f), !ui.isLoading)
        }
        Text(
            "Headless keeps the AQW socket alive in a foreground service without rendering the map.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HeadlessScreen(ui: UiState, vm: AetheraQuestViewModel, onStop: () -> Unit) {
    val status = ui.headlessStatus
    val area = status.area
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Headless mode", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(status.message, color = MaterialTheme.colorScheme.primary)
        area.mapName?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
        val details = buildList {
            area.roomId?.let { add("Room $it") }
            if (area.monsters.isNotEmpty()) add("${area.monsters.size} monsters")
        }.joinToString(" · ")
        if (details.isNotEmpty()) Text(details)
        Spacer(Modifier.height(20.dp))
        Text("Rendering is off. The connection continues through the foreground service.")
        Spacer(Modifier.height(20.dp))
        SecondaryButton("Stop headless", onStop)
    }
}

@Composable
private fun AnalogPad(
    modifier: Modifier = Modifier,
    onChange: (Float, Float) -> Unit,
    onRelease: () -> Unit,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val ringColor = NeonCyan.copy(alpha = 0.30f)
    val knobColor = NeonCyan
    val background = DarkPanel
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { knob = Offset.Zero },
                    onDragEnd = { knob = Offset.Zero; onRelease() },
                    onDragCancel = { knob = Offset.Zero; onRelease() },
                ) { change, drag ->
                    change.consume()
                    val limit = minOf(size.width, size.height) / 2f
                    val next = knob + drag
                    val length = kotlin.math.sqrt(next.x * next.x + next.y * next.y)
                    knob = if (limit > 0f && length > limit) next * (limit / length) else next
                    onChange(knob.x / limit.coerceAtLeast(1f), knob.y / limit.coerceAtLeast(1f))
                }
            }
            .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(50))
            .background(background, RoundedCornerShape(50)),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f
        drawCircle(ringColor, radius * 0.62f, center, style = Stroke(width = 2f))
        drawCircle(knobColor.copy(alpha = 0.18f), radius * 0.34f, center + knob)
        drawCircle(knobColor, radius * 0.24f, center + knob)
    }
}

internal data class AutoAttackButtonStyle(val container: Color, val content: Color)

/** Bright pink/green need a dark label to remain visible at rest and pressed. */
internal fun autoAttackButtonStyle(active: Boolean) = AutoAttackButtonStyle(
    container = if (active) HpGreen else NeonPink,
    content = SpaceNavy,
)

@Composable
private fun MapScreen(ui: UiState, vm: AetheraQuestViewModel) {
    val area = ui.status.area
    var x by remember { mutableStateOf("850") }
    var y by remember { mutableStateOf("302") }
    var cell by remember { mutableStateOf("") }
    var pad by remember { mutableStateOf("Spawn") }
    var mapDraft by remember { mutableStateOf("") }
    var chatDraft by remember { mutableStateOf("") }
    var entityFilter by remember { mutableStateOf(EntityFilter.ALL) }
    BackHandler(enabled = ui.combatMode) { vm.closeCombatMode() }
    if (ui.combatMode) {
        CombatModeScreen(ui, vm)
        return
    }
    val allRows = remember(area) {
        buildList {
            area.self?.let { add(EntityRow(it.cell ?: "Unknown", "Self", it.username, it.hp, it.maxHp, null)) }
            area.players.values.filter { it.username != area.self?.username }.forEach {
                add(EntityRow(it.cell ?: "Unknown", "Player", it.username, it.hp, it.maxHp, null))
            }
            area.monsters.values.filter { it.alive }.forEach {
                add(EntityRow(it.cell ?: "Unknown", "Monster", it.name ?: "Monster #${it.mapId}", it.hp, it.maxHp, it.mapId))
            }
        }
    }
    val grouped = remember(allRows, entityFilter) {
        filterEntityRows(allRows, entityFilter)
            .groupBy { it.cell }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    val monsterCount = allRows.count { it.kind == "Monster" }
    val playerCount = allRows.count { it.kind == "Player" || it.kind == "Self" }
    // Keys are computed per cell group so a duplicate never reaches LazyColumn.
    val keyed = remember(grouped) {
        grouped.mapValues { (_, entities) -> entityKeys(entities) }
    }
    // Entities sharing our own cell: the only ones the radar may plot. Radar is
    // intentionally independent from the list filter, so focusing the list on
    // monsters does not make players silently disappear from situational view.
    val sameCellEntities = remember(allRows, area.self?.cell) {
        val mine = area.self?.cell
        if (mine == null) emptyList()
        else allRows.filter { it.cell.equals(mine, ignoreCase = true) }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Map identity lives with the room/location metadata instead of
            // competing with combat controls for horizontal space.
            Text(
                mapLocationLabel(area.mapName, area.roomId),
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                // Auto attack replaces the old single-shot button: it keeps
                // firing and interleaves the equipped class's own rotation.
                val autoStyle = autoAttackButtonStyle(ui.autoAttacking)
                AccentButton(
                    text = if (ui.autoAttacking) "Auto ON" else "Auto",
                    onClick = vm::toggleAutoAttack,
                    accentColor = autoStyle.container,
                )
                SecondaryButton("Combat", vm::openCombatMode)
                SecondaryButton(if (ui.status.afk) "AFK on" else "AFK off", vm::toggleAfk)
                SecondaryButton("Disconnect", vm::disconnect)
            }
            // Class and gear swaps live on the dashboard: combat mode is for
            // pressing skills, and its height is already spoken for.
            EquipBar(ui, vm)
            // Class and rotation are shown so the user can see what the loop
            // decided to use instead of guessing why a skill never fired.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val className = ui.className.ifBlank { "Class unknown" }
                Text(className, style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                val rotation = rotationFor(ui.className)
                if (rotation != null) {
                    Text(
                        rotation.joinToString(" → "),
                        style = MaterialTheme.typography.bodySmall,
                        color = DimWhite,
                    )
                } else if (ui.className.isNotBlank()) {
                    Text("no rotation · auto attack only", style = MaterialTheme.typography.bodySmall, color = DimWhite)
                }
            }
            Text("generation ${area.generation}", style = MaterialTheme.typography.bodySmall)
            area.self?.let {
                Text("${it.username} · ${it.cell ?: "?"}/${it.pad ?: "?"} · (${it.x ?: "?"}, ${it.y ?: "?"})")
            }
            val pos = visibleMapPosition(area, ui.trackedPosition)
            if (pos != null) {
                Text(
                    "Position (${pos.x}, ${pos.y}) · " +
                        if (pos.confirmed) "server-confirmed" else "estimated from your own moves",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("Position unknown · server has not reported tx/ty yet", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AnalogPad(
                        Modifier.size(112.dp),
                        onChange = vm::walk,
                        onRelease = vm::stopWalking,
                    )
                    TertiaryButton(if (ui.running) "Speed: run" else "Speed: walk", vm::toggleRunning)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val cellLabel = area.self?.cell ?: "unknown cell"
                    Text(
                        "Radar · $cellLabel · ${sameCellEntities.size} here",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    PositionRadar(
                        area,
                        ui.trackedPosition,
                        sameCellEntities,
                        Modifier.fillMaxWidth().height(112.dp),
                    )
                }
            }
            RadarLegend()
            // `mv` cannot change cell, so cells get an explicit target instead of
            // a guessed direction. Only cells the server has revealed are listed.
            val targets = jumpableCells(area)
            if (targets.isNotEmpty()) {
                Text("Jump to cell", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    targets.forEach { cell ->
                        AssistChip(
                            onClick = { vm.jumpToCell(cell) },
                            label = { Text(cell, style = MaterialTheme.typography.labelMedium) },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = NeonCyan,
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, NeonCyan.copy(alpha = 0.5f),
                            ),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(x, { x = it }, "X", Modifier.weight(1f))
                NumberField(y, { y = it }, "Y", Modifier.weight(1f))
                PrimaryButton("Move", { vm.move(x.toIntOrNull() ?: 0, y.toIntOrNull() ?: 0) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(cell, { cell = it }, Modifier.weight(1f), label = { Text("Cell") }, singleLine = true)
                OutlinedTextField(pad, { pad = it }, Modifier.weight(1f), label = { Text("Pad") }, singleLine = true)
                PrimaryButton("Go", { vm.moveToCell(cell, pad) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    mapDraft, { mapDraft = it }, Modifier.weight(1f),
                    label = { Text("Switch map (e.g. yulgar-1234)") }, singleLine = true,
                )
                PrimaryButton(
                    text = if (ui.status.transferring) "Moving…" else "Go map",
                    onClick = { vm.transferToMap(mapDraft); mapDraft = "" },
                    enabled = !ui.status.transferring && mapNameError(mapDraft) == null,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Filter keeps a busy room readable: focusing on monsters hides the
            // crowd, focusing on players hides the monsters.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(entityFilter, EntityFilter.ALL, "All ${allRows.size}") { entityFilter = it }
                FilterChip(entityFilter, EntityFilter.MONSTERS, "Monster $monsterCount") { entityFilter = it }
                FilterChip(entityFilter, EntityFilter.PLAYERS, "Player $playerCount") { entityFilter = it }
            }
            nextFilterLabel(entityFilter, playerCount > 0, monsterCount > 0)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = DimWhite)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (cellName, entities) ->
                    item(key = "header-$cellName") { Text(cellName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    val keys = keyed[cellName] ?: entityKeys(entities)
                    itemsIndexed(entities, key = { index, _ -> keys[index] }) { _, entity ->
                        EntityCard(
                            entity = entity,
                            selected = entity.mapId != null && entity.mapId == ui.selectedMonsterId,
                            onClick = { vm.selectMonster(entity.mapId) },
                            onDoubleClick = {
                                when (val action = entityTapAction(entity, doubleTap = true)) {
                                    is EntityTapAction.Jump -> vm.jumpToCell(action.cell)
                                    is EntityTapAction.Select -> vm.selectMonster(action.mapId)
                                }
                            },
                        )
                    }
                }
                if (ui.status.chat.isNotEmpty()) {
                    item(key = "chat-header") { Text("Chat", fontWeight = FontWeight.Bold) }
                    itemsIndexed(ui.status.chat) { index, line ->
                        Text("[${line.channel}] ${line.sender}: ${line.message}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(chatDraft, { chatDraft = it }, Modifier.weight(1f), label = { Text("Zone chat") }, singleLine = true)
                PrimaryButton("Send", { vm.sendChat(chatDraft); chatDraft = "" })
            }
        }
    }
}

@Composable
private fun PositionRadar(
    area: AreaSnapshot,
    tracked: Pair<Int, Int>?,
    sameCell: List<EntityRow>,
    modifier: Modifier = Modifier,
) {
    val position = visibleMapPosition(area, tracked)
    val point = position?.let { radarProject(it.x, it.y) }
    val confirmed = position?.confirmed == true
    val gridColor = NeonCyan.copy(alpha = 0.16f)
    val dotColor = if (confirmed) HpGreen else NeonPink
    Canvas(
        modifier
            .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .background(DarkPanel, RoundedCornerShape(12.dp)),
    ) {
        // Divider lines to suggest the map square.
        val thirdX = size.width / 3f
        val thirdY = size.height / 3f
        for (i in 1..2) {
            drawLine(gridColor, Offset(thirdX * i, 0f), Offset(thirdX * i, size.height), 1f)
            drawLine(gridColor, Offset(0f, thirdY * i), Offset(size.width, thirdY * i), 1f)
        }
        // Only entities standing in our own cell are drawn: monsters elsewhere
        // are out of reach, so plotting them on the radar would imply a
        // proximity that does not exist.
        val ring = minOf(size.width, size.height) * 0.30f
        sameCell.take(12).forEachIndexed { index, entity ->
            val angle = (index.toFloat() / sameCell.take(12).size) * Math.PI.toFloat() * 2f
            val radius = ring * 0.72f
            val cx = size.width / 2f + kotlin.math.cos(angle) * radius
            val cy = size.height / 2f + kotlin.math.sin(angle) * radius
            drawCircle(
                color = entityColor(entity),
                radius = 4.5f,
                center = Offset(cx, cy),
            )
        }
        if (point != null) {
            val px = point.x * size.width
            val py = point.y * size.height
            // Unconfirmed positions get a hollow ring so they cannot be
            // mistaken for a server-confirmed one.
            if (confirmed) {
                drawCircle(dotColor.copy(alpha = 0.22f), 12f, Offset(px, py))
                drawCircle(dotColor, 6f, Offset(px, py))
            } else {
                drawCircle(dotColor, 6f, Offset(px, py), style = Stroke(width = 2.5f))
            }
        }
    }
}

private fun entityColor(entity: EntityRow): Color = when (entity.kind) {
    "Self" -> HpGreen
    "Player" -> NeonCyan
    else -> NeonPink
}

/** Colour key for the radar dots, so the dots are not a mystery. */
@Composable
private fun RadarLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(HpGreen, "you")
        LegendDot(NeonCyan, "player")
        LegendDot(NeonPink, "monster")
        Text("· same cell only", style = MaterialTheme.typography.labelSmall, color = DimWhite)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = DimWhite)
    }
}

/** A single-select chip for the entity list filter. */
@Composable
private fun FilterChip(
    current: EntityFilter,
    value: EntityFilter,
    label: String,
    onSelect: (EntityFilter) -> Unit,
) {
    val selected = current == value
    AssistChip(
        onClick = { onSelect(value) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = if (selected) SpaceNavy else DimWhite,
            containerColor = if (selected) NeonCyan else DarkSurface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) NeonCyan else NeonCyan.copy(alpha = 0.35f),
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntityCard(
    entity: EntityRow,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
) {
    val accent = when (entity.kind) {
        "Self" -> NeonGreen
        "Player" -> NeonCyan
        else -> NeonPink
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) NeonCyanDark else MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) NeonCyan else accent.copy(alpha = 0.35f),
        ),
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onDoubleClick = onDoubleClick,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entity.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) PureWhite else MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        entity.kind,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            val max = entity.maxHp ?: 0
            val hp = entity.hp ?: 0
            val progress = if (max > 0) hp.toFloat() / max else 0f
            val ratio = progress.coerceIn(0f, 1f)
            val barColor = when {
                ratio > 0.5f -> HpGreen
                ratio > 0.2f -> HpYellow
                else -> HpRed
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(DarkPanel),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(barColor),
                )
            }
            Text(
                "HP ${entity.hp ?: "?"}/${entity.maxHp ?: "?"}",
                style = MaterialTheme.typography.labelSmall,
                color = DimWhite,
            )
        }
    }
}

/**
 * Focused combat layout. It replaces the dense map dashboard so the six skill
 * buttons have room, and it is deliberately sized to fit one screen without
 * scrolling: the two button rows take all remaining height, so a skill is never
 * below the fold mid-fight. Android Back returns to the dashboard.
 */
@Composable
private fun CombatModeScreen(ui: UiState, vm: AetheraQuestViewModel) {
    val area = ui.status.area
    val target = resolveAttackTarget(area, ui.selectedMonsterId)?.let(area.monsters::get)
    val self = area.self
    // One clock for the whole screen so every countdown advances together.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(200)
        }
    }
    val skills = ui.status.skills.ifEmpty { ui.skills }
    val className = ui.status.className.ifBlank { ui.className }
    Column(
        Modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton("Back", vm::closeCombatMode)
            Column(Modifier.weight(1f)) {
                Text("Combat", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Text(
                    mapLocationLabel(area.mapName, area.roomId),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            val autoStyle = autoAttackButtonStyle(ui.autoAttacking)
            AccentButton(
                if (ui.autoAttacking) "Auto ON" else "Auto",
                vm::toggleAutoAttack,
                autoStyle.container,
            )
        }

        // Your vitals, kept to one compact block so the buttons get the room.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    self?.username ?: "You",
                    style = MaterialTheme.typography.labelMedium,
                    color = PureWhite,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (self?.dead == true) {
                    Text(
                        "DEAD · respawning",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonRed,
                    )
                }
            }
            ResourceBar(
                label = "HP",
                value = self?.hp,
                max = self?.maxHp,
                barColor = when {
                    barFraction(self?.hp, self?.maxHp) <= 0.25f -> HpRed
                    barFraction(self?.hp, self?.maxHp) <= 0.55f -> HpYellow
                    else -> HpGreen
                },
            )
            ResourceBar(
                label = "MP",
                value = self?.mp,
                max = self?.maxMp,
                barColor = NeonCyan,
            )
        }

        // Target line: name, HP bar and cell on one compact card.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    target?.name ?: "No living target",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    "cell ${target?.cell ?: "?"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DimWhite,
                )
            }
            ResourceBar(
                label = "HP",
                value = target?.hp,
                max = target?.maxHp,
                barColor = HpRed,
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Skills",
                style = MaterialTheme.typography.labelMedium,
                color = NeonCyan,
                modifier = Modifier.weight(1f),
            )
            Text(
                className.ifBlank { "class unknown" },
                style = MaterialTheme.typography.labelSmall,
                color = DimWhite,
                maxLines = 1,
            )
        }
        // Two equal rows fill whatever height is left: no scrolling, and the
        // buttons stay big enough to hit without looking.
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..2).forEach { index ->
                ManualSkillButton(index, ui, vm, skills, nowMs, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (3..5).forEach { index ->
                ManualSkillButton(index, ui, vm, skills, nowMs, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Text(
            "A0 basic · A1–A4 class skills · cooldowns from live class data",
            style = MaterialTheme.typography.labelSmall,
            color = DimWhite,
            maxLines = 1,
        )
    }
}

/**
 * Equipment dropdowns. Every entry comes from the server's `loadInventoryBig`,
 * so the list is exactly what the character owns — nothing is hardcoded, and a
 * class the account does not have can never appear.
 *
 * Each button is anchored to its own `Box` so the menu drops from the button
 * that opened it rather than from the top of the screen.
 */
@Composable
private fun EquipBar(ui: UiState, vm: AetheraQuestViewModel) {
    var openSlot by remember { mutableStateOf<EquipmentSlot?>(null) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EquipmentSlot.entries.forEach { slot ->
            val items = vm.itemsFor(slot)
            val worn = items.firstOrNull { it.equipped }
            Box {
                OutlinedButton(
                    onClick = { openSlot = if (openSlot == slot) null else slot },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${slot.label}: ${worn?.name ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                DropdownMenu(
                    expanded = openSlot == slot,
                    onDismissRequest = { openSlot = null },
                ) {
                    if (items.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No ${slot.label.lowercase()} owned") },
                            onClick = { openSlot = null },
                        )
                    }
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    buildString {
                                        append(item.name)
                                        if (item.level > 0) append(" · Lv ${item.level}")
                                        if (item.equipped) append("  ✓")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                openSlot = null
                                if (!item.equipped) vm.equipItem(item)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Labelled resource bar used for player and monster HP/MP. */
@Composable
private fun ResourceBar(label: String, value: Int?, max: Int?, barColor: Color) {
    val fraction = barFraction(value, max)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = DimWhite,
            modifier = Modifier.width(24.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(DarkPanel),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor),
            )
        }
        Text(
            "${value ?: "?"}/${max ?: "?"}",
            style = MaterialTheme.typography.labelSmall,
            color = PureWhite,
            modifier = Modifier.width(84.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun ManualSkillButton(
    index: Int,
    ui: UiState,
    vm: AetheraQuestViewModel,
    skills: Map<String, SkillInfo>,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    val hasTarget = resolveAttackTarget(ui.status.area, ui.selectedMonsterId) != null
    val readiness = manualSkillReadiness(
        index, skills, ui.skillReadyAt, nowMs, ui.status.area.self?.mp,
    )
    val enabled = hasTarget && readiness.enabled
    val ref = manualSkillRef(index)
    val skill = ref?.let(skills::get)
    Button(
        onClick = { vm.manualSkill(index) },
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonCyan,
            contentColor = SpaceNavy,
            disabledContainerColor = DarkPanel,
            disabledContentColor = DimWhite.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("A$index", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // Name, cooldown and cost all come from the live `sAct` entry.
            val subtitle = when {
                readiness.remainingMs > 0 -> cooldownLabel(readiness.remainingMs)
                !readiness.enabled -> readiness.reason ?: ""
                skill?.name?.isNotBlank() == true -> skill.name
                else -> ""
            }
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (skill != null && skill.manaCost > 0) {
                Text("${skill.manaCost} MP", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value, { onValueChange(it.filter { c -> c.isDigit() || c == '-' }) }, modifier,
        label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
    )
}
