package com.aetheraquest.android

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class HeadlessSessionTest {
    private val login = LoginToken("demo-user", "sample-value")
    private val server = AqwServer("DemoServer", "sock7.aq.com", 5588, true, false, false)

    @Test fun startsAConnectionWithoutAnyUiAttached() = runBlocking {
        val socket = ScriptedAqwSocket()
        val session = HeadlessSession(socketFactory = { socket })
        try {
            session.start(login, server, "demo")
            val ready = withTimeout(3_000) { session.status.first { it.phase == ConnectionPhase.IN_MAP } }
            assertEquals("demo-1", ready.area.mapName)
            assertFalse("headless must not require a screen", session.hasUi)
        } finally {
            session.stop()
        }
    }

    @Test fun reportsWhenTheServerDropsTheSession() = runBlocking {
        val socket = ScriptedAqwSocket()
        val session = HeadlessSession(socketFactory = { socket })
        try {
            session.start(login, server, "demo")
            withTimeout(3_000) { session.status.first { it.phase == ConnectionPhase.IN_MAP } }
            socket.offer("%xt%server%-1%Disconnected%")
            socket.fail()
            val dead = withTimeout(5_000) { session.status.first { it.phase == ConnectionPhase.DISCONNECTED } }
            assertTrue(shouldStopHeadless(dead, hasConnected = true))
        } finally {
            session.stop()
        }
    }

    @Test fun stopClosesTheSocketSoNoBackgroundWorkSurvives() = runBlocking {
        val socket = ScriptedAqwSocket()
        val session = HeadlessSession(socketFactory = { socket })
        session.start(login, server, "demo")
        withTimeout(3_000) { session.status.first { it.phase == ConnectionPhase.IN_MAP } }
        session.stop()
        assertTrue("stopping headless must close the game socket", socket.wasClosed)
        assertEquals(ConnectionPhase.DISCONNECTED, session.status.value.phase)
    }
}
