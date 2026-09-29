package com.coldchain.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cold_chain_auth_prefs")

data class PersistedSession(
    val token: String,
    val email: String,
    val role: String
)

@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_EMAIL = stringPreferencesKey("auth_email")
        private val KEY_ROLE = stringPreferencesKey("auth_role")
    }

    val sessionFlow: Flow<PersistedSession?> = context.dataStore.data.map { prefs ->
        val token = prefs[KEY_TOKEN]
        val email = prefs[KEY_EMAIL]
        val role = prefs[KEY_ROLE]

        if (!token.isNullOrBlank() && !email.isNullOrBlank() && !role.isNullOrBlank()) {
            PersistedSession(token = token, email = email, role = role)
        } else {
            null
        }
    }

    suspend fun getSession(): PersistedSession? {
        return sessionFlow.first()
    }

    suspend fun saveSession(token: String, email: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_EMAIL] = email
            prefs[KEY_ROLE] = role
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_EMAIL)
            prefs.remove(KEY_ROLE)
        }
    }
}
