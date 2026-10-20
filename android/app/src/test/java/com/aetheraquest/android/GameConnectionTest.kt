package com.aetheraquest.android

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class GameConnectionTest {
    private val login = LoginToken("demo-user", "sample-value")
    private val server = AqwServer("DemoServer", "sock7.aq.com", 5588, true, false, false)

    @Test fun `equip waits for server confirmation`() = runBlocking {
        val socket = ScriptedAqwSocket(confirmEquip = true)
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            val item = OwnedItem(75592, "Demo Cape", EquipmentSlot.CAPE, false)
            val result = connection.equip(item, timeoutMs = 1_000)
            assertEquals(1, result.userId)
            assertEquals(75592, result.itemId)
            assertEquals(EquipmentSlot.CAPE, result.slot)
            assertTrue(socket.sent.any { "%xt%zm%equipItem%42%75592%" == it })
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun `equip reports missing confirmation instead of lying about success`() = runBlocking {
        val socket = ScriptedAqwSocket(confirmEquip = false)
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            val item = OwnedItem(75592, "Demo Cape", EquipmentSlot.CAPE, false)
            val result = runCatching { connection.equip(item, timeoutMs = 100) }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("did not confirm"))
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun firstJoinPrecedesRoomListRequestDuringWorldEntry() = runBlocking {
        val socket = ScriptedAqwSocket(rejectFirstJoinAfterRoomList = true)
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            val firstJoin = socket.sent.indexOfFirst { "%xt%zm%firstJoin%1%" == it }
            val roomList = socket.sent.indexOfFirst { "action='getRmList'" in it }
            assertTrue("firstJoin must be sent", firstJoin >= 0)
            assertTrue("firstJoin must not wait behind getRmList", roomList < 0 || firstJoin < roomList)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun worldEntryWithJoinNoticeAndNoAreaStillReachesMap() = runBlocking {
        val socket = ScriptedAqwSocket(worldEntryStyle = WorldEntryStyle.JOIN_NOTICE_ALONE)
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            val ready = withTimeout(8_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            assertEquals("demo-1", ready.area.mapName)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun versionRejectedLoginFailsFast() = runBlocking {
        val socket = ScriptedAqwSocket(loginReject = true)
        val connection = GameConnection(socketFactory = { socket })
        try {
            val result = runCatching { withTimeout(10_000) { connection.connect(login, server, "demo") } }
            assertTrue("version-rejected login must fail", result.isFailure)
            assertTrue(
                "failure must say the login was rejected, got: ${result.exceptionOrNull()?.message}",
                (result.exceptionOrNull()?.message ?: "").contains("rejected", ignoreCase = true),
            )
        } finally {
            connection.close()
        }
    }

    @Test fun worldEntryAcceptsServerJoinNoticeWithoutJoinOk() = runBlocking {
        val socket = ScriptedAqwSocket(worldEntryStyle = WorldEntryStyle.JOIN_NOTICE)
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            val ready = withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            assertEquals("demo-1", ready.area.mapName)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun tferAcceptsServerJoinNoticeWithoutMoveToArea() = runBlocking {
        val socket = ScriptedAqwSocket(transferStyle = TransferStyle.JOIN_NOTICE)
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            val ready = withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            assertEquals("demo-1", ready.area.mapName)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun coalescedHandshakeKeepsTheInitialAreaPacket() = runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            val ready = withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            assertEquals("demo-1", ready.area.mapName)
            assertTrue(socket.sent.any { it == "%xt%zm%retrieveInventory%42%1%" })
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun chatAfkAndAttackUseTheActiveConnection() = runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            connection.sendChat("hello")
            connection.setAfk(true)
            connection.attack(12, "a1")
            assertTrue(socket.sent.contains("%xt%zm%message%42%hello%zone%"))
            assertTrue(socket.sent.contains("%xt%zm%afk%1%true%"))
            // Combat goes to shared room 1 (reference client + aqw-python2), not the area room.
            assertTrue(socket.sent.contains("%xt%zm%gar%1%0%a1>m:12%wvz%"))
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun combatSequenceRollsFromThirtyBackToZero() = runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            repeat(32) { connection.attack(12) }
            val combat = socket.sent.filter { "%gar%" in it }
            assertTrue(combat[30].contains("%30%aa>m:12%"))
            assertTrue(combat[31].contains("%0%aa>m:12%"))
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun chatIsNotDuplicatedWhenTheServerEchoesItBack() = runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            connection.sendChat("hello")
            socket.offer("%xt%chatm%-1%zone~hello%demo-user%1%42%")
            withTimeout(3_000) { connection.status.first { it.chat.isNotEmpty() } }
            // The server echo is the single source of truth for the log.
            assertEquals(1, connection.status.value.chat.size)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun inboundChatAppearsInStatusHistory() = runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            socket.offer("%xt%chatm%-1%zone~hello%demo-user%1%42%")
            val updated = withTimeout(3_000) { connection.status.first { it.chat.isNotEmpty() } }
            assertEquals("demo-user", updated.chat.last().sender)
            assertEquals("hello", updated.chat.last().message)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun movementUsesTheRoomOfLaterAreaTransfers() = runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            socket.offer(area(77))
            withTimeout(3_000) { connection.status.first { it.area.roomId == 77 } }
            connection.move(123, 234)
            connection.moveToCell("Boss", "Left")
            assertTrue(socket.sent.contains("%xt%zm%mv%77%123%234%10%"))
            assertTrue(socket.sent.contains("%xt%zm%moveToCell%77%Boss%Left%"))
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun aFailedDialClosesItsSocket() = runBlocking {
        val socket = ScriptedAqwSocket(failDial = true)
        val connection = GameConnection(socketFactory = { socket })
        try {
            assertTrue(runCatching { connection.connect(login, server, "demo") }.isFailure)
            assertTrue("a failed connect must not leak its allocated socket", socket.wasClosed)
        } finally {
            connection.close()
        }
    }

    @Test fun cancellationDuringHandshakeClosesSocketPromptly() = runBlocking {
        val socket = ScriptedAqwSocket(answerVersion = false)
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.VERSION } }
            worker.cancel()
            val finished = withTimeoutOrNull(1_500) { worker.join(); true } ?: false
            assertTrue("cancellation must interrupt a handshake wait", finished)
            assertTrue("cancelled connection must close its socket", socket.wasClosed)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }

    @Test fun cancellationDuringDialClosesTheInFlightSocket() = runBlocking {
        val socket = ScriptedAqwSocket(blockDial = true, releaseDialOnClose = true)
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        try {
            assertTrue(withContext(Dispatchers.IO) { socket.dialEntered.await(3, TimeUnit.SECONDS) })
            worker.cancel()
            val finished = withTimeoutOrNull(1_500) { worker.join(); true } ?: false
            assertTrue("cancellation must interrupt an in-flight dial", finished)
            assertTrue(socket.wasClosed)
        } finally {
            connection.close()
            socket.releaseDial.countDown()
            worker.cancelAndJoin()
        }
    }

    @Test fun anOldDialCannotCloseOrReplaceTheNewConnection() = runBlocking {
        val old = ScriptedAqwSocket(blockDial = true)
        val replacement = ScriptedAqwSocket()
        val sockets = LinkedBlockingQueue(listOf(old, replacement))
        val connection = GameConnection(socketFactory = { sockets.remove() })
        val first = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        var second: Job? = null
        try {
            assertTrue(withContext(Dispatchers.IO) { old.dialEntered.await(3, TimeUnit.SECONDS) })
            second = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
            withTimeout(3_000) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            old.releaseDial.countDown()
            withTimeout(3_000) { first.join() }
            assertFalse(replacement.wasClosed)
            assertTrue(old.wasClosed)
            assertEquals(ConnectionPhase.IN_MAP, connection.status.value.phase)
            connection.move(1, 2)
            assertTrue(replacement.sent.contains("%xt%zm%mv%42%1%2%10%"))
        } finally {
            old.releaseDial.countDown()
            connection.close()
            first.cancelAndJoin()
            second?.cancelAndJoin()
        }
    }

    @Test fun lateBytesFromAnOldReadCannotPolluteTheNewSession() = runBlocking {
        val old = ScriptedAqwSocket()
        val delayedRead = DelayedRead("stale-prefix")
        old.delayedRead = delayedRead
        val replacement = ScriptedAqwSocket(holdTransfer = true)
        val sockets = LinkedBlockingQueue(listOf(old, replacement))
        val connection = GameConnection(socketFactory = { sockets.remove() })
        val first = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
        var second: Job? = null
        try {
            assertTrue(withContext(Dispatchers.IO) { delayedRead.entered.await(3, TimeUnit.SECONDS) })
            second = launch(Dispatchers.IO) { connection.connect(login, server, "demo") }
            assertTrue(withContext(Dispatchers.IO) { replacement.transferRequested.await(3, TimeUnit.SECONDS) })
            delayedRead.release.countDown()
            withTimeout(3_000) { first.join() }
            replacement.offer(area(42))
            val ready = withTimeout(1_500) { connection.status.first { it.phase == ConnectionPhase.IN_MAP } }
            assertEquals("demo-1", ready.area.mapName)
        } finally {
            delayedRead.release.countDown()
            connection.close()
            first.cancelAndJoin()
            second?.cancelAndJoin()
        }
    }

    @Test fun untrustedHostIsRejectedBeforeAllocatingASocket() = runBlocking {
        var created = false
        val connection = GameConnection(socketFactory = { created = true; ScriptedAqwSocket() })
        try {
            val result = runCatching { connection.connect(login, server.copy(host = "game.example"), "demo") }
            assertTrue(result.isFailure)
            assertFalse(created)
        } finally {
            connection.close()
        }
    }
}
