package com.aetheraquest.android

import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AetheraQuestApp() } }
    }
}

@Composable
fun AetheraQuestApp(vm: AetheraQuestViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Aethera Quest", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (ui.screen == Screen.HEADLESS) ui.headlessStatus.message else ui.status.message, color = MaterialTheme.colorScheme.primary)
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
                if (ui.isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun LoginScreen(ui: UiState, vm: AetheraQuestViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(ui.username, vm::updateUsername, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
        OutlinedTextField(
            ui.password, vm::updatePassword, Modifier.fillMaxWidth(), label = { Text("Password") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Text("Password is kept only in memory until login completes.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = vm::login, enabled = !ui.isLoading, modifier = Modifier.fillMaxWidth()) { Text("Login") }
    }
}

@Composable
private fun ServerScreen(ui: UiState, vm: AetheraQuestViewModel) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Choose server", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::loadServers) { Text("Refresh") }
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
        Button(onClick = vm::proceedToJoin, enabled = ui.selectedServer != null, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
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
            Button(onClick = vm::goToMap, enabled = !ui.isLoading, modifier = Modifier.weight(1f)) {
                Text("Visual mode")
            }
            OutlinedButton(onClick = vm::startHeadless, enabled = !ui.isLoading, modifier = Modifier.weight(1f)) {
                Text("Headless mode")
            }
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
        OutlinedButton(onClick = onStop) { Text("Stop headless") }
    }
}

@Composable
private fun AnalogPad(
    modifier: Modifier = Modifier,
    onChange: (Float, Float) -> Unit,
    onRelease: () -> Unit,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val ringColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val knobColor = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surfaceVariant
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
            .background(background, RoundedCornerShape(50)),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f
        drawCircle(ringColor, radius * 0.62f, center)
        drawCircle(knobColor, radius * 0.26f, center + knob)
    }
}

@Composable
private fun MapScreen(ui: UiState, vm: AetheraQuestViewModel) {
    val area = ui.status.area
    var x by remember { mutableStateOf("850") }
    var y by remember { mutableStateOf("302") }
    var cell by remember { mutableStateOf("") }
    var pad by remember { mutableStateOf("Spawn") }
    var mapDraft by remember { mutableStateOf("") }
    var chatDraft by remember { mutableStateOf("") }
    val grouped = remember(area) {
        buildList {
            area.self?.let { add(EntityRow(it.cell ?: "Unknown", "Self", it.username, it.hp, it.maxHp, null)) }
            area.players.values.filter { it.username != area.self?.username }.forEach {
                add(EntityRow(it.cell ?: "Unknown", "Player", it.username, it.hp, it.maxHp, null))
            }
            area.monsters.values.forEach {
                add(EntityRow(it.cell ?: "Unknown", "Monster", it.name ?: "Monster #${it.mapId}", it.hp, it.maxHp, it.mapId))
            }
        }.groupBy { it.cell }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    // Keys are computed per cell group so a duplicate never reaches LazyColumn.
    val keyed = remember(grouped) {
        grouped.mapValues { (_, entities) -> entityKeys(entities) }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(area.mapName ?: "Loading map…", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = vm::attack) {
                    val target = resolveAttackTarget(area, ui.selectedMonsterId)
                    Text(if (target == null) "Attack" else "Attack #$target")
                }
                OutlinedButton(onClick = vm::toggleAfk) { Text(if (ui.status.afk) "AFK on" else "AFK off") }
                OutlinedButton(onClick = vm::disconnect) { Text("Disconnect") }
            }
            Text("Room ${area.roomId ?: "–"} · generation ${area.generation}", style = MaterialTheme.typography.bodySmall)
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Position radar", style = MaterialTheme.typography.labelMedium)
                    PositionRadar(area, ui.trackedPosition, Modifier.fillMaxWidth().height(84.dp))
                }
                Schematic(grouped, Modifier.weight(1f).height(104.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AnalogPad(
                        Modifier.size(104.dp),
                        onChange = vm::walk,
                        onRelease = vm::stopWalking,
                    )
                    TextButton(onClick = vm::toggleRunning) {
                        Text(if (ui.running) "Speed: run" else "Speed: walk", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(x, { x = it }, "X", Modifier.weight(1f))
                NumberField(y, { y = it }, "Y", Modifier.weight(1f))
                Button(onClick = { vm.move(x.toIntOrNull() ?: 0, y.toIntOrNull() ?: 0) }) { Text("Move") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(cell, { cell = it }, Modifier.weight(1f), label = { Text("Cell") }, singleLine = true)
                OutlinedTextField(pad, { pad = it }, Modifier.weight(1f), label = { Text("Pad") }, singleLine = true)
                Button(onClick = { vm.moveToCell(cell, pad) }) { Text("Go") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    mapDraft, { mapDraft = it }, Modifier.weight(1f),
                    label = { Text("Switch map (e.g. yulgar-1234)") }, singleLine = true,
                )
                Button(
                    onClick = { vm.transferToMap(mapDraft); mapDraft = "" },
                    enabled = !ui.status.transferring && mapNameError(mapDraft) == null,
                ) { Text(if (ui.status.transferring) "Moving…" else "Go map") }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (cellName, entities) ->
                    item(key = "header-$cellName") { Text(cellName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    val keys = keyed[cellName] ?: entityKeys(entities)
                    itemsIndexed(entities, key = { index, _ -> keys[index] }) { _, entity ->
                        EntityCard(entity, selected = entity.mapId != null && entity.mapId == ui.selectedMonsterId) {
                            vm.selectMonster(entity.mapId)
                        }
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
                Button(onClick = { vm.sendChat(chatDraft); chatDraft = "" }) { Text("Send") }
            }
        }
    }
}

@Composable
private fun PositionRadar(area: AreaSnapshot, tracked: Pair<Int, Int>?, modifier: Modifier = Modifier) {
    val position = visibleMapPosition(area, tracked)
    val point = position?.let { radarProject(it.x, it.y) }
    val confirmed = position?.confirmed == true
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val dotColor = if (confirmed) Color(0xFF00C853) else MaterialTheme.colorScheme.tertiary
    Canvas(modifier.background(baseColor, RoundedCornerShape(12.dp))) {
        // Divider lines to suggest the map square.
        val thirdX = size.width / 3f
        val thirdY = size.height / 3f
        for (i in 1..2) {
            drawLine(gridColor, Offset(thirdX * i, 0f), Offset(thirdX * i, size.height), 1f)
            drawLine(gridColor, Offset(0f, thirdY * i), Offset(size.width, thirdY * i), 1f)
        }
        if (point != null) {
            val px = point.x * size.width
            val py = point.y * size.height
            // Unconfirmed positions get a hollow ring so they cannot be
            // mistaken for a server-confirmed one.
            if (confirmed) {
                drawCircle(dotColor, 6f, Offset(px, py))
            } else {
                drawCircle(dotColor, 6f, Offset(px, py), style = Stroke(width = 2.5f))
            }
        }
    }
}

@Composable
private fun Schematic(groups: Map<String, List<EntityRow>>, modifier: Modifier = Modifier) {
    Canvas(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))) {
        if (groups.isEmpty()) return@Canvas
        val columns = kotlin.math.ceil(kotlin.math.sqrt(groups.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = kotlin.math.ceil(groups.size / columns.toDouble()).toInt()
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        groups.entries.forEachIndexed { index, (_, entities) ->
            val cx = (index % columns + .5f) * cellWidth
            val cy = (index / columns + .5f) * cellHeight
            drawCircle(Color(0xFF455A64), minOf(cellWidth, cellHeight) * .30f, Offset(cx, cy))
            entities.forEachIndexed { entityIndex, entity -> drawEntity(entity, cx, cy, entityIndex, entities.size) }
        }
    }
}

private fun DrawScope.drawEntity(entity: EntityRow, cx: Float, cy: Float, index: Int, count: Int) {
    val angle = (index.toFloat() / count.coerceAtLeast(1)) * Math.PI.toFloat() * 2
    val radius = 25f
    val color = when (entity.kind) {
        "Self" -> Color(0xFF00C853)
        "Player" -> Color(0xFF29B6F6)
        else -> Color(0xFFFF5252)
    }
    drawCircle(color, 10f, Offset(cx + kotlin.math.cos(angle) * radius, cy + kotlin.math.sin(angle) * radius))
}

@Composable
private fun EntityCard(entity: EntityRow, selected: Boolean = false, onClick: () -> Unit = {}) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    )) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row { Text(entity.name, Modifier.weight(1f)); Text(entity.kind) }
            val max = entity.maxHp ?: 0
            val progress = if (max > 0) (entity.hp ?: 0).toFloat() / max else 0f
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
            Text("HP ${entity.hp ?: "?"}/${entity.maxHp ?: "?"}", style = MaterialTheme.typography.bodySmall)
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
