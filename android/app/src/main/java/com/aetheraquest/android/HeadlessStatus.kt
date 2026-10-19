package com.aetheraquest.android

/**
 * Text shown for a headless session. Kept free of Android types so the
 * formatting rules are testable on the JVM.
 */
data class HeadlessNotification(
    val title: String,
    val text: String,
    val ongoing: Boolean,
)

/**
 * Headless mode keeps the socket alive without any screen, so the notification
 * is the only status surface: it must name the map and room instead of a
 * generic "running" label, and it must never claim a monster count of zero.
 */
fun headlessNotification(status: ConnectionStatus): HeadlessNotification {
    val area = status.area
    val map = area.mapName
    if (status.phase != ConnectionPhase.IN_MAP) {
        return HeadlessNotification(
            title = "Aethera Quest",
            text = status.message,
            ongoing = status.phase != ConnectionPhase.DISCONNECTED,
        )
    }
    val room = area.roomId
    val monsters = area.monsters.size
    val detail = buildList {
        room?.let { add("room $it") }
        if (monsters > 0) add("$monsters monster${if (monsters == 1) "" else "s"}")
    }.joinToString(" · ")
    return HeadlessNotification(
        title = map ?: "In map",
        text = detail.ifEmpty { "In map" },
        ongoing = true,
    )
}

/** Initial StateFlow emits DISCONNECTED before the connection coroutine starts. */
fun shouldStopHeadless(status: ConnectionStatus, hasConnected: Boolean = false): Boolean =
    hasConnected && status.phase == ConnectionPhase.DISCONNECTED
