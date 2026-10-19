package com.aetheraquest.android

/** Countdown before AQW accepts the timed player-respawn request. */
internal const val PLAYER_RESPAWN_DELAY_MS = 10_000L

/** Wait before retrying a respawn request that could not be written to the socket. */
internal const val RESPAWN_RETRY_BACKOFF_MS = 2_000L

/**
 * Death signal the server actually sends. A `ct` payload with an empty player
 * object (`"p":{"mel e":{}}`) is the authoritative death notice: it arrives in
 * the same packet as the killing blow, before any HP/MP numbers exist, so
 * waiting for `intHP:0` respawns far too late (or never, when the death is
 * never followed by a second `ct`).
 */
const val PLAYER_DEATH_COMMAND = "playerDeath"

data class RespawnState(
    val deathAtMs: Long? = null,
    val sent: Boolean = false,
    val shouldSend: Boolean = false,
    val userId: Int? = null,
    /** Latches once live HP has been seen; a lone `intHP:0` cannot mean death. */
    val seenAlive: Boolean = false,
    /** Respawn packets already written for this death. */
    val attempts: Int = 0,
)

/** Extra gap before repeating `resPlayerTimed` while the avatar stays dead. */
internal const val RESPAWN_RETRY_INTERVAL_MS = 4_000L

/** Cap so a permanently dead session cannot spam the socket. */
internal const val RESPAWN_MAX_ATTEMPTS = 6

/**
 * Advances the player death/respawn latch from server-authoritative state.
 *
 * `playerDeath` is the only signal that means dead. A `ct` entry with an empty
 * player object is a *dodge* — the server sends `"actionResult":{"type":"dodge",
 * "hp":0}` alongside it — and `intHP:0` on its own is ambiguous because the
 * initial snapshot is zero too. Once latched, the ten-second timer runs,
 * `resPlayerTimed` is requested, and it is repeated every few seconds while the
 * avatar stays dead so a lost packet cannot leave the character stuck. Positive
 * HP clears the latch for the next death.
 */
internal fun nextRespawnState(current: RespawnState, area: AreaSnapshot, nowMs: Long): RespawnState {
    val self = area.self ?: return current.copy(shouldSend = false)
    val alive = (self.hp ?: 0) > 0 && (self.state ?: 1) > 0
    // A live sighting always arms the latch, even before max HP is known.
    if (alive) return RespawnState(seenAlive = true)
    // Never latched unless `playerDeath` said so, and never armed by the
    // placeholder zeros of the initial snapshot.
    val proven = self.dead || (current.seenAlive && (self.maxHp ?: 0) > 0)
    if (!proven) return current.copy(shouldSend = false)

    val deathAt = current.deathAtMs ?: nowMs
    val wait = PLAYER_RESPAWN_DELAY_MS + current.attempts * RESPAWN_RETRY_INTERVAL_MS
    val due = self.userId != null &&
        current.attempts < RESPAWN_MAX_ATTEMPTS &&
        nowMs - deathAt >= wait
    return current.copy(
        deathAtMs = deathAt,
        shouldSend = due,
        userId = if (due) self.userId else null,
        seenAlive = true,
    )
}
