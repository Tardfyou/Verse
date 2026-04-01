package com.tardfyou.paperlens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.tardfyou.paperlens.core.designsystem.PaperLensTheme
import com.tardfyou.paperlens.core.ui.PaperLensUiTags
import com.tardfyou.paperlens.feature.camera.CameraScreen
import com.tardfyou.paperlens.feature.camera.CameraUiState
import java.io.File
import org.junit.Rule
import org.junit.Test

class CameraScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cameraWithoutPermissionShowsGuidance() {
        composeRule.setContent {
            PaperLensTheme {
                CameraScreen(
                    uiState = CameraUiState(hasPermission = false),
                    onBack = {},
                    onCapture = {},
                    createFile = { File("capture.jpg") },
                    onError = {},
                )
            }
        }

        composeRule.onNodeWithTag(PaperLensUiTags.CameraPermissionState).assertIsDisplayed()
    }
}
