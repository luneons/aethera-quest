package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionNavigationTest {
    @Test fun disconnectDoesNotNavigateBackToMapWhenConnectReturns() {
        assertEquals(
            Screen.LOGIN,
            screenForConnectionResult(Screen.LOGIN, ConnectionStatus()),
        )
    }

    @Test fun successfulConnectionNavigatesToMap() {
        assertEquals(
            Screen.MAP,
            screenForConnectionResult(Screen.JOIN, ConnectionStatus(ConnectionPhase.IN_MAP, "ready")),
        )
    }
}
