package com.mlh.skinanalyzer.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pinDataStore by preferencesDataStore(name = "mlh_pin_store")

/**
 * PIN de acceso a la app (DataStore). Valor de fábrica: 9618.
 * Re-bloqueo tras [RELOCK_AFTER_MS] en segundo plano.
 */
object PinStore {
    const val DEFAULT_PIN = "9618"
    const val RELOCK_AFTER_MS = 5 * 60 * 1000L

    private val KEY_PIN = stringPreferencesKey("app_pin")
    private val KEY_UNLOCKED_AT = longPreferencesKey("unlocked_at_ms")

    fun pinFlow(ctx: Context): Flow<String> =
        ctx.pinDataStore.data.map { prefs ->
            prefs[KEY_PIN] ?: DEFAULT_PIN
        }

    suspend fun currentPin(ctx: Context): String =
        pinFlow(ctx).first()

    suspend fun setPin(ctx: Context, pin: String) {
        require(pin.length == 4 && pin.all { it.isDigit() })
        ctx.pinDataStore.edit { it[KEY_PIN] = pin }
    }

    suspend fun markUnlocked(ctx: Context) {
        ctx.pinDataStore.edit { it[KEY_UNLOCKED_AT] = System.currentTimeMillis() }
    }

    suspend fun clearUnlock(ctx: Context) {
        ctx.pinDataStore.edit { it.remove(KEY_UNLOCKED_AT) }
    }

    /** true si hay sesión desbloqueada reciente. */
    suspend fun isSessionValid(ctx: Context): Boolean {
        val prefs = ctx.pinDataStore.data.first()
        val at = prefs[KEY_UNLOCKED_AT] ?: return false
        return System.currentTimeMillis() - at < RELOCK_AFTER_MS
    }

    suspend fun verify(ctx: Context, candidate: String): Boolean {
        val ok = candidate == currentPin(ctx)
        if (ok) markUnlocked(ctx)
        return ok
    }
}
