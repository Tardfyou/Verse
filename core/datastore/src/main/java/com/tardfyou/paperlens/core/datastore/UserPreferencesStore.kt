package com.tardfyou.paperlens.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tardfyou.paperlens.core.model.AppPreferences
import com.tardfyou.paperlens.core.model.ContrastMode
import com.tardfyou.paperlens.core.model.DefaultProfile
import com.tardfyou.paperlens.core.model.ThemeMode
import com.tardfyou.paperlens.core.model.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore by preferencesDataStore(name = "paperlens_preferences")

private object PreferenceKeys {
    val themeMode = stringPreferencesKey("theme_mode")
    val contrastMode = stringPreferencesKey("contrast_mode")
    val defaultProfile = stringPreferencesKey("default_profile")
    val fontScale = floatPreferencesKey("font_scale")
    val lineHeightScale = floatPreferencesKey("line_height_scale")
    val speechRate = floatPreferencesKey("speech_rate")
}

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferencesRepository {
    override val preferences: Flow<AppPreferences> = context.userPreferencesDataStore.data.map { prefs ->
        AppPreferences(
            themeMode = prefs[PreferenceKeys.themeMode]
                ?.let(ThemeMode::valueOf)
                ?: ThemeMode.System,
            contrastMode = prefs[PreferenceKeys.contrastMode]
                ?.let(ContrastMode::valueOf)
                ?: ContrastMode.Standard,
            defaultProfile = prefs[PreferenceKeys.defaultProfile]
                ?.let(DefaultProfile::valueOf)
                ?: DefaultProfile.Standard,
            fontScale = prefs[PreferenceKeys.fontScale] ?: 1.0f,
            lineHeightScale = prefs[PreferenceKeys.lineHeightScale] ?: 1.0f,
            speechRate = prefs[PreferenceKeys.speechRate] ?: 1.0f,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.userPreferencesDataStore.edit { it[PreferenceKeys.themeMode] = mode.name }
    }

    override suspend fun setContrastMode(mode: ContrastMode) {
        context.userPreferencesDataStore.edit { it[PreferenceKeys.contrastMode] = mode.name }
    }

    override suspend fun setDefaultProfile(profile: DefaultProfile) {
        context.userPreferencesDataStore.edit { it[PreferenceKeys.defaultProfile] = profile.name }
    }

    override suspend fun setFontScale(value: Float) {
        context.userPreferencesDataStore.edit { it[PreferenceKeys.fontScale] = value }
    }

    override suspend fun setLineHeightScale(value: Float) {
        context.userPreferencesDataStore.edit { it[PreferenceKeys.lineHeightScale] = value }
    }

    override suspend fun setSpeechRate(value: Float) {
        context.userPreferencesDataStore.edit { it[PreferenceKeys.speechRate] = value }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreBindingsModule {
    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: DataStoreUserPreferencesRepository,
    ): UserPreferencesRepository
}
