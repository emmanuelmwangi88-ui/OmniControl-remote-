package com.example.omnicontrol.di

import android.content.Context
import androidx.room.Room
import com.example.omnicontrol.data.local.AppDatabase
import com.example.omnicontrol.data.local.DeviceDao
import com.example.omnicontrol.data.local.ShortcutDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "omni_control_db"
        )
        .fallbackToDestructiveMigration() // For development ease with schema changes
        .build()
    }

    @Provides
    fun provideDeviceDao(database: AppDatabase): DeviceDao {
        return database.deviceDao()
    }

    @Provides
    fun provideShortcutDao(database: AppDatabase): ShortcutDao {
        return database.shortcutDao()
    }
}
