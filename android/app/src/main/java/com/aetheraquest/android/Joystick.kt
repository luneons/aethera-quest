package com.aetheraquest.android

import kotlin.math.roundToInt

/** Converts a normalized joystick vector into the next in-map destination. */
internal fun joystickTarget(
    x: Int,
    y: Int,
    dx: Float,
    dy: Float,
    step: Int = 60,
    multiplier: Float = 1f,
    deadZone: Float = 0.14f,
): Pair<Int, Int>? {
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < deadZone) return null
    val nx = (dx / length).coerceIn(-1f, 1f)
    val ny = (dy / length).coerceIn(-1f, 1f)
    val magnitude = length.coerceAtMost(1f) * multiplier.coerceAtLeast(0f)
    val nextX = (x + nx * step * magnitude).roundToInt().coerceIn(MAP_MIN, MAP_MAX)
    val nextY = (y + ny * step * magnitude).roundToInt().coerceIn(MAP_MIN, MAP_MAX)
    if (nextX == x && nextY == y) return null
    return nextX to nextY
}
