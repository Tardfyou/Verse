package com.tardfyou.paperlens.core.model

import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class DefaultProfile {
    Standard,
    Elder,
    Student,
}

data class ProfilePreset(
    val fontScale: Float,
    val lineHeightScale: Float,
    val contrastMode: ContrastMode,
    val speechRate: Float,
)

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val contrastMode: ContrastMode = ContrastMode.Standard,
    val defaultProfile: DefaultProfile = DefaultProfile.Standard,
    val fontScale: Float = 1.0f,
    val lineHeightScale: Float = 1.0f,
    val speechRate: Float = 1.0f,
)

interface UserPreferencesRepository {
    val preferences: Flow<AppPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setContrastMode(mode: ContrastMode)

    suspend fun setDefaultProfile(profile: DefaultProfile)

    suspend fun setFontScale(value: Float)

    suspend fun setLineHeightScale(value: Float)

    suspend fun setSpeechRate(value: Float)
}

fun DefaultProfile.toPreset(): ProfilePreset = when (this) {
    DefaultProfile.Standard -> ProfilePreset(
        fontScale = 1.0f,
        lineHeightScale = 1.0f,
        contrastMode = ContrastMode.Standard,
        speechRate = 1.0f,
    )
    DefaultProfile.Elder -> ProfilePreset(
        fontScale = 1.35f,
        lineHeightScale = 1.25f,
        contrastMode = ContrastMode.High,
        speechRate = 0.95f,
    )
    DefaultProfile.Student -> ProfilePreset(
        fontScale = 1.1f,
        lineHeightScale = 1.15f,
        contrastMode = ContrastMode.Standard,
        speechRate = 1.05f,
    )
}
