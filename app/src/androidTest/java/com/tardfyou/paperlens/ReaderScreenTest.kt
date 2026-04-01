package com.tardfyou.paperlens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.tardfyou.paperlens.core.designsystem.PaperLensTheme
import com.tardfyou.paperlens.core.model.ReaderBlock
import com.tardfyou.paperlens.core.ui.PaperLensUiTags
import com.tardfyou.paperlens.feature.reader.ReaderScreen
import com.tardfyou.paperlens.feature.reader.ReaderUiState
import org.junit.Rule
import org.junit.Test

class ReaderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readerScrollsAndSupportsLongPressHighlight() {
        var highlightedBlockIndex = -1
        val blocks = (0 until 24).map { index ->
            ReaderBlock(
                id = index.toLong(),
                pageId = 1L,
                blockIndex = index,
                content = "Paragraph $index",
            )
        }

        composeRule.setContent {
            PaperLensTheme {
                ReaderScreen(
                    uiState = ReaderUiState(
                        documentId = 1L,
                        title = "Reader",
                        pageId = 1L,
                        blocks = blocks,
                    ),
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onLongPressBlock = { highlightedBlockIndex = it.blockIndex },
                    onFontScaleChanged = {},
                    onLineHeightScaleChanged = {},
                )
            }
        }

        composeRule.onNodeWithTag("${PaperLensUiTags.ReaderParagraphPrefix}23").assertIsDisplayed()
        composeRule.onNodeWithTag("${PaperLensUiTags.ReaderParagraphPrefix}0")
            .performTouchInput { longClick() }

        assertThat(highlightedBlockIndex).isEqualTo(0)
    }
}
