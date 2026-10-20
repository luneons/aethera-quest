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
 * Companion attack cadence. Slower than the primary's 600 ms on purpose: a
 * companion that floods `gar` packets delays its own taunt cast, which is the one
 * action that must never be late.
 */
internal const val COMPANION_ATTACK_INTERVAL_MS = 1_500L

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
    private val lastAttackAt = mutableMapOf<Int, Long>()
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
        val primary = area.self
        // First stack every companion in the primary's known cell. This is a
        // prerequisite, not a side effect of taunting.
        workers.values.forEach { it.followCell(primary?.cell, primary?.pad) }
        if (!shouldKeepTaunting(area, primaryInMap)) return
        val target = tauntTarget(area, null) ?: return
        // Never burn a scroll until the primary itself is already in the target
        // monster's cell. The primary is the source of truth for the fight spot.
        if (!primaryReadyToTaunt(area, target)) return
        val now = System.currentTimeMillis()
        val decision = nextTaunt(rotation, accounts, now, timing)

        // A taunt that is about to land outranks every attack: pause the
        // companions' damage briefly so the scroll cannot be pushed past the
        // Focus expiry by the packet burst.
        if (!shouldHoldAttacks(rotation, now, timing)) {
            attackAll(target, accounts, skipAccountId = decision?.accountId)
        }

        if (decision == null) return
        val worker = workers[decision.accountId] ?: return
        if (!worker.isInCell(primary?.cell)) return
        rotation = recordTaunt(rotation, decision, now)
        scope.launch {
            worker.taunt(target)
            refreshStatuses()
        }
    }

    /**
     * Keeps every companion contributing damage on its own cadence.
     *
     * Each companion attacks at most once per [COMPANION_ATTACK_INTERVAL_MS], and
     * the one whose taunt just came due is skipped so its scroll cast is never
     * delayed behind an attack packet.
     */
    private fun attackAll(target: Int, accounts: List<AuxiliaryAccount>, skipAccountId: Int?) {
        val now = System.currentTimeMillis()
        accounts.forEach { account ->
            if (account.id == skipAccountId) return@forEach
            val worker = workers[account.id] ?: return@forEach
            if (!worker.isInMap) return@forEach
            val last = lastAttackAt[account.id] ?: 0L
            if (now - last < COMPANION_ATTACK_INTERVAL_MS) return@forEach
            lastAttackAt[account.id] = now
            scope.launch {
                worker.attack(target)
                refreshStatuses()
            }
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
