package com.example.omnicontrol.data.repository

import com.example.omnicontrol.data.local.ShortcutDao
import com.example.omnicontrol.data.model.AppShortcut
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutRepository @Inject constructor(
    private val shortcutDao: ShortcutDao
) {
    val allShortcuts: Flow<List<AppShortcut>> = shortcutDao.getAllShortcuts()

    suspend fun addShortcut(shortcut: AppShortcut) {
        shortcutDao.insertShortcut(shortcut)
    }

    suspend fun deleteShortcut(shortcut: AppShortcut) {
        shortcutDao.deleteShortcut(shortcut)
    }

    suspend fun updateShortcut(shortcut: AppShortcut) {
        shortcutDao.updateShortcut(shortcut)
    }
}
