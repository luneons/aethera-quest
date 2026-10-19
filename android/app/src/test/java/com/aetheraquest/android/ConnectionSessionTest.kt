package com.aetheraquest.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSessionTest {
    @Test fun aNewSessionSupersedesOlderSocketWork() {
        val sessions = ConnectionSession()
        val first = sessions.begin()
        val second = sessions.begin()

        assertFalse(sessions.isCurrent(first))
        assertTrue(sessions.isCurrent(second))

        sessions.invalidate()
        assertFalse(sessions.isCurrent(second))
    }
}
