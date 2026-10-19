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
    fun alive(monster: MonsterState) = monster.hp == null || monster.hp > 0
    selected?.let { id -> area.monsters[id]?.takeIf(::alive)?.let { return it.mapId } }
    return area.monsters.values.firstOrNull(::alive)?.mapId
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
                )
            }
        }
    }

    fun updateUsername(v: String) { _ui.value = _ui.value.copy(username = v) }
    fun updatePassword(v: String) { _ui.value = _ui.value.copy(password = v) }
    fun updateMapName(v: String) { _ui.value = _ui.value.copy(mapName = v) }

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

    fun stopWalking() {
        stickX = 0f
        stickY = 0f
        walkJob?.cancel()
        walkJob = null
    }

    fun toggleRunning() {
        _ui.value = _ui.value.copy(running = !_ui.value.running)
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
