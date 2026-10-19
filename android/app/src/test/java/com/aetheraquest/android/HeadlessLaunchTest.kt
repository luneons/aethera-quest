package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadlessLaunchTest {
    private val login = LoginToken("demo-user", "sample-value", 41)
    private val server = AqwServer("Yorumi", "sock8.aq.com", 5588, true, false, false)
    private val ready = UiState(selectedServer = server, mapName = "yulgar")

    @Test fun aCompleteSelectionProducesAHeadlessLaunch() {
        val launch = headlessLaunchFor(ready, login)
        assertEquals(login, launch?.login)
        assertEquals(server, launch?.server)
        assertEquals("yulgar", launch?.map)
    }

    @Test fun aMissingServerIsRefused() {
        assertNull(headlessLaunchFor(ready.copy(selectedServer = null), login))
    }

    @Test fun aBlankMapIsRefused() {
        assertNull(headlessLaunchFor(ready.copy(mapName = "   "), login))
    }

    @Test fun anExpiredLoginIsRefused() {
        assertNull(headlessLaunchFor(ready, null))
    }

    @Test fun theMapIsTrimmedBeforeUse() {
        assertEquals("yulgar", headlessLaunchFor(ready.copy(mapName = "  yulgar  "), login)?.map)
    }
}
