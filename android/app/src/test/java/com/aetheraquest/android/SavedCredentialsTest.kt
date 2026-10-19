package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedCredentialsTest {
    @Test fun `blank username cannot be saved`() {
        assertNull(credentialsToSave("   ", "sample-value"))
    }

    @Test fun `blank password cannot be saved`() {
        assertNull(credentialsToSave("demo-user", ""))
    }

    @Test fun `username is trimmed but password is preserved exactly`() {
        assertEquals(
            SavedCredentials("demo-user", " sample-value "),
            credentialsToSave("  demo-user  ", " sample-value "),
        )
    }

    @Test fun `saved credentials restore both login fields`() {
        val state = UiState(username = "", password = "")
        val restored = restoreSavedCredentials(state, SavedCredentials("demo-user", "sample-value"))
        assertEquals("demo-user", restored.username)
        assertEquals("sample-value", restored.password)
        assertEquals(true, restored.credentialsSaved)
    }

    @Test fun `missing saved credentials leaves fields alone`() {
        val state = UiState(username = "typed-user", password = "typed-value")
        assertEquals(state, restoreSavedCredentials(state, null))
    }
}
