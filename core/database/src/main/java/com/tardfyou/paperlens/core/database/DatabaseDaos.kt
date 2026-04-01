package com.tardfyou.paperlens.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tardfyou.paperlens.core.model.DocumentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecentDocuments(limit: Int): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun observeDocument(documentId: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocument(documentId: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DocumentEntity): Long

    @Update
    suspend fun update(entity: DocumentEntity)

    @Query(
        """
        UPDATE documents
        SET status = :status, updatedAt = :updatedAt
        WHERE id = :documentId
        """,
    )
    suspend fun updateStatus(documentId: Long, status: DocumentStatus, updatedAt: Long)

    @Query(
        """
        UPDATE documents
        SET previewText = :previewText, updatedAt = :updatedAt
        WHERE id = :documentId
        """,
    )
    suspend fun updatePreview(documentId: Long, previewText: String, updatedAt: Long)

    @Query(
        """
        UPDATE documents
        SET updatedAt = :updatedAt
        WHERE id = :documentId
        """,
    )
    suspend fun touch(documentId: Long, updatedAt: Long)
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun observePages(documentId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPage(pageId: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE documentId = :documentId AND pageIndex = :pageIndex LIMIT 1")
    suspend fun getPageByIndex(documentId: Long, pageIndex: Int): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PageEntity): Long

    @Update
    suspend fun update(entity: PageEntity)
}

@Dao
interface TextBlockDao {
    @Query("SELECT * FROM text_blocks WHERE pageId = :pageId ORDER BY blockIndex ASC")
    fun observeBlocks(pageId: Long): Flow<List<TextBlockEntity>>

    @Query("DELETE FROM text_blocks WHERE pageId = :pageId")
    suspend fun deleteForPage(pageId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TextBlockEntity>)
}

@Dao
interface HighlightDao {
    @Query(
        """
        SELECT h.id, h.documentId, h.pageId, h.selectedText, h.note, h.createdAt, d.title AS documentTitle
        FROM highlights h
        INNER JOIN documents d ON d.id = h.documentId
        WHERE :query = ''
           OR d.title LIKE '%' || :query || '%'
           OR h.selectedText LIKE '%' || :query || '%'
           OR h.note LIKE '%' || :query || '%'
        ORDER BY h.createdAt DESC
        """,
    )
    fun observeHighlights(query: String): Flow<List<HighlightRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HighlightEntity): Long

    @Query("UPDATE highlights SET note = :note WHERE id = :highlightId")
    suspend fun updateNote(highlightId: Long, note: String)
}

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE documentId = :documentId")
    fun observeProgress(documentId: Long): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE documentId = :documentId")
    suspend fun getProgress(documentId: Long): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingProgressEntity)
}
