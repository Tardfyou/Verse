package com.tardfyou.paperlens.feature.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tardfyou.paperlens.core.designsystem.PaperLensTheme
import com.tardfyou.paperlens.core.model.AppPreferences
import com.tardfyou.paperlens.core.model.ContrastMode
import com.tardfyou.paperlens.core.model.DefaultProfile
import com.tardfyou.paperlens.core.model.ThemeMode
import com.tardfyou.paperlens.core.model.UserPreferencesRepository
import com.tardfyou.paperlens.core.model.toPreset
import com.tardfyou.paperlens.core.tts.TtsController
import com.tardfyou.paperlens.core.ui.PLCenteredPane
import com.tardfyou.paperlens.core.ui.PLInlineBanner
import com.tardfyou.paperlens.core.ui.PaperLensUiTags
import com.tardfyou.paperlens.core.ui.PLScaffold
import com.tardfyou.paperlens.core.ui.PLSettingRow
import com.tardfyou.paperlens.core.ui.PLTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserPreferencesRepository,
    private val ttsController: TtsController,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = repository.preferences
        .map(::SettingsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setFontScale(value: Float) = update { repository.setFontScale(value) }

    fun setLineHeightScale(value: Float) = update { repository.setLineHeightScale(value) }

    fun setSpeechRate(value: Float) = update {
        repository.setSpeechRate(value)
        ttsController.updateSpeechRate(value)
    }

    fun setContrast(enabled: Boolean) = update {
        repository.setContrastMode(if (enabled) ContrastMode.High else ContrastMode.Standard)
    }

    fun setThemeMode(mode: ThemeMode) = update { repository.setThemeMode(mode) }

    fun setProfile(profile: DefaultProfile) = update {
        repository.setDefaultProfile(profile)
        val preset = profile.toPreset()
        repository.setFontScale(preset.fontScale)
        repository.setLineHeightScale(preset.lineHeightScale)
        repository.setContrastMode(preset.contrastMode)
        repository.setSpeechRate(preset.speechRate)
        ttsController.updateSpeechRate(preset.speechRate)
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        modifier = modifier,
        uiState = uiState,
        onFontScaleChanged = viewModel::setFontScale,
        onLineHeightScaleChanged = viewModel::setLineHeightScale,
        onSpeechRateChanged = viewModel::setSpeechRate,
        onContrastChanged = viewModel::setContrast,
        onThemeModeSelected = viewModel::setThemeMode,
        onProfileSelected = viewModel::setProfile,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onFontScaleChanged: (Float) -> Unit,
    onLineHeightScaleChanged: (Float) -> Unit,
    onSpeechRateChanged: (Float) -> Unit,
    onContrastChanged: (Boolean) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onProfileSelected: (DefaultProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = uiState.preferences
    val context = LocalContext.current
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            writeBackupToUri(
                context = context,
                uri = it,
                content = buildSettingsBackup(preferences),
            )
        }
    }
    PLScaffold(
        modifier = modifier,
        topBar = { PLTopBar(title = "\u8bbe\u7f6e") },
    ) {
        PLCenteredPane(maxWidth = 920.dp) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PaperLensTheme.spacing.lg),
                contentPadding = PaddingValues(vertical = PaperLensTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.md),
            ) {
                item {
                    PLInlineBanner("\u8bbe\u7f6e\u9875\u53ea\u4fdd\u7559\u9605\u8bfb\u548c\u6717\u8bfb\u76f8\u5173\u7684\u6838\u5fc3\u63a7\u5236\uff0c\u4e0d\u66b4\u9732\u5e95\u5c42\u566a\u97f3\u3002")
                }
                item {
                    PLSettingRow(
                        title = "\u9ad8\u5bf9\u6bd4\u6a21\u5f0f",
                        subtitle = "\u589e\u5f3a\u6587\u5b57\u4e0e\u80cc\u666f\u7684\u533a\u5206\u5ea6\u3002",
                        trailing = {
                            Switch(
                                modifier = Modifier.testTag(PaperLensUiTags.SettingsContrastSwitch),
                                checked = preferences.contrastMode == ContrastMode.High,
                                onCheckedChange = onContrastChanged,
                            )
                        },
                    )
                }
                item {
                    SettingsSliderRow(
                        title = "\u5b57\u53f7",
                        value = preferences.fontScale,
                        valueText = "${(preferences.fontScale * 100).toInt()}%",
                        valueRange = 0.9f..1.6f,
                        onValueChanged = onFontScaleChanged,
                        modifier = Modifier.testTag(PaperLensUiTags.SettingsFontScaleSlider),
                    )
                }
                item {
                    SettingsSliderRow(
                        title = "\u884c\u8ddd",
                        value = preferences.lineHeightScale,
                        valueText = "${(preferences.lineHeightScale * 100).toInt()}%",
                        valueRange = 0.9f..1.5f,
                        onValueChanged = onLineHeightScaleChanged,
                        modifier = Modifier.testTag(PaperLensUiTags.SettingsLineHeightSlider),
                    )
                }
                item {
                    SettingsSliderRow(
                        title = "\u6717\u8bfb\u8bed\u901f",
                        value = preferences.speechRate,
                        valueText = String.format("%.1fx", preferences.speechRate),
                        valueRange = 0.6f..1.6f,
                        onValueChanged = onSpeechRateChanged,
                        modifier = Modifier.testTag(PaperLensUiTags.SettingsSpeechRateSlider),
                    )
                }
                item {
                    SettingsChoiceRow(
                        title = "\u4e3b\u9898\u6a21\u5f0f",
                        current = preferences.themeMode,
                        options = ThemeMode.entries,
                        label = ThemeMode::displayName,
                        modifierForOption = ThemeMode::toTag,
                        onSelected = onThemeModeSelected,
                    )
                }
                item {
                    SettingsChoiceRow(
                        title = "\u9ed8\u8ba4\u6a21\u5f0f",
                        current = preferences.defaultProfile,
                        options = DefaultProfile.entries,
                        label = DefaultProfile::displayName,
                        modifierForOption = DefaultProfile::toTag,
                        onSelected = onProfileSelected,
                    )
                }
                item {
                    PLSettingRow(
                        title = "\u5bfc\u51fa\u672c\u5730\u8bbe\u7f6e\u5907\u4efd",
                        subtitle = "\u628a\u4e3b\u9898\u3001\u5bf9\u6bd4\u5ea6\u3001\u5b57\u53f7\u3001\u884c\u8ddd\u548c\u8bed\u901f\u4fdd\u5b58\u4e3a\u672c\u5730 JSON \u6587\u4ef6\u3002",
                        onClick = { backupLauncher.launch("paperlens-settings-${LocalDate.now()}.json") },
                    )
                }
                item {
                    PLInlineBanner("\u9690\u79c1\u58f0\u660e\uff1a\u62cd\u7167\u3001OCR\u3001\u6717\u8bfb\u548c\u91cd\u70b9\u6570\u636e\u9ed8\u8ba4\u53ea\u4fdd\u5b58\u5728\u672c\u673a\uff0c\u4e0d\u4f1a\u4e0a\u4f20\u5230\u7f51\u7edc\u3002")
                }
                item {
                    PLSettingRow(
                        title = "\u65e0\u969c\u788d\u5e2e\u52a9",
                        subtitle = "\u5efa\u8bae\u7ed3\u5408\u7cfb\u7edf\u5927\u5b57\u53f7\u3001TalkBack \u548c\u9ad8\u5bf9\u6bd4\u6a21\u5f0f\u4f7f\u7528\uff0c\u4e3b\u6d41\u7a0b\u6309\u94ae\u4fdd\u6301 48dp \u4ee5\u4e0a\u70b9\u6309\u533a\u57df\u3002",
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            modifier = modifier,
            value = value,
            onValueChange = onValueChanged,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun <T> SettingsChoiceRow(
    title: String,
    current: T,
    options: List<T>,
    label: (T) -> String,
    modifierForOption: (T) -> Modifier,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PaperLensTheme.spacing.sm),
        ) {
            options.forEach { option ->
                AssistChip(
                    modifier = modifierForOption(option),
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (option == current) {
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

private fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.System -> "\u8ddf\u968f\u7cfb\u7edf"
    ThemeMode.Light -> "\u6d45\u8272"
    ThemeMode.Dark -> "\u6df1\u8272"
}

private fun ThemeMode.toTag(): Modifier = Modifier.testTag(
    when (this) {
        ThemeMode.System -> PaperLensUiTags.SettingsThemeSystem
        ThemeMode.Light -> PaperLensUiTags.SettingsThemeLight
        ThemeMode.Dark -> PaperLensUiTags.SettingsThemeDark
    },
)

private fun DefaultProfile.displayName(): String = when (this) {
    DefaultProfile.Standard -> "\u6807\u51c6"
    DefaultProfile.Elder -> "\u957f\u8005"
    DefaultProfile.Student -> "\u5b66\u751f"
}

private fun DefaultProfile.toTag(): Modifier = Modifier.testTag(
    when (this) {
        DefaultProfile.Standard -> PaperLensUiTags.SettingsProfileStandard
        DefaultProfile.Elder -> PaperLensUiTags.SettingsProfileElder
        DefaultProfile.Student -> PaperLensUiTags.SettingsProfileStudent
    },
)

private fun writeBackupToUri(
    context: Context,
    uri: Uri,
    content: String,
) {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
        writer.write(content)
    }
}
