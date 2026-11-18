package dev.arison.codecmanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            autoSwitch = prefs[Keys.AUTO_SWITCH] ?: true,
            startOnBoot = prefs[Keys.START_ON_BOOT] ?: true,
            notifications = prefs[Keys.NOTIFICATIONS] ?: true,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
            languageTag = prefs[Keys.LANGUAGE_TAG]?.takeIf { it.isNotBlank() },
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
        )
    }

    val profiles: Flow<List<DeviceProfile>> = store.data.map { prefs ->
        prefs[Keys.PROFILES]?.let { raw ->
            runCatching { json.decodeFromString<List<DeviceProfile>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val current = AppSettings(
                autoSwitch = prefs[Keys.AUTO_SWITCH] ?: true,
                startOnBoot = prefs[Keys.START_ON_BOOT] ?: true,
                notifications = prefs[Keys.NOTIFICATIONS] ?: true,
                themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
                languageTag = prefs[Keys.LANGUAGE_TAG]?.takeIf { it.isNotBlank() },
                onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            )
            val next = transform(current)
            prefs[Keys.AUTO_SWITCH] = next.autoSwitch
            prefs[Keys.START_ON_BOOT] = next.startOnBoot
            prefs[Keys.NOTIFICATIONS] = next.notifications
            prefs[Keys.THEME_MODE] = next.themeMode.name
            prefs[Keys.DYNAMIC_COLOR] = next.dynamicColor
            prefs[Keys.REDUCE_MOTION] = next.reduceMotion
            prefs[Keys.LANGUAGE_TAG] = next.languageTag.orEmpty()
            prefs[Keys.ONBOARDING_DONE] = next.onboardingDone
        }
    }

    suspend fun setAutoSwitch(enabled: Boolean) = update { it.copy(autoSwitch = enabled) }

    suspend fun setOnboardingDone() = update { it.copy(onboardingDone = true) }

    suspend fun upsertProfile(profile: DeviceProfile) {
        store.edit { prefs ->
            val current = decodeProfiles(prefs[Keys.PROFILES])
            val next = current.filterNot { it.mac.equals(profile.mac, ignoreCase = true) } + profile
            prefs[Keys.PROFILES] = json.encodeToString(next.sortedBy { it.name.lowercase() })
        }
    }

    suspend fun removeProfile(mac: String) {
        store.edit { prefs ->
            val current = decodeProfiles(prefs[Keys.PROFILES])
            prefs[Keys.PROFILES] = json.encodeToString(
                current.filterNot { it.mac.equals(mac, ignoreCase = true) },
            )
        }
    }

    private fun decodeProfiles(raw: String?): List<DeviceProfile> =
        raw?.let { runCatching { json.decodeFromString<List<DeviceProfile>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    private object Keys {
        val AUTO_SWITCH = booleanPreferencesKey("auto_switch")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PROFILES = stringPreferencesKey("profiles")
    }
}
