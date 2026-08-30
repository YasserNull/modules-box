package com.yassernull.nullbox.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// تهيئة DataStore لحفظ الإعدادات بشكل دائم.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// كلاس لإدارة حفظ واسترجاع تفضيلات المستخدم باستخدام DataStore.
class AppPreferences(internal val context: Context) {

    companion object {
        // مفاتيح لتخزين قيم الإعدادات المختلفة.
        private val THEME_KEY = stringPreferencesKey("theme_key")
        private val LANGUAGE_KEY = stringPreferencesKey("language_key")
        private val BLACK_THEME_KEY = booleanPreferencesKey("black_theme_key")
        private val MATERIAL_YOU_KEY = booleanPreferencesKey("material_you_key")
        private val HUE_SHIFT_KEY = floatPreferencesKey("hue_shift_key")
        private val SATURATION_SHIFT_KEY = floatPreferencesKey("saturation_shift_key")
    }

    suspend fun saveLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    suspend fun getLanguage(): String {
        return context.dataStore.data.map { preferences ->
            preferences[LANGUAGE_KEY] ?: AppLanguage.SYSTEM.name
        }.first()
    }

    suspend fun saveTheme(themeName: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = themeName
        }
    }

    fun getTheme(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[THEME_KEY] ?: AppTheme.SYSTEM.name
        }
    }

    suspend fun setBlackThemeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BLACK_THEME_KEY] = enabled
        }
    }

    fun isBlackThemeEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[BLACK_THEME_KEY] ?: false
        }
    }

    suspend fun setMaterialYouEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MATERIAL_YOU_KEY] = enabled
        }
    }

    fun isMaterialYouEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[MATERIAL_YOU_KEY] ?: true
        }
    }

    suspend fun saveHueShift(shift: Float) {
        context.dataStore.edit { preferences ->
            preferences[HUE_SHIFT_KEY] = shift
        }
    }

    fun getHueShift(): Flow<Float> {
        return context.dataStore.data.map { preferences ->
            preferences[HUE_SHIFT_KEY] ?: 0f
        }
    }
    suspend fun saveSaturationShift(shift: Float) {
        context.dataStore.edit { preferences ->
            preferences[SATURATION_SHIFT_KEY] = shift
        }
    }

    fun getSaturationShift(): Flow<Float> {
        return context.dataStore.data.map { preferences ->
            preferences[SATURATION_SHIFT_KEY] ?: 0f
        }
    }
}