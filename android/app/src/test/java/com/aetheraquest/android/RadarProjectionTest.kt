package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarProjectionTest {
    @Test fun projectsTheCentreToTheCentreOfTheRadar() {
        assertEquals(RadarPoint(.5f, .4f), radarProject(DEFAULT_X, DEFAULT_Y))
    }

    @Test fun projectsAllFourCorners() {
        assertEquals(RadarPoint(0f, 0f), radarProject(MAP_MIN, MAP_MIN))
        assertEquals(RadarPoint(1f, 0f), radarProject(MAP_MAX, MAP_MIN))
        assertEquals(RadarPoint(0f, 1f), radarProject(MAP_MIN, MAP_MAX))
        assertEquals(RadarPoint(1f, 1f), radarProject(MAP_MAX, MAP_MAX))
    }

    @Test fun clampsOutOfRangeCoordinates() {
        assertEquals(RadarPoint(0f, 1f), radarProject(-500, 2500))
    }

    @Test fun aMissingCoordinateCannotBeProjected() {
        assertNull(radarProject(null, 200))
        assertNull(radarProject(200, null))
    }

    @Test fun recordsWhetherPositionIsServerOrClientObserved() {
        val server = MapPosition(100, 200, PositionSource.SERVER)
        val local = MapPosition(300, 400, PositionSource.LOCAL)
        assertTrue(server.confirmed)
        assertFalse(local.confirmed)
    }

    @Test fun serverPositionWinsButTrackedPositionRemainsAVisibleFallback() {
        val serverArea = AreaSnapshot(
            self = PlayerState("mel e", x = 123, y = 456),
            selfPositionServerObserved = true,
        )
        val localArea = AreaSnapshot(
            self = PlayerState("mel e", x = 123, y = 456),
            selfPositionServerObserved = false,
        )
        assertEquals(MapPosition(123, 456, PositionSource.SERVER), visibleMapPosition(serverArea, 700 to 800))
        assertEquals(MapPosition(700, 800, PositionSource.LOCAL), visibleMapPosition(localArea, 700 to 800))
        assertNull(visibleMapPosition(AreaSnapshot(self = PlayerState("mel e")), null))
    }
}
