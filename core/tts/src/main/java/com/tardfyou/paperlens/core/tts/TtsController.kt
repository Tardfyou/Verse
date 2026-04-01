package com.tardfyou.paperlens.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.tardfyou.paperlens.core.model.TtsPlaybackState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

interface TtsController {
    val playbackState: StateFlow<TtsPlaybackState>

    suspend fun play(blocks: List<String>, startIndex: Int, speechRate: Float)

    fun pause()

    fun stop()

    fun nextBlock()

    fun previousBlock()

    fun updateSpeechRate(value: Float)

    fun release()
}

@Singleton
class AndroidTtsController @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _playbackState = MutableStateFlow(TtsPlaybackState())
    override val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var queue: List<String> = emptyList()
    private var currentIndex: Int = -1

    override suspend fun play(blocks: List<String>, startIndex: Int, speechRate: Float) {
        if (blocks.isEmpty()) return
        queue = blocks
        currentIndex = startIndex.coerceIn(0, blocks.lastIndex)
        val engine = ensureEngine()
        engine.setSpeechRate(speechRate)
        _playbackState.value = TtsPlaybackState(
            isSpeaking = true,
            currentBlockIndex = currentIndex,
            speechRate = speechRate,
        )
        speakCurrent(engine)
    }

    override fun pause() {
        tts?.stop()
        _playbackState.value = _playbackState.value.copy(isSpeaking = false)
    }

    override fun stop() {
        tts?.stop()
        currentIndex = -1
        queue = emptyList()
        _playbackState.value = _playbackState.value.copy(
            isSpeaking = false,
            currentBlockIndex = -1,
        )
    }

    override fun nextBlock() {
        if (queue.isEmpty()) return
        currentIndex = (currentIndex + 1).coerceAtMost(queue.lastIndex)
        scope.launch {
            ensureEngine().let(::speakCurrent)
        }
    }

    override fun previousBlock() {
        if (queue.isEmpty()) return
        currentIndex = (currentIndex - 1).coerceAtLeast(0)
        scope.launch {
            ensureEngine().let(::speakCurrent)
        }
    }

    override fun updateSpeechRate(value: Float) {
        tts?.setSpeechRate(value)
        _playbackState.value = _playbackState.value.copy(speechRate = value)
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private suspend fun ensureEngine(): TextToSpeech {
        tts?.let { return it }
        val engine = suspendCancellableCoroutine<TextToSpeech> { continuation ->
            var created: TextToSpeech? = null
            created = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    continuation.resume(created!!)
                } else {
                    continuation.resume(created!!)
                }
            }
        }
        engine.language = Locale.getDefault()
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _playbackState.value = _playbackState.value.copy(
                        isSpeaking = true,
                        currentBlockIndex = currentIndex,
                    )
                }

                override fun onDone(utteranceId: String?) {
                    if (currentIndex >= queue.lastIndex) {
                        _playbackState.value = _playbackState.value.copy(isSpeaking = false)
                    } else {
                        currentIndex += 1
                        scope.launch {
                            speakCurrent(engine)
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    _playbackState.value = _playbackState.value.copy(isSpeaking = false)
                }
            },
        )
        tts = engine
        return engine
    }

    private fun speakCurrent(engine: TextToSpeech) {
        if (currentIndex !in queue.indices) return
        _playbackState.value = _playbackState.value.copy(
            isSpeaking = true,
            currentBlockIndex = currentIndex,
        )
        engine.stop()
        engine.speak(
            queue[currentIndex],
            TextToSpeech.QUEUE_FLUSH,
            null,
            "paperlens-$currentIndex",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @Singleton
    abstract fun bindTtsController(impl: AndroidTtsController): TtsController
}
