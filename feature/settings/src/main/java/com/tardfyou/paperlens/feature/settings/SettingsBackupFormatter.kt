package com.tardfyou.paperlens.feature.settings

import com.tardfyou.paperlens.core.model.AppPreferences

fun buildSettingsBackup(preferences: AppPreferences): String = buildString {
    appendLine("{")
    appendLine("  \"themeMode\": \"${preferences.themeMode.name}\",")
    appendLine("  \"contrastMode\": \"${preferences.contrastMode.name}\",")
    appendLine("  \"defaultProfile\": \"${preferences.defaultProfile.name}\",")
    appendLine("  \"fontScale\": ${preferences.fontScale},")
    appendLine("  \"lineHeightScale\": ${preferences.lineHeightScale},")
    appendLine("  \"speechRate\": ${preferences.speechRate}")
    appendLine("}")
}
