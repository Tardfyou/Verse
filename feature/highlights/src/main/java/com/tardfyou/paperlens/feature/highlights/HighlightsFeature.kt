package com.tardfyou.paperlens.feature.highlights

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tardfyou.paperlens.core.designsystem.PaperLensTheme
import com.tardfyou.paperlens.core.model.HighlightItem
import com.tardfyou.paperlens.core.model.HighlightRepository
import com.tardfyou.paperlens.core.ui.PLEmptyState
import com.tardfyou.paperlens.core.ui.PLHighlightRow
import com.tardfyou.paperlens.core.ui.PLScaffold
import com.tardfyou.paperlens.core.ui.PLTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HighlightsUiState(
    val query: String = "",
    val highlights: List<HighlightItem> = emptyList(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HighlightsViewModel @Inject constructor(
    private val repository: HighlightRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<HighlightsUiState> = query
        .flatMapLatest { currentQuery ->
            repository.observeHighlights(currentQuery)
                .combine(query) { highlights, value ->
                    HighlightsUiState(query = value, highlights = highlights)
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HighlightsUiState(),
        )

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateNote(highlightId: Long, note: String) {
        viewModelScope.launch {
            repository.updateHighlightNote(highlightId, note)
        }
    }
}

@Composable
fun HighlightsRoute(
    modifier: Modifier = Modifier,
    viewModel: HighlightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HighlightsScreen(
        modifier = modifier,
        uiState = uiState,
        onQueryChanged = viewModel::updateQuery,
        onNoteChanged = viewModel::updateNote,
    )
}

@Composable
fun HighlightsScreen(
    uiState: HighlightsUiState,
    onQueryChanged: (String) -> Unit,
    onNoteChanged: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sections = remember(uiState.highlights) { groupHighlightsByDocument(uiState.highlights) }
    val exportTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri?.let {
            writeTextToUri(
                context = context,
                uri = it,
                content = buildHighlightsExport(
                    highlights = uiState.highlights,
                    format = HighlightExportFormat.Text,
                ),
            )
        }
    }
    val exportMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        uri?.let {
            writeTextToUri(
                context = context,
                uri = it,
                content = buildHighlightsExport(
                    highlights = uiState.highlights,
                    format = HighlightExportFormat.Markdown,
                ),
            )
        }
    }

    PLScaffold(
        modifier = modifier,
        topBar = { PLTopBar(title = "重点") },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PaperLensTheme.spacing.lg),
            contentPadding = PaddingValues(vertical = PaperLensTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.md),
        ) {
            item {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索重点") },
                    singleLine = true,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.sm)) {
                    OutlinedButton(
                        enabled = uiState.highlights.isNotEmpty(),
                        onClick = {
                            exportTextLauncher.launch(
                                "paperlens-highlights-${LocalDate.now()}.${HighlightExportFormat.Text.fileExtension}",
                            )
                        },
                    ) {
                        Text("导出 TXT")
                    }
                    OutlinedButton(
                        enabled = uiState.highlights.isNotEmpty(),
                        onClick = {
                            exportMarkdownLauncher.launch(
                                "paperlens-highlights-${LocalDate.now()}.${HighlightExportFormat.Markdown.fileExtension}",
                            )
                        },
                    ) {
                        Text("导出 Markdown")
                    }
                }
            }
            if (uiState.highlights.isEmpty()) {
                item {
                    PLEmptyState(
                        title = "还没有摘录",
                        description = "在 OCR 结果页或 Reader 页面长按一段文字，就能把重点存到这里。",
                    )
                }
            } else {
                sections.forEach { section ->
                    item(key = "header-${section.documentTitle}") {
                        Text(
                            text = section.documentTitle,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(section.items, key = { it.id }) { item ->
                        HighlightCard(
                            item = item,
                            onNoteChanged = onNoteChanged,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightCard(
    item: HighlightItem,
    onNoteChanged: (Long, String) -> Unit,
) {
    var note by rememberSaveable(item.id) { mutableStateOf(item.note) }
    Column(verticalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.sm)) {
        PLHighlightRow(item = item, showDocumentTitle = false)
        OutlinedTextField(
            value = note,
            onValueChange = {
                note = it
                onNoteChanged(item.id, it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("备注") },
        )
    }
}

private fun writeTextToUri(
    context: Context,
    uri: Uri,
    content: String,
) {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
        writer.write(content)
    }
}
