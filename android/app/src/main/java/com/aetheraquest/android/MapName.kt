package com.aetheraquest.android

/**
 * Map names go straight into a `%`-delimited packet, so anything that could
 * break framing or inject a second command is rejected before sending.
 */
internal fun mapNameError(raw: String): String? {
    val name = raw.trim()
    return when {
        name.isEmpty() -> "Map name is required"
        name.any { it.isWhitespace() } -> "Map name cannot contain spaces"
        name.any { it == '%' || it == '\u0000' } -> "Map name contains an illegal character"
        else -> null
    }
}

internal fun normalizedMapName(raw: String): String = raw.trim()
