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

/** What the "remember me" checkbox implies for stored credentials. */
internal sealed interface LoginCredentialAction {
    data class Save(val credentials: SavedCredentials) : LoginCredentialAction
    data object Clear : LoginCredentialAction
    data object None : LoginCredentialAction
}

/**
 * Decides the storage side-effect of the remember-me checkbox.
 *
 * The checkbox is the only control: checking it stores the typed login, and
 * unchecking it removes the stored one. Validation reuses `credentialsToSave`,
 * so a blank field can never overwrite an existing saved login with nothing.
 */
internal fun loginCredentialAction(
    remember: Boolean,
    username: String,
    password: String,
    currentlySaved: Boolean,
): LoginCredentialAction = when {
    remember -> credentialsToSave(username, password)
        ?.let { LoginCredentialAction.Save(it) }
        ?: LoginCredentialAction.None
    currentlySaved -> LoginCredentialAction.Clear
    else -> LoginCredentialAction.None
}
