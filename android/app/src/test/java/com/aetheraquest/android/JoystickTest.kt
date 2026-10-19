package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoystickTest {
    @Test fun aTinyPushInsideTheDeadZoneIsIgnored() {
        assertNull(joystickTarget(500, 500, dx = 0.05f, dy = 0.05f))
        assertNull(joystickTarget(500, 500, dx = 0f, dy = 0f))
    }

    @Test fun aFullRightwardPushMovesOnlyX() {
        val target = joystickTarget(500, 500, dx = 1f, dy = 0f, step = 60)!!
        assertEquals(560, target.first)
        assertEquals(500, target.second)
    }

    @Test fun aHalfPushMovesHalfTheStep() {
        val target = joystickTarget(500, 500, dx = 0f, dy = -0.5f, step = 60)!!
        assertEquals(500, target.first)
        assertEquals(470, target.second)
    }

    @Test fun aDiagonalPushMovesBothAxes() {
        val target = joystickTarget(500, 500, dx = 0.7f, dy = 0.7f, step = 60)!!
        assertEquals(542, target.first)
        assertEquals(542, target.second)
    }

    @Test fun pushingPastTheMapEdgeClampsInsteadOfLeavingTheRoom() {
        assertEquals(1000, joystickTarget(990, 500, dx = 1f, dy = 0f, step = 60)!!.first)
        assertEquals(0, joystickTarget(10, 500, dx = -1f, dy = 0f, step = 60)!!.first)
        assertEquals(1000, joystickTarget(500, 995, dx = 0f, dy = 1f, step = 60)!!.second)
    }

    @Test fun pushingAgainstAnEdgeReportsNoMovement() {
        assertNull(joystickTarget(1000, 500, dx = 1f, dy = 0f, step = 60))
        assertNull(joystickTarget(500, 0, dx = 0f, dy = -1f, step = 60))
    }

    @Test fun anOverdrivenVectorIsClampedToTheStepSize() {
        val target = joystickTarget(500, 500, dx = 5f, dy = 0f, step = 60)!!
        assertEquals(560, target.first)
    }

    @Test fun aSpeedMultiplierScalesTheStep() {
        val walk = joystickTarget(500, 500, dx = 1f, dy = 0f, step = 60)!!
        val run = joystickTarget(500, 500, dx = 1f, dy = 0f, step = 60, multiplier = 2f)!!
        assertEquals(560, walk.first)
        assertEquals(500, walk.second)
        assertEquals(620, run.first)
        assertEquals(500, run.second)
    }
}
