package com.tardfyou.paperlens.feature.highlights

import com.google.common.truth.Truth.assertThat
import com.tardfyou.paperlens.core.model.HighlightItem
import org.junit.Test

class HighlightExportTest {
    @Test
    fun `groupHighlightsByDocument preserves first-seen document order and item order`() {
        val sections = groupHighlightsByDocument(sampleHighlights())

        assertThat(sections).hasSize(2)
        assertThat(sections.map(HighlightSection::documentTitle))
            .containsExactly("数学讲义", "英语笔记")
            .inOrder()
        assertThat(sections[0].items.map(HighlightItem::selectedText))
            .containsExactly("勾股定理", "三角函数")
            .inOrder()
        assertThat(sections[1].items.map(HighlightItem::selectedText))
            .containsExactly("present perfect")
    }

    @Test
    fun `buildHighlightsExport renders plain text with titles notes and separators`() {
        val export = buildHighlightsExport(
            highlights = sampleHighlights(),
            format = HighlightExportFormat.Text,
        )

        assertThat(export).isEqualTo(
            """
            PaperLens 重点导出

            数学讲义
            1. 勾股定理
            备注：考试会考

            2. 三角函数

            ----

            英语笔记
            1. present perfect
            """.trimIndent().trimEnd(),
        )
    }

    @Test
    fun `buildHighlightsExport renders markdown structure with notes`() {
        val export = buildHighlightsExport(
            highlights = sampleHighlights(),
            format = HighlightExportFormat.Markdown,
        )

        assertThat(export).isEqualTo(
            """
            # PaperLens 重点导出

            ## 数学讲义

            - 勾股定理
              - 备注：考试会考
            - 三角函数

            ## 英语笔记

            - present perfect
            """.trimIndent().trimEnd(),
        )
    }
}

private fun sampleHighlights(): List<HighlightItem> = listOf(
    HighlightItem(
        id = 1L,
        documentId = 10L,
        pageId = 101L,
        documentTitle = "数学讲义",
        selectedText = "勾股定理",
        note = "考试会考",
    ),
    HighlightItem(
        id = 2L,
        documentId = 11L,
        pageId = 201L,
        documentTitle = "英语笔记",
        selectedText = "present perfect",
    ),
    HighlightItem(
        id = 3L,
        documentId = 10L,
        pageId = 102L,
        documentTitle = "数学讲义",
        selectedText = "三角函数",
    ),
)
