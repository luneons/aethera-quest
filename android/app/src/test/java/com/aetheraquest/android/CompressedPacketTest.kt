package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressedPacketTest {
    /** Real decompressed moveToArea (doomwood) trimmed to the fields we need. */
    private val decompressed = """
        {"t":"xt","b":{"r":-1,"o":{"cmd":"moveToArea","areaId":106071,"areaName":"doomwood-93635",
        "monmap":[{"MonMapID":1,"MonID":615,"strFrame":"r2"},{"MonMapID":2,"MonID":616,"strFrame":"r2"}],
        "mondef":[{"MonID":615,"strMonName":"Doomwood Ectomancer"},{"MonID":616,"strMonName":"Doomwood Bonemuncher"}],
        "uoBranch":[{"uoName":"mel e","entType":"p","entID":46406,"strUsername":"MEL E","strFrame":"Enter","strPad":"Spawn","intState":1,"intLevel":41,"intHP":1602,"intMP":100,"intHPMax":1602,"intMPMax":100,"tx":0,"ty":0,"afk":false}],
        "monBranch":[{"MonMapID":1,"MonID":615,"intState":1,"intHP":3300,"intMP":100,"intHPMax":3300,"intMPMax":100},{"MonMapID":2,"MonID":616,"intState":1,"intHP":3400,"intMP":100,"intHPMax":3400,"intMPMax":100}]}}}
    """.trimIndent()

    private fun compress(payload: String): String {
        val deflater = java.util.zip.Deflater()
        deflater.setInput(payload.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return "Z" + java.util.Base64.getEncoder().encodeToString(out.toByteArray())
    }

    @Test fun decompressesAZlibPacketBackToTheOriginalJson() {
        val wire = compress(decompressed)
        assertTrue(wire.startsWith('Z'))
        assertEquals(decompressed.replace("\\s".toRegex(), ""), decodeSmartFoxPacket(wire).replace("\\s".toRegex(), ""))
    }

    @Test fun passesThroughPlainPacketsUntouched() {
        assertEquals("%xt%zm%message%1%hi%zone%", decodeSmartFoxPacket("%xt%zm%message%1%hi%zone%"))
        assertEquals("plain", decodeSmartFoxPacket("plain"))
    }

    @Test fun reducerPicksUpMonstersFromTheCompressedMoveToArea() {
        val reducer = AreaReducer("mel e", 46406)
        reducer.feed(decodeSmartFoxPacket(compress(decompressed)))
        val area = reducer.snapshot()
        assertEquals("doomwood-93635", area.mapName)
        assertEquals(2, area.monsters.size)
        assertEquals(615, area.monsters[1]?.monsterId)
        assertEquals("Doomwood Ectomancer", area.monsters[1]?.name)
        assertEquals(3300, area.monsters[1]?.hp)
        assertEquals(616, area.monsters[2]?.monsterId)
        assertEquals("Doomwood Bonemuncher", area.monsters[2]?.name)
        assertEquals("mel e", area.self?.username)
    }
}