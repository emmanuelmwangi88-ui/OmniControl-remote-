package com.example.omnicontrol.data.local

import androidx.room.*
import com.example.omnicontrol.data.model.AppShortcut
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM app_shortcuts")
    fun getAllShortcuts(): Flow<List<AppShortcut>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: AppShortcut)

    @Update
    suspend fun updateShortcut(shortcut: AppShortcut)

    @Delete
    suspend fun deleteShortcut(shortcut: AppShortcut)
}
