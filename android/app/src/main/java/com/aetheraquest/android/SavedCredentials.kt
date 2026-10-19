package com.aetheraquest.android

/** Credentials chosen by the user for local encrypted storage. */
data class SavedCredentials(val username: String, val password: String)

/** Validate the login fields before sending secrets to storage. */
internal fun credentialsToSave(username: String, password: String): SavedCredentials? {
    val cleanUsername = username.trim()
    if (cleanUsername.isEmpty() || password.isEmpty()) return null
    return SavedCredentials(cleanUsername, password)
}

/** Restore the saved pair into the login form. */
internal fun restoreSavedCredentials(state: UiState, saved: SavedCredentials?): UiState =
    if (saved == null) state else state.copy(
        username = saved.username,
        password = saved.password,
        credentialsSaved = true,
    )
