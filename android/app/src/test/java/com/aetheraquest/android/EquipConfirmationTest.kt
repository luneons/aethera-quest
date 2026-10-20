package com.aetheraquest.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Equip is two-phase: the client sends `equipItem`, and the server broadcasts
 * `equipItem` back with the accepted `ItemID`/`strES`. Treating "packet sent"
 * as success makes rapid weapon/cape swaps look random, because a second click
 * can overtake the first while the server is still answering. Confirmation is
 * tracked per slot: a slot rejects a second click until the server answers the
 * first.
 */
class EquipConfirmationTest {
    private fun packet(es: String, itemId: Int) = JSONObject(
        """{"t":"xt","b":{"r":-1,"o":{"cmd":"equipItem","uid":46628,
        "strES":"$es","ItemID":$itemId,"sType":"X"}}}""",
    )

    @Test fun `cape and weapon confirmations are parsed`() {
        assertEquals(
            EquipConfirmation(46628, 75592, EquipmentSlot.CAPE),
            parseEquipConfirmation(packet("ba", 75592)),
        )
        assertEquals(
            EquipConfirmation(46628, 3, EquipmentSlot.WEAPON),
            parseEquipConfirmation(packet("Weapon", 3)),
        )
    }

    @Test fun `class confirmation is parsed`() {
        assertEquals(
            EquipConfirmation(46628, 15653, EquipmentSlot.CLASS),
            parseEquipConfirmation(packet("ar", 15653)),
        )
    }

    @Test fun `other packets and unknown slots yield nothing`() {
        assertNull(parseEquipConfirmation(JSONObject("""{"cmd":"ct"}""")))
        assertNull(parseEquipConfirmation(packet("zz", 5)))
        assertNull(parseEquipConfirmation(packet("ba", 0)))
    }

    @Test fun `missing user id remains parseable for direct payload compatibility`() {
        val direct = JSONObject("""{"cmd":"equipItem","strES":"ba","ItemID":5}""")
        assertEquals(EquipConfirmation(0, 5, EquipmentSlot.CAPE), parseEquipConfirmation(direct))
    }
}
