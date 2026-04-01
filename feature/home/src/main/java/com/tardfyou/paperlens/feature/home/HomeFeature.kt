package com.tardfyou.paperlens.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tardfyou.paperlens.core.designsystem.PaperLensTheme
import com.tardfyou.paperlens.core.model.DefaultProfile
import com.tardfyou.paperlens.core.model.DocumentRepository
import com.tardfyou.paperlens.core.model.DocumentSummary
import com.tardfyou.paperlens.core.model.UserPreferencesRepository
import com.tardfyou.paperlens.core.model.toPreset
import com.tardfyou.paperlens.core.ui.PLDocumentCard
import com.tardfyou.paperlens.core.ui.PLEmptyState
import com.tardfyou.paperlens.core.ui.PLInlineBanner
import com.tardfyou.paperlens.core.ui.PLPrimaryButton
import com.tardfyou.paperlens.core.ui.PLScaffold
import com.tardfyou.paperlens.core.ui.PLSecondaryButton
import com.tardfyou.paperlens.core.ui.PaperLensUiTags
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentDocuments: List<DocumentSummary> = emptyList(),
    val currentProfile: DefaultProfile = DefaultProfile.Standard,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        documentRepository.observeRecentDocuments(),
        preferencesRepository.preferences,
    ) { documents, preferences ->
        HomeUiState(
            recentDocuments = documents,
            currentProfile = preferences.defaultProfile,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun updateProfile(profile: DefaultProfile) {
        viewModelScope.launch {
            preferencesRepository.setDefaultProfile(profile)
            val preset = profile.toPreset()
            preferencesRepository.setFontScale(preset.fontScale)
            preferencesRepository.setLineHeightScale(preset.lineHeightScale)
            preferencesRepository.setContrastMode(preset.contrastMode)
            preferencesRepository.setSpeechRate(preset.speechRate)
        }
    }
}

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    onCaptureClick: () -> Unit,
    onImportClick: () -> Unit,
    onDocumentClick: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        modifier = modifier,
        uiState = uiState,
        onCaptureClick = onCaptureClick,
        onImportClick = onImportClick,
        onDocumentClick = onDocumentClick,
        onProfileSelected = viewModel::updateProfile,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onCaptureClick: () -> Unit,
    onImportClick: () -> Unit,
    onDocumentClick: (Long) -> Unit,
    onProfileSelected: (DefaultProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    PLScaffold(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 920.dp)
                    .padding(horizontal = PaperLensTheme.spacing.lg),
                contentPadding = PaddingValues(vertical = PaperLensTheme.spacing.giant),
                verticalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.lg),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.sm)) {
                        Text("PaperLens", style = MaterialTheme.typography.displaySmall)
                        Text(
                            "\u79bb\u7ebf\u62cd\u7167\u8bc6\u5b57\u3001PDF \u9605\u8bfb\u548c\u6717\u8bfb\u5de5\u5177\u3002",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.md)) {
                        PLPrimaryButton(
                            text = "\u62cd\u7167\u8bc6\u5b57",
                            onClick = onCaptureClick,
                            modifier = Modifier.testTag(PaperLensUiTags.HomeCaptureButton),
                        )
                        PLSecondaryButton(
                            text = "\u5bfc\u5165 PDF / \u56fe\u7247",
                            onClick = onImportClick,
                            modifier = Modifier.testTag(PaperLensUiTags.HomeImportButton),
                        )
                    }
                }
                item {
                    PLInlineBanner("\u6838\u5fc3\u80fd\u529b\u9ed8\u8ba4\u5b8c\u5168\u79bb\u7ebf\uff0c\u6587\u6863\u4e0e\u8bc6\u522b\u7ed3\u679c\u53ea\u4fdd\u5b58\u5728\u672c\u673a\u3002")
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.sm)) {
                        Text("\u5feb\u901f\u6a21\u5f0f", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.sm)) {
                            DefaultProfile.entries.forEach { profile ->
                                AssistChip(
                                    modifier = Modifier.testTag(profile.toTag()),
                                    onClick = { onProfileSelected(profile) },
                                    label = { Text(profile.displayName()) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (profile == uiState.currentProfile) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                item {
                    Text("\u6700\u8fd1\u6587\u6863", style = MaterialTheme.typography.titleMedium)
                }
                if (uiState.recentDocuments.isEmpty()) {
                    item {
                        PLEmptyState(
                            title = "\u8fd8\u6ca1\u6709\u6587\u6863",
                            description = "\u62cd\u4e00\u5f20\u7eb8\u6216\u5bfc\u5165\u4e00\u4e2a PDF\uff0c\u4e3b\u6d41\u7a0b\u4f1a\u81ea\u52a8\u6c89\u6dc0\u5230\u8fd9\u91cc\u3002",
                        )
                    }
                } else {
                    items(uiState.recentDocuments, key = { it.id }) { document ->
                        PLDocumentCard(document = document, onClick = { onDocumentClick(document.id) })
                    }
                }
            }
        }
    }
}

private fun DefaultProfile.displayName(): String = when (this) {
    DefaultProfile.Standard -> "\u6807\u51c6"
    DefaultProfile.Elder -> "\u957f\u8005"
    DefaultProfile.Student -> "\u5b66\u751f"
}

private fun DefaultProfile.toTag(): String = when (this) {
    DefaultProfile.Standard -> PaperLensUiTags.HomeProfileStandard
    DefaultProfile.Elder -> PaperLensUiTags.HomeProfileElder
    DefaultProfile.Student -> PaperLensUiTags.HomeProfileStudent
}
