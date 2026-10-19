package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Test

class PacketInboxTest {
    @Test fun preservesPacketsReadAfterTheMatchingHandshakePacket() {
        val inbox = PacketInbox()

        inbox.offer(listOf("joinOK", "moveToArea"))

        assertEquals("joinOK", inbox.poll())
        assertEquals("moveToArea", inbox.poll())
        assertEquals(null, inbox.poll())
    }
}
