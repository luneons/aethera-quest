package com.aetheraquest.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Joystick walk cadence and per-tick step, in AQW map units. */
internal const val WALK_TICK_MS = 220L
internal const val WALK_STEP = 55
internal const val RUN_STEP = 110
/** How often the auto-attack loop fires. 600 ms matches a human mash cadence. */
internal const val AUTO_TICK_MS = 600L

data class UiState(
    val screen: Screen = Screen.LOGIN,
    val username: String = "",
    val password: String = "",
    val servers: List<AqwServer> = emptyList(),
    val selectedServer: AqwServer? = null,
    val mapName: String = "",
    val status: ConnectionStatus = ConnectionStatus(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedMonsterId: Int? = null,
    val startHeadless: HeadlessLaunch? = null,
    val headlessLaunch: HeadlessLaunch? = null,
    val headlessStatus: ConnectionStatus = ConnectionStatus(),
    val running: Boolean = false,
    val trackedPosition: Pair<Int, Int>? = null,
    /** True while the auto-attack loop is firing. */
    val autoAttacking: Boolean = false,
    /** Current class name from `updateClass` / `loadInventoryBig`. */
    val className: String = "",
    /** Live skill table from `sAct`. */
    val skills: Map<String, SkillInfo> = emptyMap(),
    /** Whether an encrypted login is currently stored on this device. */
    val credentialsSaved: Boolean = false,
    /** User choice exposed by the Remember login checkbox. */
    val rememberCredentials: Boolean = false,
    /** Full-screen combat control layout. Back closes it. */
    val combatMode: Boolean = false,
    /**
     * Absolute time each combat ref becomes usable again, derived from the live
     * `sAct` cooldown of that class skill. The UI counts down from this map so
     * the number on a button is the same cooldown the button obeys.
     */
    val skillReadyAt: Map<String, Long> = emptyMap(),
    /** Everything the character owns, from `loadInventoryBig`. */
    val inventory: List<OwnedItem> = emptyList(),
)

enum class Screen { LOGIN, SERVER, JOIN, MAP, HEADLESS }

internal fun screenForConnectionResult(current: Screen, status: ConnectionStatus): Screen =
    if (status.phase == ConnectionPhase.IN_MAP) Screen.MAP else current

/**
 * Picks the monster an attack should be sent to. The selection is only a
 * preference: a dead or vanished selection falls back to the first living
 * monster so the button never sends a `gar` for a corpse.
 */
internal fun resolveAttackTarget(area: AreaSnapshot, selected: Int?): Int? {
    selected?.let { id -> area.monsters[id]?.takeIf { it.alive }?.let { return it.mapId } }
    return area.monsters.values.firstOrNull { it.alive }?.mapId
}

data class HeadlessLaunch(val login: LoginToken, val server: AqwServer, val map: String)

/**
 * Validates the inputs headless mode needs before a foreground service is
 * started, so an incomplete selection cannot leave an empty service running.
 */
internal fun headlessLaunchFor(state: UiState, login: LoginToken?): HeadlessLaunch? {
    val server = state.selectedServer ?: return null
    val map = state.mapName.trim()
    if (map.isEmpty()) return null
    return HeadlessLaunch(login ?: return null, server, map)
}

internal fun headlessUiAfterServiceStart(state: UiState, launch: HeadlessLaunch): UiState = state.copy(
    screen = Screen.HEADLESS,
    startHeadless = null,
    headlessLaunch = launch,
    headlessStatus = ConnectionStatus(ConnectionPhase.CONNECTING, "Starting headless mode…"),
    isLoading = true,
    error = null,
)

internal fun headlessUiFromStatus(state: UiState, status: ConnectionStatus): UiState {
    val failed = status.phase == ConnectionPhase.DISCONNECTED && status.message != "Disconnected"
    return state.copy(
        screen = Screen.HEADLESS,
        headlessStatus = status,
        isLoading = status.phase != ConnectionPhase.IN_MAP && status.phase != ConnectionPhase.DISCONNECTED,
        error = if (failed) status.message else null,
    )
}

internal fun headlessUiAfterStop(state: UiState): UiState = state.copy(
    screen = Screen.JOIN,
    startHeadless = null,
    headlessLaunch = null,
    headlessStatus = ConnectionStatus(),
    isLoading = false,
    error = null,
)

class AetheraQuestViewModel : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val connection = GameConnection()
    private var loginToken: LoginToken? = null
    private var credentialStore: AndroidCredentialStore? = null
    private var respawnState = RespawnState()
    private var respawnJob: Job? = null

    init {
        viewModelScope.launch {
            connection.status.collect { status ->
                _ui.value = _ui.value.copy(
                    status = status,
                    screen = if (status.phase == ConnectionPhase.IN_MAP) Screen.MAP else _ui.value.screen,
                    isLoading = status.phase != ConnectionPhase.IN_MAP &&
                        status.phase != ConnectionPhase.DISCONNECTED && !status.transferring,
                    error = if (status.phase == ConnectionPhase.DISCONNECTED &&
                        status.message != "Disconnected" &&
                        _ui.value.screen == Screen.MAP
                    ) status.message else _ui.value.error,
                    className = status.className.ifBlank { _ui.value.className },
                    skills = status.skills.ifEmpty { _ui.value.skills },
                    inventory = status.inventory.ifEmpty { _ui.value.inventory },
                )
            }
        }
        respawnJob = viewModelScope.launch {
            while (isActive) {
                val current = _ui.value
                if (current.status.phase != ConnectionPhase.IN_MAP) {
                    respawnState = RespawnState()
                    delay(500)
                    continue
                }
                respawnState = nextRespawnState(respawnState, current.status.area, System.currentTimeMillis())
                if (respawnState.shouldSend) {
                    val uid = respawnState.userId
                    if (uid != null) {
                        val ok = runCatching { connection.respawnPlayer(uid) }.isSuccess
                        // Count the attempt either way: a write that throws must
                        // not retry forever, and a write that succeeds must not
                        // be repeated on the next tick.
                        respawnState = respawnState.copy(
                            shouldSend = false,
                            attempts = respawnState.attempts + 1,
                            sent = respawnState.sent || ok,
                        )
                    }
                }
                delay(500)
            }
        }
    }

    fun updateUsername(v: String) { _ui.value = _ui.value.copy(username = v) }
    fun updatePassword(v: String) { _ui.value = _ui.value.copy(password = v) }
    fun updateMapName(v: String) { _ui.value = _ui.value.copy(mapName = v) }
    fun updateRememberCredentials(value: Boolean) {
        _ui.value = _ui.value.copy(rememberCredentials = value)
        // Turning it off means "do not remember me" immediately, including an
        // account restored from a previous run.
        if (!value && _ui.value.credentialsSaved) clearSavedCredentials()
    }

    /**
     * Attach device-local encrypted storage and restore its login once.
     * Context is held only by the store's applicationContext, never an Activity.
     */
    fun attachCredentialStore(store: AndroidCredentialStore) {
        if (credentialStore != null) return
        credentialStore = store
        val restored = restoreSavedCredentials(_ui.value, runCatching { store.load() }.getOrNull())
        _ui.value = restored.copy(rememberCredentials = restored.credentialsSaved)
    }

    private fun applyRememberChoice(username: String, password: String) {
        val store = credentialStore
        when (val action = loginCredentialAction(
            remember = _ui.value.rememberCredentials,
            username = username,
            password = password,
            currentlySaved = _ui.value.credentialsSaved,
        )) {
            is LoginCredentialAction.Save -> {
                if (store != null && runCatching { store.save(action.credentials) }.isSuccess) {
                    _ui.value = _ui.value.copy(credentialsSaved = true)
                }
            }
            LoginCredentialAction.Clear -> if (store != null && runCatching { store.clear() }.isSuccess) {
                _ui.value = _ui.value.copy(credentialsSaved = false)
            }
            LoginCredentialAction.None -> Unit
        }
    }

    fun clearSavedCredentials() {
        val store = credentialStore ?: return
        runCatching { store.clear() }
            .onSuccess {
                _ui.value = _ui.value.copy(
                    credentialsSaved = false,
                    rememberCredentials = false,
                    error = null,
                )
            }
            .onFailure { _ui.value = _ui.value.copy(error = "Could not remove saved login") }
    }

    fun selectServer(server: AqwServer) {
        _ui.value = _ui.value.copy(selectedServer = server)
    }

    fun loadServers() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            try {
                val list = AqwApi.servers().filter { it.online && !it.upgradeOnly }
                _ui.value = _ui.value.copy(servers = list, isLoading = false)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(isLoading = false, error = "Server list: ${e.message}")
            }
        }
    }

    fun login() {
        viewModelScope.launch {
            val u = _ui.value
            if (u.username.isBlank() || u.password.isBlank()) {
                _ui.value = _ui.value.copy(error = "Username and password required")
                return@launch
            }
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            try {
                loginToken = AqwApi.login(u.username.trim(), u.password)
                applyRememberChoice(u.username, u.password)
                val list = AqwApi.servers().filter { it.online && !it.upgradeOnly }
                _ui.value = _ui.value.copy(screen = Screen.SERVER, servers = list)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "Login failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(isLoading = false, password = "")
            }
        }
    }

    fun proceedToJoin() {
        _ui.value = _ui.value.copy(screen = Screen.JOIN)
    }

    fun goToMap() {
        viewModelScope.launch {
            val u = _ui.value
            val server = u.selectedServer
            if (server == null) { _ui.value = _ui.value.copy(error = "Select a server"); return@launch }
            if (u.mapName.isBlank()) { _ui.value = _ui.value.copy(error = "Enter a map name"); return@launch }
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            tracked = null
            _ui.value = _ui.value.copy(trackedPosition = null)
            try {
                val token = loginToken ?: error("Login session expired")
                connection.connect(token, server, u.mapName.trim())
                _ui.value = _ui.value.copy(
                    screen = screenForConnectionResult(_ui.value.screen, connection.status.value),
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message ?: "Connection failed")
            } finally {
                _ui.value = _ui.value.copy(isLoading = false)
            }
        }
    }

    fun startHeadless() {
        val launch = headlessLaunchFor(_ui.value, loginToken)
        if (launch == null) {
            _ui.value = _ui.value.copy(error = "Select a server and enter a map name")
            return
        }
        connection.close()
        _ui.value = _ui.value.copy(startHeadless = launch, error = null, isLoading = false)
    }

    fun clearHeadlessLaunch(message: String) {
        _ui.value = _ui.value.copy(startHeadless = null, error = message)
    }

    fun consumeHeadlessLaunch() {
        val launch = _ui.value.startHeadless ?: return
        _ui.value = headlessUiAfterServiceStart(_ui.value, launch)
    }

    fun updateHeadlessStatus(status: ConnectionStatus) {
        if (_ui.value.screen == Screen.HEADLESS) {
            _ui.value = headlessUiFromStatus(_ui.value, status)
        }
    }

    fun stopHeadlessUi() {
        _ui.value = headlessUiAfterStop(_ui.value)
    }

    fun move(x: Int, y: Int) {
        tracked = x to y
        _ui.value = _ui.value.copy(trackedPosition = tracked)
        viewModelScope.launch { runCatching { connection.move(x, y) } }
    }

    /** Latest analog vector; the walk loop reads it on every tick. */
    private var stickX = 0f
    private var stickY = 0f
    private var walkJob: Job? = null
    private var tracked: Pair<Int, Int>? = null

    fun walk(dx: Float, dy: Float) {
        stickX = dx
        stickY = dy
        if (walkJob?.isActive == true) return
        walkJob = viewModelScope.launch {
            while (isActive) {
                val area = _ui.value.status.area
                if (_ui.value.status.phase != ConnectionPhase.IN_MAP) { delay(WALK_TICK_MS); continue }
                val step = if (_ui.value.running) RUN_STEP else WALK_STEP
                val origin = walkOrigin(area, tracked)
                val target = joystickTarget(origin.first, origin.second, stickX, stickY, step)
                if (target != null) {
                    tracked = target
                    _ui.value = _ui.value.copy(trackedPosition = target)
                    runCatching { connection.move(target.first, target.second) }
                }
                delay(WALK_TICK_MS)
            }
        }
    }

    /**
     * Jump to a cell the server has already revealed. `mv` can never change
     * cell, and the map's trigger geometry is not available, so an explicit
     * destination is the only honest way to move between cells.
     */
    fun jumpToCell(cell: String, pad: String = "Left") {
        if (cell.isBlank()) return
        stopWalking()
        tracked = null
        _ui.value = _ui.value.copy(trackedPosition = null)
        viewModelScope.launch {
            runCatching { connection.moveToCell(cell.trim(), pad.trim().ifEmpty { "Left" }) }
                .onFailure { _ui.value = _ui.value.copy(error = it.message ?: "Cell jump failed") }
        }
    }

    fun stopWalking() {
        stickX = 0f
        stickY = 0f
        walkJob?.cancel()
        walkJob = null
    }

    fun toggleRunning() {
        _ui.value = _ui.value.copy(running = !_ui.value.running)
    }

    fun openCombatMode() {
        _ui.value = _ui.value.copy(combatMode = combatModeAfterOpen(_ui.value.combatMode))
    }

    fun closeCombatMode() {
        _ui.value = _ui.value.copy(combatMode = combatModeAfterBack(_ui.value.combatMode))
    }

    /**
     * Equips one owned item and then re-requests the inventory.
     *
     * The server's immediate `updateClass` proves a class swap before a full
     * inventory refresh arrives. The UI still marks the selected item locally
     * so the dropdown header cannot stay on the old class while combat has
     * already switched rotations.
     */
    fun equipItem(item: OwnedItem) {
        val state = _ui.value
        if (state.status.phase != ConnectionPhase.IN_MAP) {
            _ui.value = state.copy(error = "Enter a map first")
            return
        }
        if (item.slot == null) {
            _ui.value = state.copy(error = "${item.name} cannot be equipped")
            return
        }
        viewModelScope.launch {
            runCatching { connection.equip(item.itemId) }
                .onSuccess {
                    val optimistic = applyEquipped(_ui.value.inventory, item)
                    _ui.value = _ui.value.copy(
                        inventory = optimistic,
                        className = if (item.slot == EquipmentSlot.CLASS) item.name else _ui.value.className,
                        error = null,
                    )
                    val uid = _ui.value.status.area.self?.userId
                    if (uid != null) {
                        // The UI remains responsive even when live servers do
                        // not answer retrieveInventory after every equip.
                        runCatching { connection.refreshInventory(uid) }
                    }
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.message ?: "Equip failed") }
        }
    }

    /** Owned items for one dropdown, with the currently worn one first. */
    fun itemsFor(slot: EquipmentSlot): List<OwnedItem> =
        equipmentBySlot(_ui.value.inventory, slot)
            .sortedWith(compareByDescending<OwnedItem> { it.equipped }.thenBy { it.name.lowercase() })

    fun manualSkill(index: Int) {
        val state = _ui.value
        val action = manualCombatAction(state.status.area, state.selectedMonsterId, index)
        if (action == null) {
            _ui.value = state.copy(error = "No living monster selected")
            return
        }
        val skills = state.status.skills.ifEmpty { state.skills }
        val readiness = manualSkillReadiness(
            index, skills, state.skillReadyAt, System.currentTimeMillis(), state.status.area.self?.mp,
        )
        if (!readiness.enabled) {
            _ui.value = state.copy(error = readiness.reason ?: "A$index is not ready")
            return
        }
        when (action) {
            is ManualCombatAction.Cast -> viewModelScope.launch {
                val sentAt = System.currentTimeMillis()
                runCatching { connection.cast(action.mapId, action.ref) }
                    .onSuccess {
                        // Start the countdown from the class's own `cd`, so the
                        // number shown is exactly what the server will enforce.
                        val cd = skills[action.ref]?.cooldownMs ?: 0L
                        _ui.value = _ui.value.copy(
                            skillReadyAt = _ui.value.skillReadyAt + (action.ref to sentAt + cd),
                            error = null,
                        )
                    }
                    .onFailure { _ui.value = _ui.value.copy(error = it.message ?: "Skill failed") }
            }
        }
    }

    fun moveToCell(cell: String, pad: String = "Spawn") {
        if (cell.isBlank()) return
        viewModelScope.launch {
            runCatching { connection.moveToCell(cell.trim(), pad.trim().ifEmpty { "Spawn" }) }
                .onFailure { _ui.value = _ui.value.copy(error = it.message ?: "Cell move failed") }
        }
    }

    fun transferToMap(map: String) {
        val target = map.trim()
        if (target.isEmpty()) return
        if (_ui.value.status.phase != ConnectionPhase.IN_MAP) {
            _ui.value = _ui.value.copy(error = "Enter the current map first")
            return
        }
        viewModelScope.launch {
            runCatching { connection.transfer(target) }
                .onSuccess {
                    // Map coordinates are per-room; a stale position would walk
                    // the character from the previous map's layout.
                    stopWalking()
                    tracked = null
                    _ui.value = _ui.value.copy(
                        trackedPosition = null,
                        selectedMonsterId = null,
                        error = null,
                    )
                }
                .onFailure {
                    _ui.value = _ui.value.copy(
                        error = it.message ?: "Failed to switch map",
                        isLoading = false,
                    )
                }
        }
    }

    fun selectMonster(mapId: Int?) {
        _ui.value = _ui.value.copy(selectedMonsterId = mapId)
        // A new target restarts the rotation, matching the reference client.
        autoState = autoState.copy(targetMapId = mapId, index = 0)
    }

    private var autoState = AutoAttackState()
    private var autoJob: Job? = null
    private var lastUsed = mutableMapOf<String, Long>()
    private var lastTickAt = 0L

    /** True when the attack loop should keep firing. */
    fun toggleAutoAttack() {
        if (_ui.value.autoAttacking) stopAutoAttack() else startAutoAttack()
    }

    fun startAutoAttack() {
        val state = _ui.value
        if (state.status.phase != ConnectionPhase.IN_MAP) {
            _ui.value = state.copy(error = "Enter a map first")
            return
        }
        if (resolveAttackTarget(state.status.area, state.selectedMonsterId) == null) {
            _ui.value = state.copy(error = "No living monster in this map")
            return
        }
        _ui.value = state.copy(autoAttacking = true, error = null)
        autoState = autoState.copy(enabled = true, index = 0)
        lastUsed.clear()
        lastTickAt = 0L
        autoJob?.cancel()
        autoJob = viewModelScope.launch {
            while (isActive) {
                tickAutoAttack()
                delay(AUTO_TICK_MS)
            }
        }
    }

    fun stopAutoAttack() {
        autoJob?.cancel()
        autoJob = null
        autoState = autoState.copy(enabled = false, index = 0)
        _ui.value = _ui.value.copy(autoAttacking = false)
    }

    /**
     * One auto-attack step: pick a living target, decide the next skill from
     * the class rotation and the live `sAct` table, then send it.
     *
     * The loop never waits for a skill to become available — it sends whatever
     * is legal now, which is what keeps damage flowing during cooldowns.
     */
    private suspend fun tickAutoAttack() {
        val state = _ui.value
        if (state.status.phase != ConnectionPhase.IN_MAP) {
            stopAutoAttack()
            return
        }
        val area = state.status.area
        val target = resolveAttackTarget(area, state.selectedMonsterId)
        if (target == null) {
            // Every monster is dead: keep the loop alive so respawns are picked
            // up automatically instead of forcing the user to press again.
            return
        }
        val className = state.status.className.ifBlank { state.className }
        val skills = state.status.skills.ifEmpty { state.skills }
        val rotation = rotationFor(className)
        val now = System.currentTimeMillis()

        val decision = nextAutoAction(
            state = autoState.copy(className = className, targetMapId = target),
            rotation = rotation,
            skills = skills,
            nowMs = now,
            lastUsed = lastUsed,
            mana = state.status.area.self?.mp ?: Int.MAX_VALUE,
            targetMapId = target,
            previousClassName = autoState.className.takeIf { it.isNotBlank() },
        ) ?: return

        // Respect the action's own cooldown so the loop does not spam a skill
        // the server will reject; `aa` keeps the swing going in between.
        val info = skills[decision.ref]
        val readyAt = (lastUsed[decision.ref] ?: 0L) + (info?.cooldownMs ?: 0L)
        if (now < readyAt) {
            val fallbackReadyAt = (lastUsed["aa"] ?: 0L) + (skills["aa"]?.cooldownMs ?: 0L)
            if (now < fallbackReadyAt) return
            sendCombat("aa", target, now)
            return
        }
        sendCombat(decision.ref, target, now)
        autoState = autoState.copy(
            className = className,
            targetMapId = target,
            index = decision.nextIndex,
        )
    }

    private suspend fun sendCombat(ref: String, target: Int, now: Long) {
        runCatching { connection.cast(target, ref) }
            .onSuccess { lastUsed[ref] = now }
            .onFailure { _ui.value = _ui.value.copy(error = it.message ?: "Attack failed") }
    }

    fun attack() {
        val state = _ui.value
        val plan = combatPlan(state.status.area, state.selectedMonsterId)
        if (plan == null) {
            _ui.value = state.copy(error = "No living monster in this map")
            return
        }
        _ui.value = state.copy(selectedMonsterId = plan.targetMapId, error = null)
        viewModelScope.launch {
            if (plan.moveFirst) {
                runCatching { connection.moveToCell(plan.cell, plan.pad) }
                    .onFailure { _ui.value = _ui.value.copy(error = it.message ?: "Cell move failed") }
                    .onSuccess { delay(600) }
            }
            runCatching { connection.attack(plan.targetMapId) }
                .onFailure { _ui.value = _ui.value.copy(error = it.message ?: "Attack failed") }
        }
    }

    fun sendChat(message: String, channel: String = "zone") {
        if (message.isBlank()) return
        viewModelScope.launch {
            runCatching { connection.sendChat(message.trim(), channel) }
                .onFailure { _ui.value = _ui.value.copy(error = it.message ?: "Chat failed") }
        }
    }

    fun toggleAfk() {
        val next = !_ui.value.status.afk
        viewModelScope.launch {
            runCatching { connection.setAfk(next) }
                .onFailure { _ui.value = _ui.value.copy(error = it.message ?: "AFK toggle failed") }
        }
    }

    fun disconnect() {
        stopWalking()
        tracked = null
        viewModelScope.launch { connection.close() }
        loginToken = null
        _ui.value = _ui.value.copy(screen = Screen.LOGIN, password = "", trackedPosition = null)
    }

    fun clearError() { _ui.value = _ui.value.copy(error = null) }
    override fun onCleared() { connection.close() }
}
