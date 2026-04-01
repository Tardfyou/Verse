package com.tardfyou.paperlens.feature.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tardfyou.paperlens.core.file.DocumentImportManager
import com.tardfyou.paperlens.core.ml.OcrProcessor
import com.tardfyou.paperlens.core.model.DocumentPage
import com.tardfyou.paperlens.core.model.DocumentRepository
import com.tardfyou.paperlens.core.model.HighlightRepository
import com.tardfyou.paperlens.core.model.OcrStatus
import com.tardfyou.paperlens.core.model.ReadingProgressRepository
import com.tardfyou.paperlens.core.model.ReaderBlock
import com.tardfyou.paperlens.core.model.TextBlock
import com.tardfyou.paperlens.core.model.UserPreferencesRepository
import com.tardfyou.paperlens.core.tts.TtsController
import com.tardfyou.paperlens.core.ui.PLBottomPlayerBar
import com.tardfyou.paperlens.core.ui.PLInlineBanner
import com.tardfyou.paperlens.core.ui.PLReaderParagraph
import com.tardfyou.paperlens.core.ui.PLScaffold
import com.tardfyou.paperlens.core.ui.PLTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PdfUiState(
    val documentId: Long = 0L,
    val title: String = "",
    val sourceUri: String = "",
    val currentPageIndex: Int = 0,
    val pageCount: Int = 0,
    val pageId: Long = 0L,
    val blocks: List<TextBlock> = emptyList(),
    val isSpeaking: Boolean = false,
    val currentBlockIndex: Int = -1,
    val speechRate: Float = 1.0f,
    val pageError: String? = null,
    val qualityWarning: String? = null,
)

@HiltViewModel
class PdfViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val highlightRepository: HighlightRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val ocrProcessor: OcrProcessor,
    private val ttsController: TtsController,
) : ViewModel() {
    private val documentId: Long = checkNotNull(savedStateHandle["documentId"])
    private val currentPageIndex = MutableStateFlow(0)
    private val restoredBlockIndex = MutableStateFlow(-1)
    private val pageError = MutableStateFlow<String?>(null)

    private val contentState = combine(
        documentRepository.observeDocument(documentId),
        currentPageIndex,
        preferencesRepository.preferences,
        ttsController.playbackState,
        restoredBlockIndex,
    ) { document, pageIndex, preferences, playback, savedBlockIndex ->
        val resolvedPageIndex = pageIndex.coerceIn(0, ((document?.pageCount ?: 1) - 1).coerceAtLeast(0))
        val currentBlockIndex = playback.currentBlockIndex.takeIf { it >= 0 } ?: savedBlockIndex
        val page = document?.pages?.find { it.pageIndex == resolvedPageIndex }
        PdfUiState(
            documentId = document?.id ?: documentId,
            title = document?.title.orEmpty(),
            sourceUri = document?.sourceUri.orEmpty(),
            currentPageIndex = resolvedPageIndex,
            pageCount = document?.pageCount ?: 0,
            pageId = page?.id ?: 0L,
            blocks = page?.blocks.orEmpty(),
            isSpeaking = playback.isSpeaking,
            currentBlockIndex = currentBlockIndex,
            speechRate = preferences.speechRate,
            qualityWarning = buildOcrQualityWarning(page?.blocks.orEmpty()),
        )
    }

    val uiState: StateFlow<PdfUiState> = contentState.combine(pageError) { state, error ->
        state.copy(pageError = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PdfUiState(documentId = documentId),
    )

    init {
        viewModelScope.launch {
            readingProgressRepository.touch(documentId)
        }
        viewModelScope.launch {
            readingProgressRepository.observeProgress(documentId).collect { progress ->
                val page = progress?.currentPage ?: 0
                if (page != currentPageIndex.value) {
                    currentPageIndex.value = page
                }
                restoredBlockIndex.value = progress?.currentBlock ?: -1
            }
        }
        viewModelScope.launch {
            ttsController.playbackState
                .map { it.currentBlockIndex }
                .distinctUntilChanged()
                .collect { blockIndex ->
                    if (blockIndex >= 0) {
                        restoredBlockIndex.value = blockIndex
                        readingProgressRepository.updateProgress(
                            documentId = documentId,
                            currentPage = currentPageIndex.value,
                            currentBlock = blockIndex,
                        )
                    }
                }
        }
    }

    fun showPage(index: Int) {
        val maxPageIndex = uiState.value.pageCount
            .takeIf { it > 0 }
            ?.minus(1)
        val resolvedIndex = maxPageIndex?.let { index.coerceIn(0, it) } ?: index.coerceAtLeast(0)
        currentPageIndex.value = resolvedIndex
        restoredBlockIndex.value = -1
        pageError.value = null
        viewModelScope.launch {
            readingProgressRepository.updateProgress(
                documentId = documentId,
                currentPage = resolvedIndex,
                currentBlock = -1,
            )
        }
    }

    fun processPageBitmap(bitmap: Bitmap) {
        val snapshot = uiState.value
        if (snapshot.blocks.isNotEmpty()) return
        viewModelScope.launch {
            pageError.value = null
            runCatching {
                val result = ocrProcessor.recognizeBitmap(bitmap)
                val pageId = snapshot.pageId.takeIf { it != 0L } ?: documentRepository.upsertPage(
                    DocumentPage(
                        documentId = snapshot.documentId,
                        pageIndex = snapshot.currentPageIndex,
                        rawText = result.fullText,
                        ocrStatus = OcrStatus.Done,
                        width = bitmap.width,
                        height = bitmap.height,
                    ),
                )
                val blocks = result.blocks.mapIndexed { index, block ->
                    TextBlock(
                        pageId = pageId,
                        blockIndex = index,
                        content = block.content,
                        boundingBox = block.boundingBox,
                        confidence = block.confidence,
                    )
                }
                documentRepository.upsertPage(
                    DocumentPage(
                        id = pageId,
                        documentId = snapshot.documentId,
                        pageIndex = snapshot.currentPageIndex,
                        rawText = result.fullText,
                        ocrStatus = OcrStatus.Done,
                        width = bitmap.width,
                        height = bitmap.height,
                        blocks = blocks,
                    ),
                )
                documentRepository.replaceTextBlocks(pageId, blocks)
            }.onFailure { throwable ->
                pageError.value = throwable.message ?: "当前页文字提取失败，请重试。"
            }
        }
    }

    fun playPause() {
        val snapshot = uiState.value
        viewModelScope.launch {
            if (snapshot.isSpeaking) {
                ttsController.pause()
            } else {
                ttsController.play(
                    blocks = snapshot.blocks.map { it.content },
                    startIndex = snapshot.currentBlockIndex.takeIf { it >= 0 } ?: 0,
                    speechRate = snapshot.speechRate,
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
fun PdfRoute(
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PdfScreen(
        modifier = modifier,
        uiState = uiState,
        onPageSelected = viewModel::showPage,
        onPageBitmapRendered = viewModel::processPageBitmap,
        onPlayPause = viewModel::playPause,
        onNextBlock = viewModel::nextBlock,
        onPreviousBlock = viewModel::previousBlock,
        onLongPressBlock = viewModel::addHighlight,
    )
}

@Composable
fun PdfScreen(
    uiState: PdfUiState,
    onPageSelected: (Int) -> Unit,
    onPageBitmapRendered: (Bitmap) -> Unit,
    onPlayPause: () -> Unit,
    onNextBlock: () -> Unit,
    onPreviousBlock: () -> Unit,
    onLongPressBlock: (TextBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val importManager = remember(context) { DocumentImportManager(context) }
    var bitmap by remember(uiState.sourceUri, uiState.currentPageIndex) { mutableStateOf<Bitmap?>(null) }
    var renderAttempt by remember(uiState.sourceUri, uiState.currentPageIndex) { mutableStateOf(0) }
    var renderError by remember(uiState.sourceUri, uiState.currentPageIndex) { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.sourceUri, uiState.currentPageIndex, renderAttempt) {
        renderError = null
        bitmap = null
        uiState.sourceUri.takeIf { it.isNotBlank() }?.let {
            runCatching { importManager.renderPdfPage(it, uiState.currentPageIndex).bitmap }
                .onSuccess { rendered -> bitmap = rendered }
                .onFailure { throwable ->
                    renderError = throwable.message ?: "当前页渲染失败，请重试。"
                }
        }
    }
    LaunchedEffect(bitmap, uiState.blocks) {
        if (bitmap != null && uiState.blocks.isEmpty()) {
            onPageBitmapRendered(bitmap!!)
        }
    }

    PLScaffold(
        modifier = modifier,
        topBar = { PLTopBar(title = uiState.title.ifBlank { "PDF 阅读" }) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 980.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onPageSelected(uiState.currentPageIndex - 1) },
                            enabled = uiState.currentPageIndex > 0,
                        ) {
                            Text("上一页")
                        }
                        OutlinedButton(
                            onClick = { onPageSelected(uiState.currentPageIndex + 1) },
                            enabled = uiState.currentPageIndex < uiState.pageCount - 1,
                        ) {
                            Text("下一页")
                        }
                    }
                }
                item {
                    Text(
                        text = "第 ${uiState.currentPageIndex + 1} / ${uiState.pageCount.coerceAtLeast(1)} 页",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (uiState.qualityWarning != null) {
                    item {
                        PLInlineBanner(uiState.qualityWarning)
                    }
                }
                item {
                    when {
                        bitmap != null -> {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "PDF 页面预览",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                        renderError != null -> {
                            Text(
                                text = renderError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (renderError != null || uiState.pageError != null) {
                    item {
                        OutlinedButton(onClick = { renderAttempt += 1 }) {
                            Text("重新加载当前页")
                        }
                    }
                }
                if (uiState.blocks.isEmpty()) {
                    item {
                        val helperText = when {
                            renderError != null -> "当前页渲染失败，请重新加载。"
                            uiState.pageError != null -> uiState.pageError
                            else -> "当前页正在准备文本结果，完成后会支持段落朗读和摘录。"
                        }
                        Text(
                            helperText.orEmpty(),
                            color = if (renderError != null || uiState.pageError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                } else {
                    items(uiState.blocks, key = { it.id.takeIf { id -> id != 0L } ?: "${it.pageId}-${it.blockIndex}" }) { block ->
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
                        onPrevious = onPreviousBlock,
                        onPlayPause = onPlayPause,
                        onNext = onNextBlock,
                    )
                }
            }
        }
    }
}

private fun buildOcrQualityWarning(blocks: List<TextBlock>): String? {
    if (blocks.isEmpty()) return null
    val averageConfidence = blocks.map(TextBlock::confidence).average()
    return if (averageConfidence < 0.72f) {
        "当前页识别质量偏低，建议放大页面或重新加载后再试。"
    } else {
        null
    }
}
