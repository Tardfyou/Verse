package com.tardfyou.paperlens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsPrimaryActions() {
        composeRule.onNodeWithText("\u62cd\u7167\u8bc6\u5b57").assertIsDisplayed()
        composeRule.onNodeWithText("\u5bfc\u5165 PDF / \u56fe\u7247").assertIsDisplayed()
        composeRule.onNodeWithText("\u9996\u9875").assertIsDisplayed()
        composeRule.onNodeWithText("\u8bbe\u7f6e").assertIsDisplayed()
    }

    @Test
    fun documentsTabShowsEmptyState() {
        composeRule.onNodeWithText("\u6587\u6863").performClick()
        composeRule.onNodeWithText("\u8fd8\u6ca1\u6709\u6587\u6863").assertIsDisplayed()
        composeRule.onNodeWithText("\u5bfc\u5165\u6587\u6863").assertIsDisplayed()
    }
}
