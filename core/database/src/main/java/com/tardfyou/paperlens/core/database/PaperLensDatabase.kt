package com.tardfyou.paperlens.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class,
        TextBlockEntity::class,
        HighlightEntity::class,
        ReadingProgressEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(PaperLensConverters::class)
abstract class PaperLensDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun textBlockDao(): TextBlockDao
    abstract fun highlightDao(): HighlightDao
    abstract fun readingProgressDao(): ReadingProgressDao
}
