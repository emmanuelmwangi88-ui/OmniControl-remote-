package com.example.omnicontrol.data.remote

import android.util.Base64
import android.util.Log
import com.example.omnicontrol.domain.ConnectionResult
import com.example.omnicontrol.domain.ConnectionState
import com.example.omnicontrol.domain.DpadKey
import com.example.omnicontrol.domain.RemoteController
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

class SamsungController(
    private val ipAddress: String,
    private val client: OkHttpClient,
    private val appName: String = "OmniControl",
    private var token: String? = null
) : RemoteController {

    private var webSocket: WebSocket? = null
    private val encodedName = Base64.encodeToString(appName.toByteArray(), Base64.NO_WRAP)
    private var tokenListener: ((String) -> Unit)? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override fun setTokenListener(listener: (String) -> Unit) {
        this.tokenListener = listener
    }

    override suspend fun connect(): ConnectionResult {
        return connectWithPort(8002)
    }

    private fun connectWithPort(port: Int): ConnectionResult {
        _connectionState.value = ConnectionState.CONNECTING
        val url = "wss://$ipAddress:$port/api/v2/channels/samsung.remote.control?name=$encodedName" +
                (if (token != null) "&token=$token" else "")
        
        Log.d("SamsungController", "Connecting to $url")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SamsungController", "WebSocket opened on port $port")
                if (token != null) {
                    _connectionState.value = ConnectionState.CONNECTED
                } else {
                    _connectionState.value = ConnectionState.PAIRING_REQUIRED
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("SamsungController", "Message: $text")
                try {
                    val json = JSONObject(text)
                    val event = json.optString("event")
                    if (event == "ms.channel.connect") {
                        val data = json.optJSONObject("data")
                        if (data != null && data.has("token")) {
                            val newToken = data.getString("token")
                            token = newToken
                            tokenListener?.invoke(newToken)
                            _connectionState.value = ConnectionState.CONNECTED
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SamsungController", "Parse error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SamsungController", "WebSocket failure on port $port: ${t.message}")
                if (port == 8002) {
                    // Try port 8001 as fallback
                    connectWithPort(8001)
                } else {
                    _connectionState.value = ConnectionState.ERROR
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
        return ConnectionResult.Success
    }

    private fun sendKey(key: String) {
        val json = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", key)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }
        webSocket?.send(json.toString())
    }

    override suspend fun powerToggle() = sendKey("KEY_POWER")
    override suspend fun volumeUp() = sendKey("KEY_VOLUP")
    override suspend fun volumeDown() = sendKey("KEY_VOLDOWN")
    override suspend fun mute() = sendKey("KEY_MUTE")
    
    override suspend fun dpad(direction: DpadKey) {
        val key = when (direction) {
            DpadKey.UP -> "KEY_UP"
            DpadKey.DOWN -> "KEY_DOWN"
            DpadKey.LEFT -> "KEY_LEFT"
            DpadKey.RIGHT -> "KEY_RIGHT"
            DpadKey.SELECT -> "KEY_ENTER"
        }
        sendKey(key)
    }

    override suspend fun home() = sendKey("KEY_HOME")
    override suspend fun back() = sendKey("KEY_RETURN")
    
    override suspend fun launchApp(appId: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$ipAddress:8001/api/v2/applications/$appId"
                val request = Request.Builder().url(url).post("".toRequestBody()).build()
                client.newCall(request).execute().use { response ->
                    Log.d("SamsungController", "Launch app $appId result: ${response.isSuccessful}")
                }
            } catch (e: Exception) {
                Log.e("SamsungController", "Failed to launch app $appId", e)
            }
        }
    }
    
    override fun disconnect() {
        webSocket?.close(1000, "User disconnect")
    }
}
