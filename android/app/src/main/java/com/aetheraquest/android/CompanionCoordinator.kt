package com.aetheraquest.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Socket

/** How often the coordinator re-evaluates the rotation. */
internal const val TAUNT_TICK_MS = 400L

/**
 * Runs the companion accounts and keeps a taunt rotation going.
 *
 * Only the primary account has a UI. Companions are pure workers: they log in,
 * follow the primary's map, and taunt on their turn. Every decision is taken by
 * the pure functions in [TauntLoop] so the timing is testable without a socket.
 */
class CompanionCoordinator(
    private val socketFactory: () -> Socket = { Socket() },
    private val timing: TauntTiming = TauntTiming(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workers = linkedMapOf<Int, CompanionWorker>()
    private val _statuses = MutableStateFlow<List<CompanionStatus>>(emptyList())
    val statuses: StateFlow<List<CompanionStatus>> = _statuses.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var loopJob: Job? = null
    private var rotation = TauntRotation()
    private var lastMap: String? = null

    /**
     * Starts every enabled companion and begins the rotation.
     *
     * Accounts with a blank password or no consumable for a taunter role are
     * skipped instead of being started into a guaranteed failure.
     */
    fun start(accounts: List<AuxiliaryAccount>, server: AqwServer, map: String) {
        stop()
        val usable = accounts.filter { it.enabled && it.username.isNotBlank() && it.password.isNotBlank() }
        if (usable.isEmpty()) {
            _statuses.value = emptyList()
            return
        }
        usable.forEach { account ->
            val worker = CompanionWorker(account, socketFactory)
            workers[account.id] = worker
            worker.observe()
            worker.start(server, map)
        }
        lastMap = map
        rotation = TauntRotation()
        _running.value = true
        refreshStatuses()
        loopJob = scope.launch { runLoop() }
    }

    /**
     * The rotation loop.
     *
     * Each tick: mirror the primary's map, read its live area for a target, ask
     * [nextTaunt] whose turn it is, then cast and record the result. A failed cast
     * is recorded too, so a broken account cannot be retried forever in the same
     * tick and stall the other taunter.
     */
    private suspend fun runLoop() {
        while (scope.isActive) {
            val primaryMap = lastMap
            if (primaryMap == null || workers.isEmpty()) {
                delay(TAUNT_TICK_MS)
                continue
            }
            workers.values.forEach { it.followMap(primaryMap) }
            refreshStatuses()
            delay(TAUNT_TICK_MS)
        }
    }

    /** Called by the ViewModel with the primary's live area every tick. */
    fun onPrimaryArea(area: AreaSnapshot, primaryInMap: Boolean, accounts: List<AuxiliaryAccount>) {
        if (!_running.value) return
        if (!shouldKeepTaunting(area, primaryInMap)) return
        val target = area.monsters.values.firstOrNull { it.alive }?.mapId ?: return
        val now = System.currentTimeMillis()
        val decision = nextTaunt(rotation, accounts, now, timing) ?: return
        val worker = workers[decision.accountId] ?: return
        rotation = recordTaunt(rotation, decision, now)
        scope.launch {
            worker.taunt(target)
            refreshStatuses()
        }
    }

    /** Updates the map companions should follow. */
    fun followMap(map: String) {
        if (map.isBlank() || map == lastMap) return
        lastMap = map
        workers.values.forEach { it.followMap(map) }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        workers.values.forEach { it.stop() }
        workers.clear()
        rotation = TauntRotation()
        lastMap = null
        _running.value = false
        _statuses.value = emptyList()
    }

    private fun refreshStatuses() {
        _statuses.value = workers.values.map { it.status.value }
    }
}
