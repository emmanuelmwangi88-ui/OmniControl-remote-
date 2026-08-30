package com.example.omnicontrol.data.repository

import com.example.omnicontrol.data.local.DeviceDao
import com.example.omnicontrol.data.model.Device
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao
) {
    val allDevices: Flow<List<Device>> = deviceDao.getAllDevices()

    suspend fun saveDevice(device: Device) {
        deviceDao.insertDevice(device)
    }

    suspend fun removeDevice(device: Device) {
        deviceDao.deleteDevice(device)
    }

    suspend fun updateDeviceStatus(id: String, isOnline: Boolean) {
        val device = deviceDao.getDeviceById(id)
        device?.let {
            deviceDao.updateDevice(it.copy(isOnline = isOnline))
        }
    }

    suspend fun updateLastConnected(id: String) {
        val device = deviceDao.getDeviceById(id)
        device?.let {
            deviceDao.updateDevice(it.copy(lastConnected = System.currentTimeMillis()))
        }
    }

    suspend fun updateToken(id: String, token: String) {
        val device = deviceDao.getDeviceById(id)
        device?.let {
            deviceDao.updateDevice(it.copy(authToken = token))
        }
    }

    suspend fun updateIpAddress(id: String, ip: String) {
        val device = deviceDao.getDeviceById(id)
        device?.let {
            deviceDao.updateDevice(it.copy(ipAddress = ip))
        }
    }
}
