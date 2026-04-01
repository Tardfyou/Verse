package com.tardfyou.paperlens.core.model

import kotlinx.coroutines.flow.Flow

enum class DocumentType {
    Image,
    Pdf,
}

enum class DocumentStatus {
    Draft,
    Processing,
    Ready,
    Failed,
}

enum class OcrStatus {
    Pending,
    Processing,
    Done,
    Failed,
}

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class TextBlock(
    val id: Long = 0L,
    val pageId: Long,
    val blockIndex: Int,
    val content: String,
    val boundingBox: BoundingBox? = null,
    val confidence: Float = 0f,
)

data class DocumentPage(
    val id: Long = 0L,
    val documentId: Long,
    val pageIndex: Int,
    val imagePath: String? = null,
    val rawText: String = "",
    val ocrStatus: OcrStatus = OcrStatus.Pending,
    val width: Int = 0,
    val height: Int = 0,
    val blocks: List<TextBlock> = emptyList(),
)

data class DocumentSummary(
    val id: Long,
    val title: String,
    val type: DocumentType,
    val previewText: String = "",
    val sourceUri: String = "",
    val pageCount: Int = 1,
    val status: DocumentStatus = DocumentStatus.Draft,
)

data class DocumentDetail(
    val id: Long,
    val title: String,
    val type: DocumentType,
    val sourceUri: String,
    val pageCount: Int,
    val status: DocumentStatus,
    val pages: List<DocumentPage> = emptyList(),
)

data class HighlightItem(
    val id: Long,
    val documentId: Long,
    val pageId: Long,
    val documentTitle: String,
    val selectedText: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class ReadingProgress(
    val documentId: Long,
    val currentPage: Int = 0,
    val currentBlock: Int = -1,
    val charOffset: Int = 0,
    val lastOpenedAt: Long = System.currentTimeMillis(),
)

interface DocumentRepository {
    fun observeDocuments(): Flow<List<DocumentSummary>>

    fun observeRecentDocuments(limit: Int = 10): Flow<List<DocumentSummary>>

    fun observeDocument(documentId: Long): Flow<DocumentDetail?>

    suspend fun createImageDocument(
        title: String,
        imagePath: String,
        sourceUri: String,
    ): Long

    suspend fun createPdfDocument(
        title: String,
        sourceUri: String,
        pageCount: Int,
    ): Long

    suspend fun updateDocumentStatus(
        documentId: Long,
        status: DocumentStatus,
    )

    suspend fun upsertPage(page: DocumentPage): Long

    suspend fun replaceTextBlocks(
        pageId: Long,
        blocks: List<TextBlock>,
    )
}

interface HighlightRepository {
    fun observeHighlights(query: String = ""): Flow<List<HighlightItem>>

    suspend fun addHighlight(
        documentId: Long,
        pageId: Long,
        selectedText: String,
        note: String = "",
    ): Long

    suspend fun updateHighlightNote(
        highlightId: Long,
        note: String,
    )
}

interface ReadingProgressRepository {
    fun observeProgress(documentId: Long): Flow<ReadingProgress?>

    suspend fun touch(documentId: Long)

    suspend fun updateProgress(
        documentId: Long,
        currentPage: Int,
        currentBlock: Int,
        charOffset: Int = 0,
    )
}
