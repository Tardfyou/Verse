package com.tardfyou.paperlens.core.model

enum class ContrastMode {
    Standard,
    High,
}

data class ReaderBlock(
    val id: Long,
    val pageId: Long,
    val blockIndex: Int,
    val content: String,
    val isHighlighted: Boolean = false,
)
