package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapNameTest {
    @Test fun acceptsPlainAndNumberedMapNames() {
        assertEquals(null, mapNameError("yulgar"))
        assertEquals(null, mapNameError("yulgar-1234"))
        assertEquals(null, mapNameError("  battleon-9999  "))
        assertEquals(null, mapNameError("Yulgar-1"))
    }

    @Test fun rejectsEmptyAndWhitespaceOnly() {
        assertTrue(mapNameError("") != null)
        assertTrue(mapNameError("   ") != null)
    }

    @Test fun rejectsSeparatorAndControlCharacters() {
        // A '%' or NUL would break the packet framing and could inject packets.
        assertTrue(mapNameError("yulgar%1234") != null)
        assertTrue(mapNameError("yulgar\u00001234") != null)
        assertTrue(mapNameError("yulgar\n1234") != null)
    }

    @Test fun rejectsNamesWithInternalSpaces() {
        assertTrue(mapNameError("yul gar") != null)
    }

    @Test fun trimsTheAcceptedValue() {
        assertEquals("yulgar-1234", normalizedMapName("  yulgar-1234  "))
    }

    @Test fun aValidNameIsReportedAsHavingNoError() {
        assertFalse(mapNameError("yulgar-1234") != null)
    }
}
