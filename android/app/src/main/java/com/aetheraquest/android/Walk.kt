package com.aetheraquest.android

/** AQW map coordinates are normalized to a 0..1000 square. */
internal const val MAP_MIN = 0
internal const val MAP_MAX = 1000
internal const val DEFAULT_X = 500
internal const val DEFAULT_Y = 400

/**
 * Live AQW joins usually omit `tx`/`ty` on the self entity, so the analog stick
 * needs a fallback origin: the last locally tracked position, else the centre.
 */
internal fun walkOrigin(area: AreaSnapshot, tracked: Pair<Int, Int>? = null): Pair<Int, Int> {
    val self = area.self
    if (self?.x != null && self.y != null) return self.x to self.y
    return tracked ?: (DEFAULT_X to DEFAULT_Y)
}
