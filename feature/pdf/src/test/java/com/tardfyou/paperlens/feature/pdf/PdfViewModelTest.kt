package com.tardfyou.paperlens.feature.pdf

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.tardfyou.paperlens.core.ml.OcrProcessor
import com.tardfyou.paperlens.core.model.AppPreferences
import com.tardfyou.paperlens.core.model.BoundingBox
import com.tardfyou.paperlens.core.model.DocumentDetail
import com.tardfyou.paperlens.core.model.DocumentPage
import com.tardfyou.paperlens.core.model.DocumentRepository
import com.tardfyou.paperlens.core.model.DocumentStatus
import com.tardfyou.paperlens.core.model.DocumentSummary
import com.tardfyou.paperlens.core.model.DocumentType
import com.tardfyou.paperlens.core.model.HighlightItem
import com.tardfyou.paperlens.core.model.HighlightRepository
import com.tardfyou.paperlens.core.model.OcrBlock
import com.tardfyou.paperlens.core.model.OcrResult
import com.tardfyou.paperlens.core.model.OcrStatus
import com.tardfyou.paperlens.core.model.ReadingProgress
import com.tardfyou.paperlens.core.model.ReadingProgressRepository
import com.tardfyou.paperlens.core.model.TextBlock
import com.tardfyou.paperlens.core.model.ThemeMode
import com.tardfyou.paperlens.core.model.TtsPlaybackState
import com.tardfyou.paperlens.core.model.UserPreferencesRepository
import com.tardfyou.paperlens.core.tts.TtsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `restores saved page from reading progress`() = runTest(dispatcher) {
        val documentId = 24L
        val viewModel = PdfViewModel(
            savedStateHandle = SavedStateHandle(mapOf("documentId" to documentId)),
            documentRepository = FakePdfDocumentRepository(documentId),
            highlightRepository = FakePdfHighlightRepository(),
            readingProgressRepository = FakePdfReadingProgressRepository(
                ReadingProgress(documentId = documentId, currentPage = 2, currentBlock = 1),
            ),
            preferencesRepository = FakePdfPreferencesRepository(),
            ocrProcessor = FakeOcrProcessor(),
            ttsController = FakePdfTtsController(),
        )
        val collectionJob = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentPageIndex).isEqualTo(2)
        assertThat(viewModel.uiState.value.currentBlockIndex).isEqualTo(1)
        collectionJob.cancel()
    }

    @Test
    fun `showPage persists new progress`() = runTest(dispatcher) {
        val documentId = 24L
        val progressRepository = FakePdfReadingProgressRepository()
        val viewModel = PdfViewModel(
            savedStateHandle = SavedStateHandle(mapOf("documentId" to documentId)),
            documentRepository = FakePdfDocumentRepository(documentId),
            highlightRepository = FakePdfHighlightRepository(),
            readingProgressRepository = progressRepository,
            preferencesRepository = FakePdfPreferencesRepository(),
            ocrProcessor = FakeOcrProcessor(),
            ttsController = FakePdfTtsController(),
        )
        val collectionJob = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        viewModel.showPage(3)
        advanceUntilIdle()

        assertThat(progressRepository.updates).isNotEmpty()
        assertThat(progressRepository.updates.last().currentPage).isEqualTo(3)
        assertThat(progressRepository.updates.last().currentBlock).isEqualTo(-1)
        collectionJob.cancel()
    }
}

private class FakePdfDocumentRepository(documentId: Long) : DocumentRepository {
    private val document = DocumentDetail(
        id = documentId,
        title = "PDF 文档",
        type = DocumentType.Pdf,
        sourceUri = "content://sample.pdf",
        pageCount = 4,
        status = DocumentStatus.Ready,
        pages = listOf(
            createPage(documentId = documentId, pageId = 1L, pageIndex = 0, text = "第 1 页"),
            createPage(documentId = documentId, pageId = 2L, pageIndex = 2, text = "第 3 页"),
            createPage(documentId = documentId, pageId = 3L, pageIndex = 3, text = "第 4 页"),
        ),
    )

    override fun observeDocuments(): Flow<List<DocumentSummary>> = flowOf(emptyList())

    override fun observeRecentDocuments(limit: Int): Flow<List<DocumentSummary>> = flowOf(emptyList())

    override fun observeDocument(documentId: Long): Flow<DocumentDetail?> = flowOf(document)

    override suspend fun createImageDocument(title: String, imagePath: String, sourceUri: String): Long = 0L

    override suspend fun createPdfDocument(title: String, sourceUri: String, pageCount: Int): Long = 0L

    override suspend fun updateDocumentStatus(documentId: Long, status: DocumentStatus) = Unit

    override suspend fun upsertPage(page: DocumentPage): Long = page.id

    override suspend fun replaceTextBlocks(pageId: Long, blocks: List<TextBlock>) = Unit

    private fun createPage(
        documentId: Long,
        pageId: Long,
        pageIndex: Int,
        text: String,
    ): DocumentPage = DocumentPage(
        id = pageId,
        documentId = documentId,
        pageIndex = pageIndex,
        rawText = text,
        ocrStatus = OcrStatus.Done,
        blocks = listOf(
            TextBlock(
                id = pageId,
                pageId = pageId,
                blockIndex = 0,
                content = text,
            ),
        ),
    )
}

private class FakePdfHighlightRepository : HighlightRepository {
    override fun observeHighlights(query: String): Flow<List<HighlightItem>> = flowOf(emptyList())

    override suspend fun addHighlight(
        documentId: Long,
        pageId: Long,
        selectedText: String,
        note: String,
    ): Long = 0L

    override suspend fun updateHighlightNote(highlightId: Long, note: String) = Unit
}

private class FakePdfReadingProgressRepository(
    initialProgress: ReadingProgress? = null,
) : ReadingProgressRepository {
    private val progressFlow = MutableStateFlow(initialProgress)
    val updates = mutableListOf<ReadingProgress>()

    override fun observeProgress(documentId: Long): Flow<ReadingProgress?> = progressFlow

    override suspend fun touch(documentId: Long) = Unit

    override suspend fun updateProgress(
        documentId: Long,
        currentPage: Int,
        currentBlock: Int,
        charOffset: Int,
    ) {
        val updated = ReadingProgress(
            documentId = documentId,
            currentPage = currentPage,
            currentBlock = currentBlock,
            charOffset = charOffset,
        )
        updates += updated
        progressFlow.value = updated
    }
}

private class FakePdfPreferencesRepository : UserPreferencesRepository {
    private val preferencesFlow = MutableStateFlow(AppPreferences(themeMode = ThemeMode.System))

    override val preferences: Flow<AppPreferences> = preferencesFlow

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setContrastMode(mode: com.tardfyou.paperlens.core.model.ContrastMode) = Unit

    override suspend fun setDefaultProfile(profile: com.tardfyou.paperlens.core.model.DefaultProfile) = Unit

    override suspend fun setFontScale(value: Float) = Unit

    override suspend fun setLineHeightScale(value: Float) = Unit

    override suspend fun setSpeechRate(value: Float) = Unit
}

private class FakeOcrProcessor : OcrProcessor {
    override suspend fun recognizeImage(uri: android.net.Uri): OcrResult = OcrResult(
        fullText = "stub",
        blocks = listOf(
            OcrBlock(
                content = "stub",
                boundingBox = BoundingBox(0f, 0f, 1f, 1f),
            ),
        ),
    )

    override suspend fun recognizeBitmap(bitmap: Bitmap): OcrResult = OcrResult(
        fullText = "stub",
        blocks = listOf(
            OcrBlock(
                content = "stub",
                boundingBox = BoundingBox(0f, 0f, 1f, 1f),
            ),
        ),
    )
}

private class FakePdfTtsController : TtsController {
    private val playbackFlow = MutableStateFlow(TtsPlaybackState())

    override val playbackState: StateFlow<TtsPlaybackState> = playbackFlow.asStateFlow()

    override suspend fun play(blocks: List<String>, startIndex: Int, speechRate: Float) = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun nextBlock() = Unit

    override fun previousBlock() = Unit

    override fun updateSpeechRate(value: Float) = Unit

    override fun release() = Unit
}
