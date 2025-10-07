package id.xms.xcai.data.preferences

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xchat_settings")

class UserPreferences(private val context: Context) {

    companion object {
        private val SELECTED_MODEL_KEY = stringPreferencesKey("selected_model")
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")

        // Rate limiting keys
        private val REQUEST_COUNT_KEY = intPreferencesKey("request_count")
        private val WINDOW_START_TIME_KEY = longPreferencesKey("window_start_time")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")

        // Constants
        const val MAX_REQUESTS_PER_WINDOW = 20
        const val TIME_WINDOW_MS = 30 * 60 * 1000L // 30 menit
    }

    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    val selectedModelId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_MODEL_KEY] ?: "moonshotai/kimi-k2-instruct"
        }

    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DARK_MODE_KEY] ?: false
        }

    val isDynamicColorEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DYNAMIC_COLOR_KEY] ?: true
        }

    suspend fun setSelectedModel(modelId: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_MODEL_KEY] = modelId
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    // Rate limiting with device ID verification
    suspend fun canMakeRequest(): Pair<Boolean, Long> {
        val preferences = context.dataStore.data.first()
        val currentTime = System.currentTimeMillis()
        val windowStartTime = preferences[WINDOW_START_TIME_KEY] ?: 0L
        val requestCount = preferences[REQUEST_COUNT_KEY] ?: 0
        val storedDeviceId = preferences[DEVICE_ID_KEY]
        val currentDeviceId = getDeviceId()

        // Verify device ID (prevent data manipulation)
        if (storedDeviceId != null && storedDeviceId != currentDeviceId) {
            // Different device or data tampered, reset
            context.dataStore.edit { prefs ->
                prefs[DEVICE_ID_KEY] = currentDeviceId
                prefs[WINDOW_START_TIME_KEY] = currentTime
                prefs[REQUEST_COUNT_KEY] = 0
            }
            return Pair(true, 0L)
        }

        // Set device ID if not exists
        if (storedDeviceId == null) {
            context.dataStore.edit { prefs ->
                prefs[DEVICE_ID_KEY] = currentDeviceId
            }
        }

        // Cek apakah window sudah lewat
        if (currentTime - windowStartTime > TIME_WINDOW_MS) {
            // Reset window
            context.dataStore.edit { prefs ->
                prefs[WINDOW_START_TIME_KEY] = currentTime
                prefs[REQUEST_COUNT_KEY] = 0
            }
            return Pair(true, 0L)
        }

        // Cek apakah sudah mencapai limit
        if (requestCount >= MAX_REQUESTS_PER_WINDOW) {
            val timeRemaining = TIME_WINDOW_MS - (currentTime - windowStartTime)
            return Pair(false, timeRemaining)
        }

        return Pair(true, 0L)
    }

    suspend fun incrementRequestCount() {
        context.dataStore.edit { preferences ->
            val currentCount = preferences[REQUEST_COUNT_KEY] ?: 0
            val deviceId = getDeviceId()

            preferences[REQUEST_COUNT_KEY] = currentCount + 1
            preferences[DEVICE_ID_KEY] = deviceId

            // Set window start time jika belum ada
            if (preferences[WINDOW_START_TIME_KEY] == null) {
                preferences[WINDOW_START_TIME_KEY] = System.currentTimeMillis()
            }
        }
    }

    suspend fun getRemainingRequests(): Int {
        val preferences = context.dataStore.data.first()
        val currentTime = System.currentTimeMillis()
        val windowStartTime = preferences[WINDOW_START_TIME_KEY] ?: 0L
        val requestCount = preferences[REQUEST_COUNT_KEY] ?: 0

        // Jika window sudah lewat, return max requests
        if (currentTime - windowStartTime > TIME_WINDOW_MS) {
            return MAX_REQUESTS_PER_WINDOW
        }

        return MAX_REQUESTS_PER_WINDOW - requestCount
    }
    suspend fun resetRateLimit() {
        context.dataStore.edit { preferences ->
            preferences.remove(REQUEST_COUNT_KEY)
            preferences.remove(WINDOW_START_TIME_KEY)
        }
    }
}
