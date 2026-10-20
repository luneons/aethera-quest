package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * With a 20 s scroll cooldown and 10 s of Focus, two taunters alternate a short
 * gap and a long one. The long one exceeds Focus, which is the "loop, pause,
 * loop" the player reported. These tests pin the arithmetic so nobody "fixes" it
 * by making the refresh faster, which widens the long gap instead.
 */
class TauntCadenceTest {
    private fun taunter(id: Int) = AuxiliaryAccount(
        id = id,
        username = "demo-$id",
        role = if (id % 2 == 1) CompanionRole.TAUNTER_A else CompanionRole.TAUNTER_B,
        enabled = true,
        consumableItemId = SCROLL_OF_ENRAGE_ITEM_ID,
    )

    /** Replays the rotation and returns the gaps between consecutive casts. */
    private fun gaps(taunters: List<AuxiliaryAccount>, stepMs: Long = 100L, horizonMs: Long = 90_000L): List<Long> {
        var rotation = TauntRotation()
        var now = 0L
        val casts = mutableListOf<Long>()
        while (now <= horizonMs) {
            val decision = nextTaunt(rotation, taunters, now)
            if (decision != null) {
                rotation = recordTaunt(rotation, decision, now)
                casts += now
            }
            now += stepMs
        }
        return casts.zipWithNext { a, b -> b - a }
    }

    @Test fun `two taunters alternate a short and a long gap`() {
        val g = gaps(listOf(taunter(1), taunter(2)))
        assertTrue(g.isNotEmpty())
        val short = g[0]
        val long = g[1]
        // The short gap is the refresh interval, the long one is what is left of
        // the 20 s cooldown after it.
        assertEquals(TauntTiming().refreshAtMs.toDouble(), short.toDouble(), 200.0)
        assertEquals(
            (TauntTiming().scrollCooldownMs - TauntTiming().refreshAtMs).toDouble(),
            long.toDouble(),
            200.0,
        )
        assertTrue("long gap must exceed focus duration", long > TauntTiming().focusDurationMs)
    }

    @Test fun `three taunters make every gap fit inside the focus window`() {
        val g = gaps(listOf(taunter(1), taunter(2), taunter(3)))
        assertTrue(g.isNotEmpty())
        assertTrue("every gap should be under focus: $g", g.all { it <= TauntTiming().focusDurationMs })
    }

    @Test fun `the gap warning names the missing taunter`() {
        assertEquals(
            "2 taunter(s): Focus drops briefly, add one more for a seamless loop",
            focusGapWarning(2),
        )
        assertEquals("no taunter configured", focusGapWarning(0))
        assertNull(focusGapWarning(TAUNTERS_FOR_SEAMLESS_FOCUS))
        assertNull(focusGapWarning(5))
    }

    @Test fun `two taunters are reported as not seamless`() {
        assertNotNull(focusGapWarning(2))
        assertEquals(3, TAUNTERS_FOR_SEAMLESS_FOCUS)
    }

    @Test fun `attack pause is short enough not to look like a freeze`() {
        // The reserve only has to cover packet latency, not seconds of downtime.
        assertTrue(TauntTiming().attackReserveMs <= 1_000L)
    }
}