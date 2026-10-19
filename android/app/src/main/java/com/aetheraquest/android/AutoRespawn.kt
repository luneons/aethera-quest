package com.aetheraquest.android

/** Countdown before AQW accepts the timed player-respawn request. */
internal const val PLAYER_RESPAWN_DELAY_MS = 10_000L

/** Wait before retrying a respawn request that could not be written to the socket. */
internal const val RESPAWN_RETRY_BACKOFF_MS = 2_000L

data class RespawnState(
    val deathAtMs: Long? = null,
    val sent: Boolean = false,
    val shouldSend: Boolean = false,
    val userId: Int? = null,
    /** Latches once live HP has been seen; a lone `intHP:0` cannot mean death. */
    val seenAlive: Boolean = false,
)

/**
 * Advances the player death/respawn latch from server-authoritative HP state.
 *
 * `ct` packets for the player usually carry only `intHP`, so a zero alone is
 * ambiguous: the snapshot default is also zero. Death therefore requires proof
 * of a live character first (a positive HP sighting or a known max HP). After
 * that, zero HP/state starts a ten-second timer, exactly one `resPlayerTimed`
 * is requested per death cycle, and positive HP clears the latch.
 */
internal fun nextRespawnState(current: RespawnState, area: AreaSnapshot, nowMs: Long): RespawnState {
    val self = area.self ?: return current.copy(shouldSend = false)
    val hp = self.hp
    val alive = (hp ?: 0) > 0 && (self.state ?: 1) > 0
    // A live sighting always arms the latch, even before max HP is known.
    if (alive) return RespawnState(seenAlive = true)
    val proven = current.seenAlive || (self.maxHp ?: 0) > 0
    if (!proven) return current.copy(shouldSend = false)

    val deathAt = current.deathAtMs ?: nowMs
    val due = !current.sent && self.userId != null && nowMs - deathAt >= PLAYER_RESPAWN_DELAY_MS
    return current.copy(
        deathAtMs = deathAt,
        shouldSend = due,
        userId = if (due) self.userId else null,
        seenAlive = true,
    )
}
