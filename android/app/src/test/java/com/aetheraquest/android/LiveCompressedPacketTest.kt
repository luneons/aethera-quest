package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveCompressedPacketTest {
    /** Any `Z...` packet captured from the live server (doomwood), as-is. */
    @Test fun decodesTheRealCapture() {
        val capture = File("src/test/resources/wire4_sample.txt")
        assertTrue("missing fixture", capture.exists())
        val packet = capture.readText().trim()
        assertTrue(packet.startsWith('Z'))
        val decoded = decodeSmartFoxPacket(packet)
        // If decompression failed, the raw Z blob comes back untouched.
        assertTrue(
            "expected decompressed JSON, got len=${decoded.length}:\n${decoded.take(120)}",
            decoded.startsWith("{") && decoded.contains("moveToArea"),
        )
    }
}