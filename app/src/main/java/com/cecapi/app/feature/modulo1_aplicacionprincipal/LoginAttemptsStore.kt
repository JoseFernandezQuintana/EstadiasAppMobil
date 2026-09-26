package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.loginAttemptsDataStore by preferencesDataStore(name = "login_attempts")

/**
 * Counts failed sign-ins on this device and locks sign-in after [MAX_FAILED_ATTEMPTS] misses.
 * It is stored on disk so closing and reopening the app does not reset the count.
 * There is no admin screen yet to unlock an account, so the lock simply expires after [LOCK_MS].
 */
@Singleton
class LoginAttemptsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Milliseconds left on the lock, or 0 if sign-in is allowed. */
    suspend fun lockRemainingMs(): Long {
        val until = context.loginAttemptsDataStore.data.first()[KEY_LOCKED_UNTIL] ?: 0L
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Records a failed attempt. Returns which failure this was (1..[MAX_FAILED_ATTEMPTS]). */
    suspend fun recordFailure(): Int {
        var failures = 0
        context.loginAttemptsDataStore.edit { prefs ->
            failures = (prefs[KEY_FAILED] ?: 0) + 1
            if (failures >= MAX_FAILED_ATTEMPTS) {
                prefs[KEY_LOCKED_UNTIL] = System.currentTimeMillis() + LOCK_MS
                prefs[KEY_FAILED] = 0
            } else {
                prefs[KEY_FAILED] = failures
            }
        }
        return failures
    }

    suspend fun reset() {
        context.loginAttemptsDataStore.edit {
            it[KEY_FAILED] = 0
            it[KEY_LOCKED_UNTIL] = 0L
        }
    }

    companion object {
        const val MAX_FAILED_ATTEMPTS = 10
        const val LOCK_MS = 15 * 60 * 1000L

        private val KEY_FAILED = intPreferencesKey("failed_attempts")
        private val KEY_LOCKED_UNTIL = longPreferencesKey("locked_until")
    }
}
