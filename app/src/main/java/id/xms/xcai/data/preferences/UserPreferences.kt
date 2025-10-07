package id.xms.xcai.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xchat_settings")

class UserPreferencesManager(private val context: Context) {

    companion object {
        private val SELECTED_MODEL_KEY = stringPreferencesKey("selected_model")
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
    }

    val selectedModelId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_MODEL_KEY] ?: "moonshotai/kimi-k2-instruct-0905"
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
}
