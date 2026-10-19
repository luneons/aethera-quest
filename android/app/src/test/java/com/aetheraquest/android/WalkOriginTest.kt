package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkOriginTest {
    private fun area(x: Int?, y: Int?) = AreaSnapshot(
        self = PlayerState("mel e", x = x, y = y),
    )

    @Test fun usesTheServerReportedPositionWhenPresent() {
        assertEquals(320 to 480, walkOrigin(area(320, 480)))
    }

    @Test fun fallsBackToTheCentreWhenTheServerSendsNoCoordinates() {
        // Live AQW joins often omit tx/ty, so the joystick must still have a base.
        assertEquals(DEFAULT_X to DEFAULT_Y, walkOrigin(area(null, null)))
    }

    @Test fun keepsTheTrackedPositionWhenTheAreaHasNone() {
        assertEquals(700 to 250, walkOrigin(area(null, null), tracked = 700 to 250))
    }

    @Test fun serverPositionWinsOverTheTrackedPosition() {
        assertEquals(320 to 480, walkOrigin(area(320, 480), tracked = 700 to 250))
    }

    @Test fun aMissingSelfStillYieldsAUsableOrigin() {
        assertEquals(DEFAULT_X to DEFAULT_Y, walkOrigin(AreaSnapshot()))
    }

    @Test fun walkingFromAFallbackOriginStillProducesATarget() {
        val origin = walkOrigin(area(null, null))
        val target = joystickTarget(origin.first, origin.second, dx = 1f, dy = 0f, step = 55)
        assertEquals(DEFAULT_X + 55, target!!.first)
    }

    @Test fun aReleasedStickDoesNotMove() {
        assertNull(joystickTarget(DEFAULT_X, DEFAULT_Y, dx = 0f, dy = 0f, step = 55))
    }
}
