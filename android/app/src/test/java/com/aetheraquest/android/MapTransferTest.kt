package com.aetheraquest.android

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTransferTest {
    private val login = LoginToken("demo-user", "sample-value")
    private val server = AqwServer("DemoServer", "sock7.aq.com", 5588, true, false, false)

    @Test fun transferSendsTferForTheRequestedMap() = runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            connection.transfer("yulgar-1234")
            assertTrue(
                "transfer must send a tfer for the requested map",
                socket.sent.any { it == "%xt%zm%cmd%1%tfer%demo-user%yulgar-1234%" },
            )
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun aSuccessfulTransferReportsTheNewMap() = runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            connection.transfer("yulgar-1234")
            val moved = withTimeout(5_000) {
                connection.status.first { it.area.mapName == "yulgar-1234" }
            }
            assertEquals(ConnectionPhase.IN_MAP, moved.phase)
            assertEquals("yulgar-1234", moved.area.mapName)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun theConnectionStaysUsableAfterATransfer() = runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            connection.transfer("yulgar-1234")
            withTimeout(5_000) { connection.status.first { it.area.mapName == "yulgar-1234" } }
            // A transfer must not tear down the socket or the receive loop.
            connection.sendChat("still here")
            assertTrue(socket.sent.any { "%xt%zm%message%42%still here%zone%" == it })
            assertEquals(ConnectionPhase.IN_MAP, connection.status.value.phase)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun anIgnoredTransferFailsWithoutKillingTheConnection() = runBlocking {
        val socket = ScriptedAqwSocket(ignoreLaterTransfers = true)
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            val result = runCatching { withTimeout(30_000) { connection.transfer("yulgar-1234", timeoutMs = 1_500) } }
            assertTrue("an unanswered transfer must fail", result.isFailure)
            assertEquals(ConnectionPhase.IN_MAP, connection.status.value.phase)
            connection.sendChat("after failure")
            assertTrue(socket.sent.any { "%xt%zm%message%42%after failure%zone%" == it })
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun transferRejectsBlankAndUntrustedMapNames() = runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            assertTrue(runCatching { connection.transfer("   ") }.isFailure)
            assertTrue(runCatching { connection.transfer("bad map!") }.isFailure)
            assertTrue(runCatching { connection.transfer("yulgar\n1234") }.isFailure)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }
}
