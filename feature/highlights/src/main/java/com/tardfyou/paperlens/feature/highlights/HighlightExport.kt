package com.tardfyou.paperlens.feature.highlights

import com.tardfyou.paperlens.core.model.HighlightItem

enum class HighlightExportFormat(
    val fileExtension: String,
    val fileLabel: String,
) {
    Text(fileExtension = "txt", fileLabel = "TXT"),
    Markdown(fileExtension = "md", fileLabel = "Markdown"),
}

data class HighlightSection(
    val documentTitle: String,
    val items: List<HighlightItem>,
)

fun groupHighlightsByDocument(highlights: List<HighlightItem>): List<HighlightSection> =
    highlights
        .groupBy(HighlightItem::documentTitle)
        .map { (documentTitle, items) ->
            HighlightSection(
                documentTitle = documentTitle,
                items = items,
            )
        }

fun buildHighlightsExport(
    highlights: List<HighlightItem>,
    format: HighlightExportFormat,
): String {
    val sections = groupHighlightsByDocument(highlights)
    return when (format) {
        HighlightExportFormat.Text -> buildPlainTextExport(sections)
        HighlightExportFormat.Markdown -> buildMarkdownExport(sections)
    }.trimEnd()
}

private fun buildPlainTextExport(sections: List<HighlightSection>): String = buildString {
    appendLine("PaperLens 重点导出")
    appendLine()
    if (sections.isEmpty()) {
        appendLine("当前没有可导出的重点。")
        return@buildString
    }
    sections.forEachIndexed { sectionIndex, section ->
        appendLine(section.documentTitle)
        section.items.forEachIndexed { itemIndex, item ->
            appendLine("${itemIndex + 1}. ${item.selectedText}")
            if (item.note.isNotBlank()) {
                appendLine("备注：${item.note}")
            }
            appendLine()
        }
        if (sectionIndex != sections.lastIndex) {
            appendLine("----")
            appendLine()
        }
    }
}

private fun buildMarkdownExport(sections: List<HighlightSection>): String = buildString {
    appendLine("# PaperLens 重点导出")
    appendLine()
    if (sections.isEmpty()) {
        appendLine("当前没有可导出的重点。")
        return@buildString
    }
    sections.forEach { section ->
        appendLine("## ${section.documentTitle}")
        appendLine()
        section.items.forEach { item ->
            appendLine("- ${item.selectedText}")
            if (item.note.isNotBlank()) {
                appendLine("  - 备注：${item.note}")
            }
        }
        appendLine()
    }
}
