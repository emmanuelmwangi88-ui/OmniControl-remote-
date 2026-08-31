package com.example.omnicontrol.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.omnicontrol.data.model.Device
import com.example.omnicontrol.data.model.AppShortcut

@Database(entities = [Device::class, AppShortcut::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun shortcutDao(): ShortcutDao
}
