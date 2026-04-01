package com.tardfyou.paperlens.core.file

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedImage(
    val file: File,
    val displayName: String,
)

data class PdfPageBitmap(
    val pageIndex: Int,
    val bitmap: Bitmap,
)

@Singleton
class DocumentImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createCaptureFile(): File {
        val directory = File(context.filesDir, "captures").apply { mkdirs() }
        return File(directory, "paperlens_${System.currentTimeMillis()}.jpg")
    }

    fun importImage(uri: Uri): ImportedImage {
        val fileName = queryDisplayName(uri)?.substringBeforeLast('.') ?: "Imported Image"
        val target = File(context.filesDir, "imports").apply { mkdirs() }
            .resolve("${fileName}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            FileOutputStream(target).use { output ->
                requireNotNull(input) { "Unable to open image input stream." }
                input.copyTo(output)
            }
        }
        return ImportedImage(file = target, displayName = fileName)
    }

    fun persistPdfPermission(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            IntentFlags.ReadOnly.value,
        )
    }

    fun getDisplayName(uri: Uri): String =
        queryDisplayName(uri)
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: "Untitled"

    fun renderPdfPage(uri: String, pageIndex: Int, targetWidth: Int = 1440): PdfPageBitmap {
        val documentUri = uri.toUri()
        val descriptor = openPdfDescriptor(context.contentResolver, documentUri)
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val safePageIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safePageIndex).use { page ->
                    val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return PdfPageBitmap(pageIndex = safePageIndex, bitmap = bitmap)
                }
            }
        }
    }

    fun getPdfPageCount(uri: String): Int {
        val documentUri = uri.toUri()
        val descriptor = openPdfDescriptor(context.contentResolver, documentUri)
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                return renderer.pageCount
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (!cursor.moveToFirst() || nameIndex == -1) {
                null
            } else {
                cursor.getString(nameIndex)
            }
        }
}

private fun openPdfDescriptor(contentResolver: ContentResolver, uri: Uri): ParcelFileDescriptor =
    requireNotNull(contentResolver.openFileDescriptor(uri, "r")) {
        "Unable to open PDF descriptor for $uri"
    }

private enum class IntentFlags(val value: Int) {
    ReadOnly(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
}

@Module
@InstallIn(SingletonComponent::class)
object FileModule {
    @Provides
    @Singleton
    fun provideDocumentImportManager(
        @ApplicationContext context: Context,
    ): DocumentImportManager = DocumentImportManager(context)
}
