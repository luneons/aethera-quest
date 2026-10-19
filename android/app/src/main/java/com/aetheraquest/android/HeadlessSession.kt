package com.aetheraquest.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.Socket

/**
 * A connection owner for headless mode: no Compose state, no rendering, no
 * activity. The service observes [status] and turns it into a notification.
 *
 * It owns its own [GameConnection] so headless mode and the visual screen can
 * never share (and fight over) one socket.
 */
class HeadlessSession(socketFactory: () -> Socket = { Socket() }) {
    private val connection = GameConnection(socketFactory)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Always false: headless mode deliberately has no UI attached. */
    val hasUi: Boolean = false
    val status: StateFlow<ConnectionStatus> = connection.status

    fun start(login: LoginToken, server: AqwServer, map: String) {
        job?.cancel()
        job = scope.launch {
            runCatching { connection.connect(login, server, map) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        connection.close()
        scope.cancel()
    }
}
