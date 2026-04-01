package com.tardfyou.paperlens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.tardfyou.paperlens.core.designsystem.PaperLensTheme
import com.tardfyou.paperlens.core.ui.PaperLensUiTags
import com.tardfyou.paperlens.feature.home.HomeScreen
import com.tardfyou.paperlens.feature.home.HomeUiState
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeShowsPrimaryCtas() {
        var captureClicked = false
        var importClicked = false

        composeRule.setContent {
            PaperLensTheme {
                HomeScreen(
                    uiState = HomeUiState(),
                    onCaptureClick = { captureClicked = true },
                    onImportClick = { importClicked = true },
                    onDocumentClick = {},
                    onProfileSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("PaperLens").assertIsDisplayed()
        composeRule.onNodeWithTag(PaperLensUiTags.HomeCaptureButton).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(PaperLensUiTags.HomeImportButton).assertIsDisplayed().performClick()

        assertThat(captureClicked).isTrue()
        assertThat(importClicked).isTrue()
    }
}
