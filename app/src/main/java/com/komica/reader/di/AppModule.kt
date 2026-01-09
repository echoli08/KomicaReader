package com.komica.reader.di

import android.content.Context
import com.komica.reader.db.AppDatabase
import com.komica.reader.repository.HistoryRepository
import com.komica.reader.repository.KomicaRepository
import com.komica.reader.util.FavoritesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideKomicaRepository(@ApplicationContext context: Context): KomicaRepository {
        return KomicaRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideHistoryRepository(@ApplicationContext context: Context): HistoryRepository {
        return HistoryRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideFavoritesManager(@ApplicationContext context: Context): FavoritesManager {
        return FavoritesManager.getInstance(context)
    }
}
