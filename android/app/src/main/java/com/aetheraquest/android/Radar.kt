package com.aetheraquest.android

/** Where a position came from, so the UI never implies more certainty than it has. */
enum class PositionSource { SERVER, LOCAL }

data class MapPosition(val x: Int, val y: Int, val source: PositionSource) {
    val confirmed: Boolean get() = source == PositionSource.SERVER
}

data class RadarPoint(val x: Float, val y: Float)

/**
 * AQW map coordinates are a normalized 0..1000 square, so a point maps
 * linearly onto the radar. The server is the only authority for the exact
 * position; a locally tracked value is shown but marked as unconfirmed.
 */
internal fun radarProject(x: Int?, y: Int?): RadarPoint? {
    if (x == null || y == null) return null
    val nx = (x.toFloat() / MAP_MAX).coerceIn(0f, 1f)
    val ny = (y.toFloat() / MAP_MAX).coerceIn(0f, 1f)
    return RadarPoint(nx, ny)
}

/**
 * The live server often never reports `tx`/`ty`, so a locally predicted
 * position is shown as a fallback — but only a server-reported coordinate is
 * labelled as confirmed.
 */
internal fun visibleMapPosition(area: AreaSnapshot, tracked: Pair<Int, Int>?): MapPosition? {
    val self = area.self
    if (self?.x != null && self.y != null && area.selfPositionServerObserved) {
        return MapPosition(self.x, self.y, PositionSource.SERVER)
    }
    if (tracked != null) return MapPosition(tracked.first, tracked.second, PositionSource.LOCAL)
    if (self?.x != null && self.y != null) return MapPosition(self.x, self.y, PositionSource.LOCAL)
    return null
}
