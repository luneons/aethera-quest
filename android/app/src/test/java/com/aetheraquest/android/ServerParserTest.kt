package com.aetheraquest.android

import org.junit.Assert.*
import org.junit.Test

class ServerParserTest {
    @Test fun serverListUsesTls() {
        assertTrue(AqwConfig.SERVER_LIST_URL.startsWith("https://"))
        assertFalse(AqwConfig.SERVER_LIST_URL.startsWith("http://"))
    }

    @Test fun rejectsUntrustedGameServerHosts() {
        assertTrue(AqwConfig.isTrustedGameHost("sock7.aq.com"))
        assertTrue(AqwConfig.isTrustedGameHost("asia.game.artix.com"))
        assertFalse(AqwConfig.isTrustedGameHost("evil.aq.com"))
        assertFalse(AqwConfig.isTrustedGameHost("game.example"))
        assertFalse(AqwConfig.isTrustedGameHost("aq.com"))
    }

    @Test fun parsesAvailabilityAndCapacity() {
        val servers = AqwApi.parseServers("""[
          {"sName":"DemoServer","sIP":"sock7.aq.com","iPort":5588,"iCount":500,"iMax":1000,"bOnline":1,"bUpg":0},
          {"sName":"Full","sIP":"sock8.aq.com","iPort":5588,"iCount":1000,"iMax":1000,"bOnline":1,"bUpg":1},
          {"sName":"Untrusted","sIP":"evil.aq.com","iPort":5588,"iCount":0,"iMax":1000,"bOnline":1,"bUpg":0}
        ]""")
        assertEquals(2, servers.size)
        assertTrue(servers[0].online)
        assertFalse(servers[0].full)
        assertTrue(servers[1].full)
        assertTrue(servers[1].upgradeOnly)
    }
}
