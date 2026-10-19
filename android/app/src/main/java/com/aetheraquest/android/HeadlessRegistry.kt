package com.aetheraquest.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process bridge between [HeadlessService] and the Activity. The service and
 * the UI run in the same process, so a plain singleton is enough; nothing here
 * is persisted, and the status is cleared when the session ends.
 */
object HeadlessRegistry {
    private val current = MutableStateFlow<ConnectionStatus?>(null)
    val status: StateFlow<ConnectionStatus?> = current.asStateFlow()

    fun publish(status: ConnectionStatus) {
        current.value = status
    }

    fun clear() {
        current.value = null
    }
}
