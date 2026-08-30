package com.example.omnicontrol.data.remote

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.omnicontrol.domain.ConnectionResult
import com.example.omnicontrol.domain.ConnectionState
import com.example.omnicontrol.domain.DpadKey
import com.example.omnicontrol.domain.RemoteController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.*

class BluetoothController(
    private val address: String,
    private val context: Context
) : RemoteController {

    private val TAG = "BluetoothController"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard Serial Port Service
    private var socket: BluetoothSocket? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect(): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.CONNECTING
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            socket = device.createRfcommSocketToServiceRecord(MY_UUID)
            socket?.connect()
            Log.d(TAG, "Connected to Bluetooth device: $address")
            _connectionState.value = ConnectionState.CONNECTED
            ConnectionResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Bluetooth device", e)
            _connectionState.value = ConnectionState.ERROR
            ConnectionResult.Failure(e.message ?: "Bluetooth connection failed")
        }
    }

    override suspend fun powerToggle() {
        sendByte(0x01) // Placeholder byte for power
    }

    override suspend fun volumeUp() {
        sendByte(0x02)
    }

    override suspend fun volumeDown() {
        sendByte(0x03)
    }

    override suspend fun mute() {
        sendByte(0x04)
    }

    override suspend fun dpad(direction: DpadKey) {
        val b = when (direction) {
            DpadKey.UP -> 0x05
            DpadKey.DOWN -> 0x06
            DpadKey.LEFT -> 0x07
            DpadKey.RIGHT -> 0x08
            DpadKey.SELECT -> 0x09
        }
        sendByte(b.toByte())
    }

    override suspend fun home() {
        sendByte(0x0A)
    }

    override suspend fun back() {
        sendByte(0x0B)
    }

    override suspend fun launchApp(appId: String) {
        // Bluetooth app launching is non-standard
    }

    private fun sendByte(b: Byte) {
        try {
            socket?.outputStream?.write(b.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending bluetooth byte", e)
        }
    }

    override fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
