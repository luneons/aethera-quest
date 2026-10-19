package com.aetheraquest.android

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RespawnProtocolTest {
    private fun text(bytes: ByteArray) = bytes.toString(Charsets.UTF_8).trimEnd('\u0000')

    @Test fun `timed respawn uses live room and user id`() {
        assertEquals(
            "%xt%zm%resPlayerTimed%42%46841%",
            text(SfsProtocol.respawnPlayer(42, 46841)),
        )
    }

    @Test fun `a5 uses potion wire ref`() {
        assertEquals(
            "%xt%zm%gar%1%4%i1>m:21%wvz%",
            text(SfsProtocol.attack(42, 4, "i1", "m:21")),
        )
    }
}
