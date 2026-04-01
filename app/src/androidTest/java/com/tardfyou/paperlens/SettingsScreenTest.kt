package com.tardfyou.paperlens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.tardfyou.paperlens.core.designsystem.PaperLensTheme
import com.tardfyou.paperlens.core.model.AppPreferences
import com.tardfyou.paperlens.core.model.DefaultProfile
import com.tardfyou.paperlens.core.ui.PaperLensUiTags
import com.tardfyou.paperlens.feature.settings.SettingsScreen
import com.tardfyou.paperlens.feature.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsTriggersContrastSpeechRateAndProfileChanges() {
        var contrastEnabled = false
        var speechRate = 1.0f
        var selectedProfile = DefaultProfile.Standard

        composeRule.setContent {
            PaperLensTheme {
                SettingsScreen(
                    uiState = SettingsUiState(preferences = AppPreferences()),
                    onFontScaleChanged = {},
                    onLineHeightScaleChanged = {},
                    onSpeechRateChanged = { speechRate = it },
                    onContrastChanged = { contrastEnabled = it },
                    onThemeModeSelected = {},
                    onProfileSelected = { selectedProfile = it },
                )
            }
        }

        composeRule.onNodeWithTag(PaperLensUiTags.SettingsContrastSwitch).performClick()
        composeRule.onNodeWithTag(PaperLensUiTags.SettingsSpeechRateSlider)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.4f) }
        composeRule.onNodeWithTag(PaperLensUiTags.SettingsProfileElder).performClick()

        assertThat(contrastEnabled).isTrue()
        assertThat(speechRate).isWithin(0.001f).of(1.4f)
        assertThat(selectedProfile).isEqualTo(DefaultProfile.Elder)
    }
}
