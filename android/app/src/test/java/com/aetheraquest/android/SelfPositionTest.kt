package com.aetheraquest.android

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelfPositionTest {
    private val login = LoginToken("demo-user", "sample-value")
    private val server = AqwServer("DemoServer", "sock7.aq.com", 5588, true, false, false)
    private val join = """{"t":"xt","b":{"r":42,"o":{"cmd":"moveToArea","areaId":42,"areaName":"demo-1",
        "uoBranch":[{"uoName":"demo-user","uid":1,"strFrame":"Enter","strPad":"Spawn","tx":120,"ty":340}],
        "mondef":[],"monmap":[],"monBranch":[]}}}"""

    @Test fun moveToAreaCarriesTheSelfCoordinatesWhenTheServerIncludesThem() {
        val reducer = AreaReducer("demo-user")
        reducer.feed(join)
        assertEquals(120, reducer.snapshot().self?.x)
        assertEquals(340, reducer.snapshot().self?.y)
    }

    @Test fun uotlsUpdatesTheSelfCoordinates() {
        val reducer = AreaReducer("demo-user")
        reducer.feed(join)
        reducer.feed("%xt%zm%uotls%-1%demo-user%strFrame:Enter,strPad:Spawn,tx:555,ty:666%")
        val self = reducer.snapshot().self
        assertEquals(555, self?.x)
        assertEquals(666, self?.y)
    }

    @Test fun aMovementWithoutCoordinatesKeepsTheLastKnownPosition() {
        val reducer = AreaReducer("demo-user")
        reducer.feed(join)
        reducer.feed("%xt%zm%uotls%-1%demo-user%strFrame:Enter,strPad:Spawn%")
        val self = reducer.snapshot().self
        assertEquals(120, self?.x)
        assertEquals(340, self?.y)
    }

    @Test fun aJoinWithoutCoordinatesLeavesThePositionUnknown() {
        val reducer = AreaReducer("demo-user")
        reducer.feed(join.replace(",\"tx\":120,\"ty\":340", ""))
        assertNull(reducer.snapshot().self?.x)
        assertNull(reducer.snapshot().self?.y)
    }

    @Test fun ourOwnMoveIsNotReportedAsAServerConfirmedPosition() = kotlinx.coroutines.runBlocking {
        val socket = ScriptedAqwSocket()
        val connection = GameConnection(socketFactory = { socket })
        val worker = launch(Dispatchers.IO) {
            connection.connect(login, server, "demo")
        }
        try {
            withTimeout(3_000) {
                connection.status.first { it.phase == ConnectionPhase.IN_MAP }
            }
            connection.move(600, 500)
            // The demo area carries no tx/ty, and our own mv must not be
            // promoted into a server-authoritative coordinate.
            assertEquals(600, connection.status.value.area.self?.x)
            assertEquals(
                MapPosition(600, 500, PositionSource.LOCAL),
                visibleMapPosition(connection.status.value.area, 600 to 500),
            )
            assertEquals(false, connection.status.value.area.selfPositionServerObserved)
        } finally {
            connection.close()
            worker.cancelAndJoin()
        }
    }
}
