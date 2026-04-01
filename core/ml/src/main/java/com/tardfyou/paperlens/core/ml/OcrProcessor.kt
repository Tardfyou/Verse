package com.tardfyou.paperlens.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tardfyou.paperlens.core.common.DefaultDispatcher
import com.tardfyou.paperlens.core.model.BoundingBox
import com.tardfyou.paperlens.core.model.OcrBlock
import com.tardfyou.paperlens.core.model.OcrResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine

sealed class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class EmptyResult : OcrException("No readable text was detected.")

    class ProcessingFailure(cause: Throwable) :
        OcrException("Failed to process OCR request.", cause)
}

interface OcrProcessor {
    suspend fun recognizeImage(uri: Uri): OcrResult

    suspend fun recognizeBitmap(bitmap: Bitmap): OcrResult
}

@Singleton
class MlKitOcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : OcrProcessor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeImage(uri: Uri): OcrResult = withContext(defaultDispatcher) {
        val image = InputImage.fromFilePath(context, uri)
        runRecognition(image)
    }

    override suspend fun recognizeBitmap(bitmap: Bitmap): OcrResult = withContext(defaultDispatcher) {
        val image = InputImage.fromBitmap(bitmap, 0)
        runRecognition(image)
    }

    private suspend fun runRecognition(inputImage: InputImage): OcrResult =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    val blocks = result.textBlocks.mapNotNull { block ->
                        val box = block.boundingBox ?: return@mapNotNull null
                        OcrBlock(
                            content = block.text,
                            boundingBox = BoundingBox(
                                left = box.left.toFloat(),
                                top = box.top.toFloat(),
                                right = box.right.toFloat(),
                                bottom = box.bottom.toFloat(),
                            ),
                            confidence = block.lines.mapNotNull { it.confidence }.average()
                                .takeIf { !it.isNaN() }
                                ?.toFloat()
                                ?: 0.85f,
                        )
                    }.sortedBy { it.boundingBox.top }
                    if (blocks.isEmpty()) {
                        continuation.resumeWithException(OcrException.EmptyResult())
                    } else {
                        continuation.resume(
                            OcrResult(
                                fullText = result.text,
                                blocks = blocks,
                            ),
                        )
                    }
                }
                .addOnFailureListener { throwable ->
                    continuation.resumeWithException(OcrException.ProcessingFailure(throwable))
                }
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds
    @Singleton
    abstract fun bindOcrProcessor(impl: MlKitOcrProcessor): OcrProcessor
}
