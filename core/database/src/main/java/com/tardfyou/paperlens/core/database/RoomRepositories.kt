package com.tardfyou.paperlens.core.database

import com.tardfyou.paperlens.core.common.TimeProvider
import com.tardfyou.paperlens.core.model.BoundingBox
import com.tardfyou.paperlens.core.model.DocumentDetail
import com.tardfyou.paperlens.core.model.DocumentPage
import com.tardfyou.paperlens.core.model.DocumentRepository
import com.tardfyou.paperlens.core.model.DocumentStatus
import com.tardfyou.paperlens.core.model.DocumentSummary
import com.tardfyou.paperlens.core.model.DocumentType
import com.tardfyou.paperlens.core.model.HighlightItem
import com.tardfyou.paperlens.core.model.HighlightRepository
import com.tardfyou.paperlens.core.model.ReadingProgress
import com.tardfyou.paperlens.core.model.ReadingProgressRepository
import com.tardfyou.paperlens.core.model.TextBlock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RoomDocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val textBlockDao: TextBlockDao,
) : DocumentRepository {
    override fun observeDocuments(): Flow<List<DocumentSummary>> =
        documentDao.observeDocuments().map { items -> items.map(DocumentEntity::toSummary) }

    override fun observeRecentDocuments(limit: Int): Flow<List<DocumentSummary>> =
        documentDao.observeRecentDocuments(limit).map { items -> items.map(DocumentEntity::toSummary) }

    override fun observeDocument(documentId: Long): Flow<DocumentDetail?> =
        combine(
            documentDao.observeDocument(documentId),
            pageDao.observePages(documentId),
        ) { document, pages -> document to pages }
            .flatMapLatest { (document, pages) ->
                if (document == null) {
                    flowOf(null)
                } else if (pages.isEmpty()) {
                    flowOf(document.toDetail(emptyList()))
                } else {
                    combine(
                        pages.map { page ->
                            textBlockDao.observeBlocks(page.id)
                                .map { blocks -> page.toModel(blocks.map(TextBlockEntity::toModel)) }
                        },
                    ) { pageModels ->
                        document.toDetail(pageModels.toList())
                    }
                }
            }

    override suspend fun createImageDocument(
        title: String,
        imagePath: String,
        sourceUri: String,
    ): Long {
        val now = System.currentTimeMillis()
        val documentId = documentDao.insert(
            DocumentEntity(
                title = title,
                type = DocumentType.Image,
                sourceUri = sourceUri,
                pageCount = 1,
                status = DocumentStatus.Draft,
                createdAt = now,
                updatedAt = now,
            ),
        )
        pageDao.insert(
            PageEntity(
                documentId = documentId,
                pageIndex = 0,
                imagePath = imagePath,
            ),
        )
        return documentId
    }

    override suspend fun createPdfDocument(
        title: String,
        sourceUri: String,
        pageCount: Int,
    ): Long {
        val now = System.currentTimeMillis()
        return documentDao.insert(
            DocumentEntity(
                title = title,
                type = DocumentType.Pdf,
                sourceUri = sourceUri,
                pageCount = pageCount.coerceAtLeast(1),
                status = DocumentStatus.Draft,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun updateDocumentStatus(documentId: Long, status: DocumentStatus) {
        documentDao.updateStatus(
            documentId = documentId,
            status = status,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun upsertPage(page: DocumentPage): Long {
        val existing = when {
            page.id != 0L -> pageDao.getPage(page.id)
            else -> pageDao.getPageByIndex(page.documentId, page.pageIndex)
        }
        val entity = PageEntity(
            id = existing?.id ?: page.id,
            documentId = page.documentId,
            pageIndex = page.pageIndex,
            imagePath = page.imagePath,
            rawText = page.rawText,
            ocrStatus = page.ocrStatus,
            width = page.width,
            height = page.height,
        )
        val pageId = if (existing == null && page.id == 0L) {
            pageDao.insert(entity)
        } else {
            val resolvedId = existing?.id ?: page.id
            pageDao.update(entity.copy(id = resolvedId))
            resolvedId
        }
        updateDocumentPreview(page.documentId, page.rawText, page.blocks)
        return pageId
    }

    override suspend fun replaceTextBlocks(pageId: Long, blocks: List<TextBlock>) {
        textBlockDao.deleteForPage(pageId)
        if (blocks.isNotEmpty()) {
            textBlockDao.insertAll(blocks.map { block -> block.toEntity(pageId) })
        }
        val page = pageDao.getPage(pageId) ?: return
        updateDocumentPreview(page.documentId, page.rawText, blocks)
    }

    private suspend fun updateDocumentPreview(
        documentId: Long,
        rawText: String,
        blocks: List<TextBlock>,
    ) {
        val preview = rawText.ifBlank { blocks.firstOrNull()?.content.orEmpty() }
            .replace('\n', ' ')
            .trim()
            .take(120)
        documentDao.updatePreview(
            documentId = documentId,
            previewText = preview,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

@Singleton
class RoomHighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao,
) : HighlightRepository {
    override fun observeHighlights(query: String): Flow<List<HighlightItem>> =
        highlightDao.observeHighlights(query.trim()).map { rows ->
            rows.map { row ->
                HighlightItem(
                    id = row.id,
                    documentId = row.documentId,
                    pageId = row.pageId,
                    documentTitle = row.documentTitle,
                    selectedText = row.selectedText,
                    note = row.note,
                    createdAt = row.createdAt,
                )
            }
        }

    override suspend fun addHighlight(
        documentId: Long,
        pageId: Long,
        selectedText: String,
        note: String,
    ): Long = highlightDao.insert(
        HighlightEntity(
            documentId = documentId,
            pageId = pageId,
            selectedText = selectedText,
            note = note,
        ),
    )

    override suspend fun updateHighlightNote(highlightId: Long, note: String) {
        highlightDao.updateNote(highlightId, note)
    }
}

@Singleton
class RoomReadingProgressRepository @Inject constructor(
    private val readingProgressDao: ReadingProgressDao,
    private val documentDao: DocumentDao,
    private val timeProvider: TimeProvider,
) : ReadingProgressRepository {
    override fun observeProgress(documentId: Long): Flow<ReadingProgress?> =
        readingProgressDao.observeProgress(documentId).map { entity -> entity?.toModel() }

    override suspend fun touch(documentId: Long) {
        val now = timeProvider.now()
        val existing = readingProgressDao.getProgress(documentId)
        readingProgressDao.upsert(
            ReadingProgressEntity(
                documentId = documentId,
                currentPage = existing?.currentPage ?: 0,
                currentBlock = existing?.currentBlock ?: -1,
                charOffset = existing?.charOffset ?: 0,
                lastOpenedAt = now,
            ),
        )
        documentDao.touch(documentId = documentId, updatedAt = now)
    }

    override suspend fun updateProgress(
        documentId: Long,
        currentPage: Int,
        currentBlock: Int,
        charOffset: Int,
    ) {
        readingProgressDao.upsert(
            ReadingProgressEntity(
                documentId = documentId,
                currentPage = currentPage.coerceAtLeast(0),
                currentBlock = currentBlock,
                charOffset = charOffset.coerceAtLeast(0),
                lastOpenedAt = timeProvider.now(),
            ),
        )
        documentDao.touch(documentId = documentId, updatedAt = timeProvider.now())
    }
}

private fun DocumentEntity.toSummary(): DocumentSummary =
    DocumentSummary(
        id = id,
        title = title,
        type = type,
        previewText = previewText,
        sourceUri = sourceUri,
        pageCount = pageCount,
        status = status,
    )

private fun DocumentEntity.toDetail(pages: List<DocumentPage>): DocumentDetail =
    DocumentDetail(
        id = id,
        title = title,
        type = type,
        sourceUri = sourceUri,
        pageCount = pageCount,
        status = status,
        pages = pages,
    )

private fun PageEntity.toModel(blocks: List<TextBlock>): DocumentPage =
    DocumentPage(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        imagePath = imagePath,
        rawText = rawText,
        ocrStatus = ocrStatus,
        width = width,
        height = height,
        blocks = blocks,
    )

private fun TextBlockEntity.toModel(): TextBlock =
    TextBlock(
        id = id,
        pageId = pageId,
        blockIndex = blockIndex,
        content = content,
        boundingBox = if (left != null && top != null && right != null && bottom != null) {
            BoundingBox(left = left, top = top, right = right, bottom = bottom)
        } else {
            null
        },
        confidence = confidence,
    )

private fun TextBlock.toEntity(pageId: Long): TextBlockEntity =
    TextBlockEntity(
        id = if (id == 0L) 0L else id,
        pageId = pageId,
        blockIndex = blockIndex,
        content = content,
        left = boundingBox?.left,
        top = boundingBox?.top,
        right = boundingBox?.right,
        bottom = boundingBox?.bottom,
        confidence = confidence,
    )

private fun ReadingProgressEntity.toModel(): ReadingProgress =
    ReadingProgress(
        documentId = documentId,
        currentPage = currentPage,
        currentBlock = currentBlock,
        charOffset = charOffset,
        lastOpenedAt = lastOpenedAt,
    )
