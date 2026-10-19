package com.aetheraquest.android

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test fun framerHandlesSplitUtf8AndMultiplePackets() {
        val framer = PacketFramer()
        val bytes = "one\u0000héllo\u0000tail".toByteArray()
        val split = bytes.indexOf(0xC3.toByte()) + 1
        assertEquals(listOf("one"), framer.feed(bytes.copyOfRange(0, split)))
        assertEquals(listOf("héllo"), framer.feed(bytes.copyOfRange(split, bytes.size)))
        assertEquals(listOf("tail"), framer.feed(byteArrayOf(0)))
    }

    @Test fun framerResetDropsPartialFrameBeforeReconnect() {
        val framer = PacketFramer()
        assertTrue(framer.feed("stale-prefix".toByteArray()).isEmpty())
        framer.reset()
        assertEquals(listOf("fresh"), framer.feed("fresh\u0000".toByteArray()))
    }

    @Test fun serializesChatAfkAndCombatPackets() {
        assertEquals("%xt%zm%message%1%hello there%zone%\u0000", SfsProtocol.chat(1, "hello there").decodeToString())
        assertEquals("%xt%zm%message%1%hi%party%\u0000", SfsProtocol.chat(1, "hi", "party").decodeToString())
        assertEquals("%xt%zm%afk%1%true%\u0000", SfsProtocol.afk(true).decodeToString())
        assertEquals("%xt%zm%afk%1%false%\u0000", SfsProtocol.afk(false).decodeToString())
        assertEquals("%xt%zm%gar%1%5%aa>m:12%wvz%\u0000", SfsProtocol.attack(1, 5, "aa", "m:12").decodeToString())
        assertEquals("%xt%zm%gar%1%0%a1>m:12,a1>m:13%wvz%\u0000", SfsProtocol.attack(1, 0, "a1", "m:12", "m:13").decodeToString())
    }

    @Test fun rejectsOutOfRangeCombatSequence() {
        assertTrue(runCatching { SfsProtocol.attack(1, 31, "aa", "m:1") }.isFailure)
        assertTrue(runCatching { SfsProtocol.attack(1, -1, "aa", "m:1") }.isFailure)
        assertTrue(runCatching { SfsProtocol.attack(1, 0, "aa", "not-a-target") }.isFailure)
        assertTrue(runCatching { SfsProtocol.attack(1, 0, "zz", "m:1") }.isFailure)
    }

    @Test fun parsesIncomingChatMessages() {
        val chat = SfsProtocol.parseChat("%xt%chatm%-1%zone~hello there%demo-user%42%1%")!!
        assertEquals("zone", chat.channel)
        assertEquals("hello there", chat.message)
        assertEquals("demo-user", chat.sender)
        assertEquals(42, chat.userId)
        assertNull(SfsProtocol.parseChat("%xt%uotls%-1%alice%intHP:50%"))
        assertNull(SfsProtocol.parseChat("%xt%chatm%-1%no-separator%demo-user%42%1%"))
    }

    @Test fun serializesHandshakeAndExtensionPackets() {
        assertEquals("<msg t='sys'><body action='verChk' r='0'><ver v='157' /></body></msg>\u0000", SfsProtocol.verCheck().decodeToString())
        assertEquals("%xt%zm%cmd%1%tfer%alice%yulgar-1234%\u0000", SfsProtocol.join("Alice", "yulgar-1234").decodeToString())
        assertEquals("%xt%zm%moveToCell%42%Boss%Left%\u0000", SfsProtocol.moveToCell(42, "Boss", "Left").decodeToString())
        assertEquals("%xt%zm%mv%42%850%302%10%\u0000", SfsProtocol.move(42, 850, 302).decodeToString())
    }

    @Test fun loginUsesSupportedClientVersion() {
        assertTrue(SfsProtocol.login("Alice", "TOKEN").decodeToString().contains("SPIDER#0001~alice~5.1"))
    }

    @Test fun parsesPacketsAndIdentifiers() {
        val parsed = SfsProtocol.parseStringPacket("%xt%uotls%-1%alice%intHP:50%")!!
        assertEquals("uotls", parsed.command)
        assertEquals(-1, parsed.room)
        assertEquals(listOf("alice", "intHP:50"), parsed.args)
        assertEquals(21030, SfsProtocol.parseLoginUserId("%xt%loginResponse%-1%true%21030%alice%Welcome!%"))
        assertEquals(273, SfsProtocol.parseJoinRoom("<msg t='sys'><body action='joinOK' r='273'></body></msg>"))
        assertEquals("battleon-1", SfsProtocol.parseServerJoinNotice("%xt%server%-1%You joined \"battleon-1\"%"))
        assertNull(SfsProtocol.parseServerJoinNotice("%xt%server%-1%Profanity filter Off.%"))
    }
}
