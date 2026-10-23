package dev.pdv.yamulite.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore("settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val QUALITY = stringPreferencesKey("quality")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val quality: Flow<Quality> = context.settingsDataStore.data.map { p ->
        Quality.entries.firstOrNull { it.name == p[Keys.QUALITY] } ?: Quality.High
    }

    suspend fun setQuality(q: Quality) {
        context.settingsDataStore.edit { it[Keys.QUALITY] = q.name }
    }

    suspend fun currentQuality(): Quality = quality.first()

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { p ->
        ThemeMode.entries.firstOrNull { it.name == p[Keys.THEME_MODE] } ?: ThemeMode.System
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}
