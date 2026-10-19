package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlessNotificationTest {
    private fun inMap(room: Int? = 42, map: String? = "yulgar-14045", monsters: Int = 3) =
        ConnectionStatus(
            phase = ConnectionPhase.IN_MAP,
            message = "Connected to yulgar-14045",
            area = AreaSnapshot(
                roomId = room,
                mapName = map,
                monsters = (1..monsters).associateWith { MonsterState(mapId = it, name = "M$it") },
            ),
        )

    @Test fun inMapShowsMapNameAndRoomInsteadOfAGenericLabel() {
        val notification = headlessNotification(inMap())
        assertEquals("yulgar-14045", notification.title)
        assertTrue("room must be visible: ${notification.text}", notification.text.contains("42"))
        assertTrue("monster count must be visible: ${notification.text}", notification.text.contains("3"))
    }

    @Test fun aMapWithoutMonstersStillReportsTheRoom() {
        val notification = headlessNotification(inMap(monsters = 0))
        assertEquals("yulgar-14045", notification.title)
        assertTrue(notification.text.contains("42"))
        assertFalse("zero monsters must not be claimed", notification.text.contains("0 monsters"))
    }

    @Test fun aMissingMapNameFallsBackToAConcreteLabel() {
        val notification = headlessNotification(inMap(map = null))
        assertEquals("In map", notification.title)
    }

    @Test fun handshakePhasesReportTheirOwnProgressMessage() {
        val status = ConnectionStatus(phase = ConnectionPhase.JOINING, message = "Joining yulgar…")
        val notification = headlessNotification(status)
        assertEquals("Joining yulgar…", notification.text)
        assertTrue(notification.ongoing)
    }

    @Test fun doesNotStopDuringAnOrdinaryDisconnectedInitialState() {
        assertFalse(shouldStopHeadless(ConnectionStatus()))
    }

    @Test fun stopsAfterARealConnectionFallsBackToDisconnected() {
        assertTrue(shouldStopHeadless(ConnectionStatus(), hasConnected = true))
    }

    @Test fun aLiveSessionKeepsTheServiceAlive() {
        assertFalse(shouldStopHeadless(inMap()))
        assertFalse(shouldStopHeadless(ConnectionStatus(phase = ConnectionPhase.CONNECTING, message = "Connecting…")))
    }
}
