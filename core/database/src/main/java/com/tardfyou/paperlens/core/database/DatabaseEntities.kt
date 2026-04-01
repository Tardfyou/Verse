package com.tardfyou.paperlens.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.tardfyou.paperlens.core.model.DocumentStatus
import com.tardfyou.paperlens.core.model.DocumentType
import com.tardfyou.paperlens.core.model.OcrStatus

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val type: DocumentType,
    val sourceUri: String,
    val pageCount: Int,
    val status: DocumentStatus,
    val previewText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId"), Index(value = ["documentId", "pageIndex"], unique = true)],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val documentId: Long,
    val pageIndex: Int,
    val imagePath: String? = null,
    val rawText: String = "",
    val ocrStatus: OcrStatus = OcrStatus.Pending,
    val width: Int = 0,
    val height: Int = 0,
)

@Entity(
    tableName = "text_blocks",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pageId")],
)
data class TextBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val pageId: Long,
    val blockIndex: Int,
    val content: String,
    val left: Float? = null,
    val top: Float? = null,
    val right: Float? = null,
    val bottom: Float? = null,
    val confidence: Float = 0f,
)

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId"), Index("pageId")],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val documentId: Long,
    val pageId: Long,
    val selectedText: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class HighlightRow(
    val id: Long,
    val documentId: Long,
    val pageId: Long,
    val selectedText: String,
    val note: String,
    val createdAt: Long,
    val documentTitle: String,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class ReadingProgressEntity(
    @PrimaryKey val documentId: Long,
    val currentPage: Int = 0,
    val currentBlock: Int = -1,
    val charOffset: Int = 0,
    val lastOpenedAt: Long = System.currentTimeMillis(),
)

class PaperLensConverters {
    @TypeConverter
    fun documentTypeToString(value: DocumentType): String = value.name

    @TypeConverter
    fun stringToDocumentType(value: String): DocumentType = DocumentType.valueOf(value)

    @TypeConverter
    fun documentStatusToString(value: DocumentStatus): String = value.name

    @TypeConverter
    fun stringToDocumentStatus(value: String): DocumentStatus = DocumentStatus.valueOf(value)

    @TypeConverter
    fun ocrStatusToString(value: OcrStatus): String = value.name

    @TypeConverter
    fun stringToOcrStatus(value: String): OcrStatus = OcrStatus.valueOf(value)
}
