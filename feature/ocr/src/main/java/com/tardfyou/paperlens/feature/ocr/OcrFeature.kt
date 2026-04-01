package com.tardfyou.paperlens.feature.ocr

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tardfyou.paperlens.core.ml.OcrProcessor
import com.tardfyou.paperlens.core.model.DocumentDetail
import com.tardfyou.paperlens.core.model.DocumentPage
import com.tardfyou.paperlens.core.model.DocumentRepository
import com.tardfyou.paperlens.core.model.DocumentStatus
import com.tardfyou.paperlens.core.model.HighlightRepository
import com.tardfyou.paperlens.core.model.OcrStatus
import com.tardfyou.paperlens.core.model.ReadingProgressRepository
import com.tardfyou.paperlens.core.model.ReaderBlock
import com.tardfyou.paperlens.core.model.TextBlock
import com.tardfyou.paperlens.core.model.UserPreferencesRepository
import com.tardfyou.paperlens.core.ui.PLBottomPlayerBar
import com.tardfyou.paperlens.core.ui.PLCenteredPane
import com.tardfyou.paperlens.core.ui.PLInlineBanner
import com.tardfyou.paperlens.core.ui.PLReaderParagraph
import com.tardfyou.paperlens.core.ui.PLScaffold
import com.tardfyou.paperlens.core.ui.PLTopBar
import com.tardfyou.paperlens.core.tts.TtsController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OcrTab {
    Image,
    Text,
}

data class OcrUiState(
    val documentId: Long = 0L,
    val title: String = "",
    val imagePath: String = "",
    val pageId: Long = 0L,
    val blocks: List<TextBlock> = emptyList(),
    val selectedTab: OcrTab = OcrTab.Text,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSpeaking: Boolean = false,
    val currentBlockIndex: Int = -1,
    val speechRate: Float = 1.0f,
    val qualityWarning: String? = null,
)

private data class OcrSessionState(
    val document: DocumentDetail? = null,
    val selectedTab: OcrTab = OcrTab.Text,
    val error: String? = null,
    val isLoading: Boolean = true,
    val qualityWarning: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OcrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val highlightRepository: HighlightRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val ocrProcessor: OcrProcessor,
    private val ttsController: TtsController,
) : ViewModel() {
    private val imagePath = checkNotNull(savedStateHandle.get<String>("imagePath"))
    private val documentIdFlow = MutableStateFlow(0L)
    private val selectedTab = MutableStateFlow(OcrTab.Text)
    private val localError = MutableStateFlow<String?>(null)
    private val loading = MutableStateFlow(true)
    private val qualityWarning = MutableStateFlow<String?>(null)

    private val sessionState = combine(
        documentIdFlow.flatMapLatest { id ->
            if (id == 0L) flowOf(null) else documentRepository.observeDocument(id)
        },
        selectedTab,
        localError,
        loading,
        qualityWarning,
    ) { document, tab, error, isLoading, warning ->
        OcrSessionState(
            document = document,
            selectedTab = tab,
            error = error,
            isLoading = isLoading,
            qualityWarning = warning,
        )
    }

    val uiState: StateFlow<OcrUiState> = combine(
        sessionState,
        preferencesRepository.preferences,
        ttsController.playbackState,
    ) { session, preferences, playback ->
        val page = session.document?.pages?.firstOrNull()
        OcrUiState(
            documentId = session.document?.id ?: documentIdFlow.value,
            title = session.document?.title.orEmpty(),
            imagePath = imagePath,
            pageId = page?.id ?: 0L,
            blocks = page?.blocks.orEmpty(),
            selectedTab = session.selectedTab,
            isLoading = session.isLoading,
            error = session.error,
            isSpeaking = playback.isSpeaking,
            currentBlockIndex = playback.currentBlockIndex,
            speechRate = preferences.speechRate,
            qualityWarning = session.qualityWarning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OcrUiState(imagePath = imagePath),
    )

    init {
        viewModelScope.launch {
            runCatching {
                val title = File(imagePath).nameWithoutExtension.ifBlank {
                    "\u62cd\u7167\u6587\u6863"
                }
                val documentId = documentRepository.createImageDocument(
                    title = title,
                    imagePath = imagePath,
                    sourceUri = Uri.fromFile(File(imagePath)).toString(),
                )
                documentIdFlow.value = documentId
                readingProgressRepository.touch(documentId)
                documentRepository.updateDocumentStatus(documentId, DocumentStatus.Processing)

                val document = documentRepository.observeDocument(documentId)
                    .filterNotNull()
                    .first()
                val page = requireNotNull(document.pages.firstOrNull())
                val result = ocrProcessor.recognizeImage(imagePath.toUri())
                val blocks = result.blocks.mapIndexed { index, block ->
                    TextBlock(
                        pageId = page.id,
                        blockIndex = index,
                        content = block.content,
                        boundingBox = block.boundingBox,
                        confidence = block.confidence,
                    )
                }

                qualityWarning.value = buildOcrQualityWarning(blocks)
                documentRepository.upsertPage(
                    DocumentPage(
                        id = page.id,
                        documentId = documentId,
                        pageIndex = page.pageIndex,
                        imagePath = imagePath,
                        rawText = result.fullText,
                        ocrStatus = OcrStatus.Done,
                        blocks = blocks,
                    ),
                )
                documentRepository.replaceTextBlocks(page.id, blocks)
                documentRepository.updateDocumentStatus(documentId, DocumentStatus.Ready)
                localError.value = null
                loading.value = false
            }.onFailure { throwable ->
                documentIdFlow.value.takeIf { it != 0L }?.let { documentId ->
                    documentRepository.updateDocumentStatus(documentId, DocumentStatus.Failed)
                }
                qualityWarning.value = null
                localError.value = throwable.message ?: "\u79bb\u7ebf OCR \u8bc6\u522b\u5931\u8d25\u3002"
                loading.value = false
            }
        }

        viewModelScope.launch {
            ttsController.playbackState
                .map { it.currentBlockIndex }
                .distinctUntilChanged()
                .collect { blockIndex ->
                    val documentId = documentIdFlow.value
                    if (documentId != 0L && blockIndex >= 0) {
                        readingProgressRepository.updateProgress(
                            documentId = documentId,
                            currentPage = 0,
                            currentBlock = blockIndex,
                        )
                    }
                }
        }
    }

    fun setSelectedTab(tab: OcrTab) {
        selectedTab.value = tab
    }

    fun playPause(blocks: List<TextBlock>) {
        viewModelScope.launch {
            if (ttsController.playbackState.value.isSpeaking) {
                ttsController.pause()
            } else {
                ttsController.play(
                    blocks = blocks.map(TextBlock::content),
                    startIndex = uiState.value.currentBlockIndex.takeIf { it >= 0 } ?: 0,
                    speechRate = uiState.value.speechRate,
                )
            }
        }
    }

    fun nextBlock() = ttsController.nextBlock()

    fun previousBlock() = ttsController.previousBlock()

    fun addHighlight(block: TextBlock) {
        viewModelScope.launch {
            highlightRepository.addHighlight(
                documentId = uiState.value.documentId,
                pageId = block.pageId,
                selectedText = block.content,
            )
        }
    }
}

@Composable
fun OcrRoute(
    modifier: Modifier = Modifier,
    viewModel: OcrViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OcrScreen(
        modifier = modifier,
        uiState = uiState,
        onTabSelected = viewModel::setSelectedTab,
        onPlayPause = { viewModel.playPause(uiState.blocks) },
        onNext = viewModel::nextBlock,
        onPrevious = viewModel::previousBlock,
        onLongPressBlock = viewModel::addHighlight,
    )
}

@Composable
fun OcrScreen(
    uiState: OcrUiState,
    onTabSelected: (OcrTab) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLongPressBlock: (TextBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    PLScaffold(
        modifier = modifier,
        topBar = {
            PLTopBar(
                title = uiState.title.ifBlank { "OCR \u7ed3\u679c" },
            )
        },
    ) {
        PLCenteredPane(
            modifier = Modifier.fillMaxSize(),
            maxWidth = 960.dp,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                        OcrTab.entries.forEach { tab ->
                            Tab(
                                selected = uiState.selectedTab == tab,
                                onClick = { onTabSelected(tab) },
                                text = {
                                    Text(
                                        if (tab == OcrTab.Image) {
                                            "\u539f\u56fe"
                                        } else {
                                            "\u6587\u672c"
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                if (uiState.qualityWarning != null && !uiState.isLoading && uiState.error == null) {
                    item {
                        PLInlineBanner(uiState.qualityWarning)
                    }
                }
                item {
                    when {
                        uiState.isLoading -> {
                            Text(
                                text = "\u6b63\u5728\u8fdb\u884c\u79bb\u7ebf\u8bc6\u522b...",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        uiState.error != null -> {
                            Text(
                                text = uiState.error,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        uiState.selectedTab == OcrTab.Image -> {
                            val bitmap = remember(uiState.imagePath) {
                                BitmapFactory.decodeFile(uiState.imagePath)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "\u8bc6\u522b\u539f\u56fe",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.FillWidth,
                                )
                            } else {
                                Text(
                                    text = "\u539f\u56fe\u52a0\u8f7d\u5931\u8d25\u3002",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        else -> {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "\u6587\u672c\u5df2\u6309\u6bb5\u843d\u6574\u7406\uff0c\u53ef\u4ee5\u76f4\u63a5\u6717\u8bfb\u6216\u957f\u6309\u6536\u85cf\u3002",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (uiState.selectedTab == OcrTab.Text) {
                    items(
                        items = uiState.blocks,
                        key = { block ->
                            block.id.takeIf { it != 0L } ?: "${block.pageId}-${block.blockIndex}"
                        },
                    ) { block ->
                        PLReaderParagraph(
                            block = ReaderBlock(
                                id = block.id,
                                pageId = block.pageId,
                                blockIndex = block.blockIndex,
                                content = block.content,
                                isHighlighted = block.blockIndex == uiState.currentBlockIndex,
                            ),
                            onLongPress = { onLongPressBlock(block) },
                        )
                    }
                }
                item {
                    PLBottomPlayerBar(
                        isSpeaking = uiState.isSpeaking,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                    )
                }
            }
        }
    }
}

private fun buildOcrQualityWarning(blocks: List<TextBlock>): String? {
    if (blocks.isEmpty()) return null
    val averageConfidence = blocks.map(TextBlock::confidence).average()
    return if (averageConfidence < 0.72) {
        "\u8bc6\u522b\u8d28\u91cf\u504f\u4f4e\uff0c\u5efa\u8bae\u91cd\u62cd\u6216\u8c03\u6574\u89d2\u5ea6\u3001\u5149\u7ebf\u540e\u518d\u8bd5\u3002"
    } else {
        null
    }
}
