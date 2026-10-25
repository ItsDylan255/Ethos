package com.example.ethos.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You (dynamic color). Off by default; enabled on request. */
    val dynamicColor: Boolean = false,
    /** "Never miss twice" hint on the Today screen. */
    val missedHint: Boolean = true,
    /** Daily notification listing today's habits. */
    val dailyReportEnabled: Boolean = true,
    val dailyReportMinutes: Int = 8 * 60,
    /** Notification with the personal motivation of habits that were missed once. */
    val motivationEnabled: Boolean = true,
    val motivationMinutes: Int = 9 * 60,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val missedKey = booleanPreferencesKey("missed_hint")
    private val reportEnabledKey = booleanPreferencesKey("daily_report_enabled")
    private val reportMinutesKey = intPreferencesKey("daily_report_minutes")
    private val motivationEnabledKey = booleanPreferencesKey("motivation_enabled")
    private val motivationMinutesKey = intPreferencesKey("motivation_minutes")

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[dynamicKey] ?: false,
            missedHint = p[missedKey] ?: true,
            dailyReportEnabled = p[reportEnabledKey] ?: true,
            dailyReportMinutes = p[reportMinutesKey] ?: (8 * 60),
            motivationEnabled = p[motivationEnabledKey] ?: true,
            motivationMinutes = p[motivationMinutesKey] ?: (9 * 60),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicKey] = enabled }
    }

    suspend fun setMissedHint(enabled: Boolean) {
        context.dataStore.edit { it[missedKey] = enabled }
    }

    suspend fun setDailyReportEnabled(enabled: Boolean) { context.dataStore.edit { it[reportEnabledKey] = enabled } }
    suspend fun setDailyReportMinutes(minutes: Int) { context.dataStore.edit { it[reportMinutesKey] = minutes } }
    suspend fun setMotivationEnabled(enabled: Boolean) { context.dataStore.edit { it[motivationEnabledKey] = enabled } }
    suspend fun setMotivationMinutes(minutes: Int) { context.dataStore.edit { it[motivationMinutesKey] = minutes } }
}
