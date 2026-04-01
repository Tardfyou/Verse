package com.tardfyou.paperlens.feature.settings

import com.google.common.truth.Truth.assertThat
import com.tardfyou.paperlens.core.model.AppPreferences
import com.tardfyou.paperlens.core.model.ContrastMode
import com.tardfyou.paperlens.core.model.DefaultProfile
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsFeatureTest {
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
    fun `setProfile applies preset and syncs tts speech rate`() = runTest(dispatcher) {
        val repository = FakeSettingsPreferencesRepository()
        val ttsController = FakeSettingsTtsController()
        val viewModel = SettingsViewModel(repository, ttsController)

        viewModel.setProfile(DefaultProfile.Elder)
        advanceUntilIdle()

        val preferences = repository.preferencesState.value
        assertThat(preferences.defaultProfile).isEqualTo(DefaultProfile.Elder)
        assertThat(preferences.fontScale).isEqualTo(1.35f)
        assertThat(preferences.lineHeightScale).isEqualTo(1.25f)
        assertThat(preferences.contrastMode).isEqualTo(ContrastMode.High)
        assertThat(preferences.speechRate).isEqualTo(0.95f)
        assertThat(ttsController.lastSpeechRate).isEqualTo(0.95f)
    }

    @Test
    fun `buildSettingsBackup renders stable json payload`() {
        val backup = buildSettingsBackup(
            AppPreferences(
                themeMode = ThemeMode.Dark,
                contrastMode = ContrastMode.High,
                defaultProfile = DefaultProfile.Student,
                fontScale = 1.1f,
                lineHeightScale = 1.15f,
                speechRate = 1.05f,
            ),
        )

        assertThat(backup).contains("\"themeMode\": \"Dark\"")
        assertThat(backup).contains("\"contrastMode\": \"High\"")
        assertThat(backup).contains("\"defaultProfile\": \"Student\"")
        assertThat(backup).contains("\"speechRate\": 1.05")
    }
}

private class FakeSettingsPreferencesRepository : UserPreferencesRepository {
    val preferencesState = MutableStateFlow(AppPreferences())

    override val preferences: Flow<AppPreferences> = preferencesState

    override suspend fun setThemeMode(mode: ThemeMode) {
        preferencesState.value = preferencesState.value.copy(themeMode = mode)
    }

    override suspend fun setContrastMode(mode: ContrastMode) {
        preferencesState.value = preferencesState.value.copy(contrastMode = mode)
    }

    override suspend fun setDefaultProfile(profile: DefaultProfile) {
        preferencesState.value = preferencesState.value.copy(defaultProfile = profile)
    }

    override suspend fun setFontScale(value: Float) {
        preferencesState.value = preferencesState.value.copy(fontScale = value)
    }

    override suspend fun setLineHeightScale(value: Float) {
        preferencesState.value = preferencesState.value.copy(lineHeightScale = value)
    }

    override suspend fun setSpeechRate(value: Float) {
        preferencesState.value = preferencesState.value.copy(speechRate = value)
    }
}

private class FakeSettingsTtsController : TtsController {
    private val playbackFlow = MutableStateFlow(TtsPlaybackState())

    var lastSpeechRate: Float = 1.0f

    override val playbackState: StateFlow<TtsPlaybackState> = playbackFlow.asStateFlow()

    override suspend fun play(blocks: List<String>, startIndex: Int, speechRate: Float) = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun nextBlock() = Unit

    override fun previousBlock() = Unit

    override fun updateSpeechRate(value: Float) {
        lastSpeechRate = value
        playbackFlow.value = playbackFlow.value.copy(speechRate = value)
    }

    override fun release() = Unit
}
