package com.tardfyou.paperlens.core.model

data class TtsPlaybackState(
    val isSpeaking: Boolean = false,
    val currentBlockIndex: Int = -1,
    val speechRate: Float = 1.0f,
)
