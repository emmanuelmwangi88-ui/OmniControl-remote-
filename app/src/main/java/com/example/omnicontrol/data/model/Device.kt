package com.example.omnicontrol.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey val id: String,
    val name: String,
    val ipAddress: String,
    val type: DeviceType,
    val authToken: String? = null,
    val lastConnected: Long = 0,
    val isOnline: Boolean = false
)

enum class DeviceType {
    ROKU, LG, SAMSUNG, ANDROID_TV, IR, BLUETOOTH
}
