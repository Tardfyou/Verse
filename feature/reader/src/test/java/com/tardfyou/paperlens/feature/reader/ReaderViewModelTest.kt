package com.tardfyou.paperlens.feature.reader

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.tardfyou.paperlens.core.model.AppPreferences
import com.tardfyou.paperlens.core.model.BoundingBox
import com.tardfyou.paperlens.core.model.ContrastMode
import com.tardfyou.paperlens.core.model.DefaultProfile
import com.tardfyou.paperlens.core.model.DocumentDetail
import com.tardfyou.paperlens.core.model.DocumentPage
import com.tardfyou.paperlens.core.model.DocumentRepository
import com.tardfyou.paperlens.core.model.DocumentStatus
import com.tardfyou.paperlens.core.model.DocumentSummary
import com.tardfyou.paperlens.core.model.DocumentType
import com.tardfyou.paperlens.core.model.HighlightItem
import com.tardfyou.paperlens.core.model.HighlightRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_restoresSavedBlock_andUsesPreferenceSpeechRate() = runTest(dispatcher) {
        val documentId = 42L
        val documentRepository = FakeDocumentRepository(documentId)
        val preferencesRepository = FakeUserPreferencesRepository(
            AppPreferences(
                themeMode = ThemeMode.System,
                contrastMode = ContrastMode.High,
                defaultProfile = DefaultProfile.Standard,
                fontScale = 1.2f,
                lineHeightScale = 1.1f,
                speechRate = 1.4f,
            ),
        )
        val highlightRepository = FakeHighlightRepository()
        val progressRepository = FakeReadingProgressRepository(
            ReadingProgress(documentId = documentId, currentPage = 0, currentBlock = 1),
        )
        val ttsController = FakeTtsController()
        val viewModel = ReaderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("documentId" to documentId)),
            documentRepository = documentRepository,
            preferencesRepository = preferencesRepository,
            highlightRepository = highlightRepository,
            readingProgressRepository = progressRepository,
            ttsController = ttsController,
        )

        val collectionJob = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentBlockIndex).isEqualTo(1)
        assertThat(viewModel.uiState.value.speechRate).isEqualTo(1.4f)

        viewModel.togglePlayback()
        advanceUntilIdle()

        assertThat(ttsController.lastPlayRequest).isNotNull()
        assertThat(ttsController.lastPlayRequest?.startIndex).isEqualTo(1)
        assertThat(ttsController.lastPlayRequest?.speechRate).isEqualTo(1.4f)

        collectionJob.cancel()
    }

    @Test
    fun playbackProgress_updatesReadingProgressRepository() = runTest(dispatcher) {
        val documentId = 7L
        val documentRepository = FakeDocumentRepository(documentId)
        val preferencesRepository = FakeUserPreferencesRepository(AppPreferences())
        val progressRepository = FakeReadingProgressRepository(null)
        val ttsController = FakeTtsController()
        val viewModel = ReaderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("documentId" to documentId)),
            documentRepository = documentRepository,
            preferencesRepository = preferencesRepository,
            highlightRepository = FakeHighlightRepository(),
            readingProgressRepository = progressRepository,
            ttsController = ttsController,
        )

        val collectionJob = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        ttsController.emitPlaybackState(
            TtsPlaybackState(
                isSpeaking = true,
                currentBlockIndex = 2,
                speechRate = 1.0f,
            ),
        )
        advanceUntilIdle()

        assertThat(progressRepository.updates).hasSize(1)
        assertThat(progressRepository.updates.single().documentId).isEqualTo(documentId)
        assertThat(progressRepository.updates.single().currentPage).isEqualTo(0)
        assertThat(progressRepository.updates.single().currentBlock).isEqualTo(2)
        assertThat(progressRepository.updates.single().charOffset).isEqualTo(0)

        collectionJob.cancel()
    }
}

private class FakeDocumentRepository(documentId: Long) : DocumentRepository {
    private val documentFlow = MutableStateFlow(
        DocumentDetail(
            id = documentId,
            title = "测试文档",
            type = DocumentType.Image,
            sourceUri = "file:///tmp/test.jpg",
            pageCount = 1,
            status = DocumentStatus.Ready,
            pages = listOf(
                DocumentPage(
                    id = 100L,
                    documentId = documentId,
                    pageIndex = 0,
                    rawText = "第一段 第二段",
                    ocrStatus = OcrStatus.Done,
                    blocks = listOf(
                        TextBlock(
                            id = 1L,
                            pageId = 100L,
                            blockIndex = 0,
                            content = "第一段",
                            boundingBox = BoundingBox(0f, 0f, 10f, 10f),
                            confidence = 0.99f,
                        ),
                        TextBlock(
                            id = 2L,
                            pageId = 100L,
                            blockIndex = 1,
                            content = "第二段",
                            boundingBox = BoundingBox(0f, 10f, 10f, 20f),
                            confidence = 0.98f,
                        ),
                    ),
                ),
            ),
        ),
    )

    override fun observeDocuments(): Flow<List<DocumentSummary>> = MutableStateFlow(emptyList())

    override fun observeRecentDocuments(limit: Int): Flow<List<DocumentSummary>> = MutableStateFlow(emptyList())

    override fun observeDocument(documentId: Long): Flow<DocumentDetail?> = documentFlow

    override suspend fun createImageDocument(title: String, imagePath: String, sourceUri: String): Long {
        error("Not needed in test.")
    }

    override suspend fun createPdfDocument(title: String, sourceUri: String, pageCount: Int): Long {
        error("Not needed in test.")
    }

    override suspend fun updateDocumentStatus(documentId: Long, status: DocumentStatus) = Unit

    override suspend fun upsertPage(page: DocumentPage): Long = page.id

    override suspend fun replaceTextBlocks(pageId: Long, blocks: List<TextBlock>) = Unit
}

private class FakeUserPreferencesRepository(initial: AppPreferences) : UserPreferencesRepository {
    private val state = MutableStateFlow(initial)

    override val preferences: Flow<AppPreferences> = state.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setContrastMode(mode: ContrastMode) {
        state.update { it.copy(contrastMode = mode) }
    }

    override suspend fun setDefaultProfile(profile: DefaultProfile) {
        state.update { it.copy(defaultProfile = profile) }
    }

    override suspend fun setFontScale(value: Float) {
        state.update { it.copy(fontScale = value) }
    }

    override suspend fun setLineHeightScale(value: Float) {
        state.update { it.copy(lineHeightScale = value) }
    }

    override suspend fun setSpeechRate(value: Float) {
        state.update { it.copy(speechRate = value) }
    }
}

private class FakeHighlightRepository : HighlightRepository {
    override fun observeHighlights(query: String): Flow<List<HighlightItem>> = MutableStateFlow(emptyList())

    override suspend fun addHighlight(
        documentId: Long,
        pageId: Long,
        selectedText: String,
        note: String,
    ): Long = 1L

    override suspend fun updateHighlightNote(highlightId: Long, note: String) = Unit
}

private class FakeReadingProgressRepository(initial: ReadingProgress?) : ReadingProgressRepository {
    private val state = MutableStateFlow(initial)
    val updates = mutableListOf<ReadingProgress>()

    override fun observeProgress(documentId: Long): Flow<ReadingProgress?> = state.asStateFlow()

    override suspend fun touch(documentId: Long) {
        state.value = state.value ?: ReadingProgress(documentId = documentId)
    }

    override suspend fun updateProgress(
        documentId: Long,
        currentPage: Int,
        currentBlock: Int,
        charOffset: Int,
    ) {
        val progress = ReadingProgress(
            documentId = documentId,
            currentPage = currentPage,
            currentBlock = currentBlock,
            charOffset = charOffset,
        )
        updates += progress
        state.value = progress
    }
}

private class FakeTtsController : TtsController {
    data class PlayRequest(
        val blocks: List<String>,
        val startIndex: Int,
        val speechRate: Float,
    )

    private val state = MutableStateFlow(TtsPlaybackState())
    var lastPlayRequest: PlayRequest? = null

    override val playbackState: StateFlow<TtsPlaybackState> = state.asStateFlow()

    override suspend fun play(blocks: List<String>, startIndex: Int, speechRate: Float) {
        lastPlayRequest = PlayRequest(blocks = blocks, startIndex = startIndex, speechRate = speechRate)
        state.value = TtsPlaybackState(
            isSpeaking = true,
            currentBlockIndex = startIndex,
            speechRate = speechRate,
        )
    }

    override fun pause() {
        state.value = state.value.copy(isSpeaking = false)
    }

    override fun stop() {
        state.value = TtsPlaybackState()
    }

    override fun nextBlock() = Unit

    override fun previousBlock() = Unit

    override fun updateSpeechRate(value: Float) {
        state.value = state.value.copy(speechRate = value)
    }

    override fun release() = Unit

    fun emitPlaybackState(value: TtsPlaybackState) {
        state.value = value
    }
}
