package com.example.omnicontrol.data.local

import androidx.room.*
import com.example.omnicontrol.data.model.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices")
    fun getAllDevices(): Flow<List<Device>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)

    @Update
    suspend fun updateDevice(device: Device)

    @Delete
    suspend fun deleteDevice(device: Device)

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getDeviceById(id: String): Device?
}
