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

/**
 * One companion account running without any UI.
 *
 * It owns a private [GameConnection], so a companion can never steal packets or
 * state from the primary account. It follows the primary's map and target, and
 * when its turn comes it casts the taunt consumable.
 */
class CompanionWorker(
    private val account: AuxiliaryAccount,
    private val socketFactory: () -> Socket = { Socket() },
) {
    private val connection = GameConnection(socketFactory)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _status = MutableStateFlow(
        CompanionStatus(id = account.id, username = account.username, role = account.role),
    )
    val status: StateFlow<CompanionStatus> = _status.asStateFlow()

    val id: Int get() = account.id
    val isInMap: Boolean get() = connection.status.value.phase == ConnectionPhase.IN_MAP
    val area: AreaSnapshot get() = connection.status.value.area

    /**
     * Logs in and joins [map]. The token is obtained here rather than reused from
     * the primary: each account needs its own session token.
     */
    fun start(server: AqwServer, map: String) {
        job?.cancel()
        publish(CompanionPhase.CONNECTING, "logging in")
        job = scope.launch {
            runCatching {
                val token = AqwApi.login(account.username, account.password)
                connection.connect(token, server, map)
            }.onFailure { error ->
                // The message never contains the password: only the failure text.
                publish(CompanionPhase.ERROR, error.message ?: "login failed")
            }
        }
    }

    /** Moves to the primary's map, or joins it when the companion is elsewhere. */
    fun followMap(map: String) {
        if (!isInMap) return
        val current = connection.status.value.area.mapName
        if (current.equals(map, ignoreCase = true)) return
        scope.launch {
            runCatching { connection.transfer(map, timeoutMs = 20_000) }
                .onFailure { publish(CompanionPhase.ERROR, it.message ?: "transfer failed") }
        }
    }

    /**
     * Casts the taunt consumable at [monsterMapId].
     *
     * `i1` carries the scroll's ItemID; without it the server silently ignores
     * the cast, which is exactly the bug this path must not reintroduce.
     */
    suspend fun taunt(monsterMapId: Int): Result<Unit> {
        if (!isInMap) return Result.failure(IllegalStateException("not in a map"))
        if (account.consumableItemId <= 0) {
            return Result.failure(IllegalStateException("no taunt consumable configured"))
        }
        // Refuse before casting when the scroll is not actually owned: a wrong
        // ItemID is silently ignored by the server, which looks like "the taunt
        // feature is broken" instead of "the ID is wrong".
        val check = checkConsumable(account.consumableItemId, connection.status.value.inventory)
        if (check is ConsumableCheck.NotOwned) {
            publish(CompanionPhase.ERROR, consumableStatusText(check))
            return Result.failure(IllegalStateException(consumableStatusText(check)))
        }
        publish(CompanionPhase.TAUNTING, "taunt on m:$monsterMapId")
        return runCatching {
            connection.cast(monsterMapId, "i1", itemId = account.consumableItemId)
        }.onFailure { publish(CompanionPhase.ERROR, it.message ?: "taunt failed") }
    }

    /** Keeps the companion's own status in sync with its socket. */
    fun observe() {
        scope.launch {
            connection.status.collect { status ->
                when (status.phase) {
                    ConnectionPhase.IN_MAP -> publish(CompanionPhase.IN_MAP, status.area.mapName ?: "in map")
                    ConnectionPhase.DISCONNECTED -> publish(CompanionPhase.STOPPED, status.message)
                    else -> publish(CompanionPhase.CONNECTING, status.message)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        connection.close()
        scope.cancel()
        _status.value = _status.value.copy(phase = CompanionPhase.STOPPED, detail = "stopped")
    }

    private fun publish(phase: CompanionPhase, detail: String) {
        _status.value = _status.value.copy(phase = phase, detail = detail)
    }
}
