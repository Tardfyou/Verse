package com.tardfyou.paperlens.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providePaperLensDatabase(
        @ApplicationContext context: Context,
    ): PaperLensDatabase = Room.databaseBuilder(
        context,
        PaperLensDatabase::class.java,
        "paperlens.db",
    ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideDocumentDao(database: PaperLensDatabase): DocumentDao = database.documentDao()

    @Provides
    fun providePageDao(database: PaperLensDatabase): PageDao = database.pageDao()

    @Provides
    fun provideTextBlockDao(database: PaperLensDatabase): TextBlockDao = database.textBlockDao()

    @Provides
    fun provideHighlightDao(database: PaperLensDatabase): HighlightDao = database.highlightDao()

    @Provides
    fun provideReadingProgressDao(database: PaperLensDatabase): ReadingProgressDao =
        database.readingProgressDao()
}
