package com.example.omnicontrol.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_shortcuts")
data class AppShortcut(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val appId: String,
    val colorHex: String,
    val iconType: String = "TEXT", // e.g., TEXT, ICON, RES
    val iconUrl: String? = null,
    val iconRes: Int? = null
)
