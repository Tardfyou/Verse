package com.tardfyou.paperlens.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tardfyou.paperlens.core.model.ContrastMode
import com.tardfyou.paperlens.core.model.DocumentRepository
import com.tardfyou.paperlens.core.model.HighlightRepository
import com.tardfyou.paperlens.core.model.ReadingProgressRepository
import com.tardfyou.paperlens.core.model.ReaderBlock
import com.tardfyou.paperlens.core.model.TtsPlaybackState
import com.tardfyou.paperlens.core.model.UserPreferencesRepository
import com.tardfyou.paperlens.core.tts.TtsController
import com.tardfyou.paperlens.core.ui.PLBottomPlayerBar
import com.tardfyou.paperlens.core.ui.PLReaderParagraph
import com.tardfyou.paperlens.core.ui.PLScaffold
import com.tardfyou.paperlens.core.ui.PLTopBar
import com.tardfyou.paperlens.core.ui.PaperLensUiTags
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

data class ReaderUiState(
    val documentId: Long = 0L,
    val title: String = "",
    val pageId: Long = 0L,
    val blocks: List<ReaderBlock> = emptyList(),
    val currentBlockIndex: Int = -1,
    val fontScale: Float = 1.0f,
    val lineHeightScale: Float = 1.0f,
    val speechRate: Float = 1.0f,
    val contrastMode: ContrastMode = ContrastMode.Standard,
    val playback: TtsPlaybackState = TtsPlaybackState(),
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    documentRepository: DocumentRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val highlightRepository: HighlightRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val ttsController: TtsController,
) : ViewModel() {
    private val documentId: Long = checkNotNull(savedStateHandle["documentId"])
    private val restoredBlockIndex = MutableStateFlow(-1)

    val uiState: StateFlow<ReaderUiState> = combine(
        documentRepository.observeDocument(documentId),
        preferencesRepository.preferences,
        ttsController.playbackState,
        restoredBlockIndex,
    ) { document, preferences, playback, savedBlockIndex ->
        val page = document?.pages?.firstOrNull()
        val currentBlockIndex = playback.currentBlockIndex.takeIf { it >= 0 } ?: savedBlockIndex
        val blocks = page?.blocks.orEmpty().map { block ->
            ReaderBlock(
                id = block.id,
                pageId = block.pageId,
                blockIndex = block.blockIndex,
                content = block.content,
                isHighlighted = block.blockIndex == currentBlockIndex,
            )
        }
        ReaderUiState(
            documentId = document?.id ?: documentId,
            title = document?.title.orEmpty(),
            pageId = page?.id ?: 0L,
            blocks = blocks,
            currentBlockIndex = currentBlockIndex,
            fontScale = preferences.fontScale,
            lineHeightScale = preferences.lineHeightScale,
            speechRate = preferences.speechRate,
            contrastMode = preferences.contrastMode,
            playback = playback,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReaderUiState(documentId = documentId),
    )

    init {
        viewModelScope.launch {
            readingProgressRepository.touch(documentId)
        }
        viewModelScope.launch {
            readingProgressRepository.observeProgress(documentId).collect { progress ->
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
                            currentPage = 0,
                            currentBlock = blockIndex,
                        )
                    }
                }
        }
    }

    fun togglePlayback() {
        val snapshot = uiState.value
        viewModelScope.launch {
            if (snapshot.playback.isSpeaking) {
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

    fun playNext() = ttsController.nextBlock()

    fun playPrevious() = ttsController.previousBlock()

    fun saveHighlight(block: ReaderBlock) {
        viewModelScope.launch {
            highlightRepository.addHighlight(
                documentId = uiState.value.documentId,
                pageId = block.pageId,
                selectedText = block.content,
            )
        }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch { preferencesRepository.setFontScale(value) }
    }

    fun setLineHeightScale(value: Float) {
        viewModelScope.launch { preferencesRepository.setLineHeightScale(value) }
    }
}

@Composable
fun ReaderRoute(
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReaderScreen(
        modifier = modifier,
        uiState = uiState,
        onPlayPause = viewModel::togglePlayback,
        onNext = viewModel::playNext,
        onPrevious = viewModel::playPrevious,
        onLongPressBlock = viewModel::saveHighlight,
        onFontScaleChanged = viewModel::setFontScale,
        onLineHeightScaleChanged = viewModel::setLineHeightScale,
    )
}

@Composable
fun ReaderScreen(
    uiState: ReaderUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLongPressBlock: (ReaderBlock) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onLineHeightScaleChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontSize = (16 * uiState.fontScale).sp
    val lineHeight = (24 * uiState.lineHeightScale).sp
    PLScaffold(
        modifier = modifier,
        topBar = { PLTopBar(title = uiState.title.ifBlank { "阅读" }) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 960.dp)
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("快速阅读样式", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = uiState.fontScale,
                        onValueChange = onFontScaleChanged,
                        valueRange = 0.9f..1.6f,
                        modifier = Modifier.testTag(PaperLensUiTags.ReaderFontScaleSlider),
                    )
                    Slider(
                        value = uiState.lineHeightScale,
                        onValueChange = onLineHeightScaleChanged,
                        valueRange = 0.9f..1.5f,
                        modifier = Modifier.testTag(PaperLensUiTags.ReaderLineHeightSlider),
                    )
                }
                items(uiState.blocks, key = { it.id }) { block ->
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalTextStyle provides TextStyle(
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                        ),
                    ) {
                        PLReaderParagraph(
                            modifier = Modifier.testTag(
                                "${PaperLensUiTags.ReaderParagraphPrefix}${block.blockIndex}",
                            ),
                            block = block,
                            onLongPress = { onLongPressBlock(block) },
                        )
                    }
                }
                item {
                    PLBottomPlayerBar(
                        modifier = Modifier.testTag(PaperLensUiTags.ReaderPlayerBar),
                        isSpeaking = uiState.playback.isSpeaking,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                    )
                }
            }
        }
    }
}
