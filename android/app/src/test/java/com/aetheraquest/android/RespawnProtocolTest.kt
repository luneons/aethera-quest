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

    @Test fun `consumable i1 carries its item id`() {
        // The reference client sends the assigned ItemID before `wvz`; without it
        // the server ignores the cast, which is why a taunt scroll would do nothing.
        assertEquals(
            "%xt%zm%gar%1%4%i1>m:21%99001%wvz%",
            text(SfsProtocol.attack(42, 4, "i1", "m:21", itemId = 99001)),
        )
    }

    @Test fun `consumable i1 without an item id is rejected`() {
        val result = runCatching { SfsProtocol.attack(42, 4, "i1", "m:21") }
        assertEquals(true, result.isFailure)
    }

    @Test fun `serverUseItem uses the inventory wire form`() {
        assertEquals(
            "%xt%zm%serverUseItem%42%+%99001%",
            text(SfsProtocol.useItem(42, 99001)),
        )
    }
}
