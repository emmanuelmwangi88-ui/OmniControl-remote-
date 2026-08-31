package com.example.omnicontrol.data.remote

import android.content.Context
import android.hardware.ConsumerIrManager
import com.example.omnicontrol.domain.ConnectionResult
import com.example.omnicontrol.domain.ConnectionState
import com.example.omnicontrol.domain.DpadKey
import com.example.omnicontrol.domain.RemoteController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class IrController(private val context: Context) : RemoteController {
    
    private val irManager: ConsumerIrManager? by lazy {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    }

    private var currentBrand: String = "Samsung"
    private var irData: JSONObject? = null
    
    fun setBrand(brand: String) {
        currentBrand = brand
    }
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    init {
        try {
            val jsonString = context.assets.open("ir_codes.json").bufferedReader().use { it.readText() }
            irData = JSONObject(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun connect(): ConnectionResult {
        return if (irManager?.hasIrEmitter() == true) {
            _connectionState.value = ConnectionState.CONNECTED
            ConnectionResult.Success
        } else {
            _connectionState.value = ConnectionState.ERROR
            ConnectionResult.Failure("IR Blaster not available or not supported on this device")
        }
    }

    private fun transmit(button: String) {
        val brandData = irData?.optJSONObject(currentBrand) ?: return
        val frequency = brandData.optInt("frequency", 38000)
        val patternJson = brandData.optJSONArray(button) ?: return
        
        val pattern = IntArray(patternJson.length())
        for (i in 0 until patternJson.length()) {
            pattern[i] = patternJson.getInt(i)
        }
        
        irManager?.transmit(frequency, pattern)
    }

    override suspend fun powerToggle() = transmit("power")
    override suspend fun volumeUp() = transmit("volumeUp")
    override suspend fun volumeDown() = transmit("volumeDown")
    override suspend fun mute() = transmit("mute")
    
    override suspend fun dpad(direction: DpadKey) {
        val key = when (direction) {
            DpadKey.UP -> "up"
            DpadKey.DOWN -> "down"
            DpadKey.LEFT -> "left"
            DpadKey.RIGHT -> "right"
            DpadKey.SELECT -> "ok"
        }
        transmit(key)
    }

    override suspend fun home() = transmit("home")
    override suspend fun back() = transmit("back")
    override suspend fun launchApp(appId: String) {}
    override fun disconnect() {}
}
