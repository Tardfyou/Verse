package com.tardfyou.paperlens.core.database

import com.tardfyou.paperlens.core.model.DocumentRepository
import com.tardfyou.paperlens.core.model.HighlightRepository
import com.tardfyou.paperlens.core.model.ReadingProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindingsModule {
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        impl: RoomDocumentRepository,
    ): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindHighlightRepository(
        impl: RoomHighlightRepository,
    ): HighlightRepository

    @Binds
    @Singleton
    abstract fun bindReadingProgressRepository(
        impl: RoomReadingProgressRepository,
    ): ReadingProgressRepository
}
