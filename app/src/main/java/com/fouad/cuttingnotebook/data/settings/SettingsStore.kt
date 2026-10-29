package com.fouad.cuttingnotebook.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Accent choices offered in Settings; the names are resolved to Arabic in the UI layer. */
enum class AccentColor { GREEN, BLUE, INDIGO, TEAL, AMBER, CRIMSON }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.GREEN,
    val soundsEnabled: Boolean = true,
    /** Button clicks are muted separately from the success/error cues (spec 11). */
    val buttonSoundsEnabled: Boolean = false,
    val pinConfigured: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * App preferences, plus the PIN verifier.
 *
 * The PIN itself is never stored: only a PBKDF2 hash and its salt, which is what the lock
 * screen checks against and what is deliberately left out of the backup file (spec 13, 15).
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            accent = prefs[KEY_ACCENT]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
                ?: AccentColor.GREEN,
            soundsEnabled = prefs[KEY_SOUNDS] ?: true,
            buttonSoundsEnabled = prefs[KEY_BUTTON_SOUNDS] ?: false,
            pinConfigured = prefs[KEY_PIN_HASH] != null,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setAccent(accent: AccentColor) {
        store.edit { it[KEY_ACCENT] = accent.name }
    }

    suspend fun setSoundsEnabled(enabled: Boolean) {
        store.edit { it[KEY_SOUNDS] = enabled }
    }

    suspend fun setButtonSoundsEnabled(enabled: Boolean) {
        store.edit { it[KEY_BUTTON_SOUNDS] = enabled }
    }

    // ---- PIN ---------------------------------------------------------------------------

    suspend fun storedVerifier(): PinVerifier? {
        val prefs = store.data.first()
        val hash = prefs[KEY_PIN_HASH] ?: return null
        val salt = prefs[KEY_PIN_SALT] ?: return null
        val iterations = prefs[KEY_PIN_ITERATIONS] ?: return null
        return PinVerifier(hash = hash, salt = salt, iterations = iterations)
    }

    suspend fun saveVerifier(verifier: PinVerifier) {
        store.edit {
            it[KEY_PIN_HASH] = verifier.hash
            it[KEY_PIN_SALT] = verifier.salt
            it[KEY_PIN_ITERATIONS] = verifier.iterations
        }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_ACCENT = stringPreferencesKey("accent_color")
        val KEY_SOUNDS = booleanPreferencesKey("sounds_enabled")
        val KEY_BUTTON_SOUNDS = booleanPreferencesKey("button_sounds_enabled")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        val KEY_PIN_ITERATIONS = intPreferencesKey("pin_iterations")
    }
}
