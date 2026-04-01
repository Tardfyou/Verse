package com.tardfyou.paperlens.core.model

data class OcrBlock(
    val content: String,
    val boundingBox: BoundingBox,
    val confidence: Float = 0f,
)

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock>,
)
