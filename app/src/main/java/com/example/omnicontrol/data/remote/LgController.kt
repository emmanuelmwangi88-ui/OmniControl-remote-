package com.example.omnicontrol.data.remote

import android.util.Log
import com.example.omnicontrol.domain.ConnectionResult
import com.example.omnicontrol.domain.ConnectionState
import com.example.omnicontrol.domain.DpadKey
import com.example.omnicontrol.domain.RemoteController
import okhttp3.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class LgController(
    private val ipAddress: String,
    private val client: OkHttpClient,
    private var clientKey: String? = null
) : RemoteController {

    private var webSocket: WebSocket? = null
    private var inputWebSocket: WebSocket? = null
    private var tokenListener: ((String) -> Unit)? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override fun setTokenListener(listener: (String) -> Unit) {
        this.tokenListener = listener
    }

    override suspend fun connect(): ConnectionResult {
        return connectWithPort(3001)
    }

    private fun connectWithPort(port: Int): ConnectionResult {
        _connectionState.value = ConnectionState.CONNECTING
        val protocol = if (port == 3001) "wss" else "ws"
        val url = "$protocol://$ipAddress:$port"
        Log.d("LgController", "Connecting to $url")
        val request = Request.Builder().url(url).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("LgController", "WebSocket opened on port $port")
                register(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("LgController", "Message: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    val payload = json.optJSONObject("payload")

                    if (type == "registered") {
                        val key = payload?.optString("client-key")
                        if (key != null) {
                            clientKey = key
                            tokenListener?.invoke(key)
                            _connectionState.value = ConnectionState.CONNECTED
                            requestInputSocket()
                        }
                    } else if (json.optString("id") == "req_input_socket") {
                        val socketUrl = payload?.optString("socketPath")
                        if (socketUrl != null) {
                            connectToInputSocket(socketUrl)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LgController", "Parse error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("LgController", "WebSocket failure on port $port: ${t.message}")
                if (port == 3001) {
                    connectWithPort(3000)
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

    private fun requestInputSocket() {
        val json = JSONObject().apply {
            put("type", "request")
            put("id", "req_input_socket")
            put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
        }
        webSocket?.send(json.toString())
    }

    private fun connectToInputSocket(url: String) {
        val request = Request.Builder().url(url).build()
        inputWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Input socket ready
            }
        })
    }

    private fun register(socket: WebSocket) {
        val json = JSONObject().apply {
            put("type", "register")
            put("id", "register_0")
            put("payload", JSONObject().apply {
                if (!clientKey.isNullOrEmpty()) {
                    put("client-key", clientKey)
                }
                put("manifest", JSONObject().apply {
                    put("permissions", listOf(
                        "LAUNCH", "CONTROL_AUDIO", "CONTROL_INPUT_TEXT", 
                        "CONTROL_POWER", "READ_INSTALLED_APPS"
                    ))
                })
            })
        }
        socket.send(json.toString())
    }

    private fun sendCommand(uri: String, payload: JSONObject? = null) {
        val json = JSONObject().apply {
            put("type", "request")
            put("id", "req_${System.currentTimeMillis()}")
            put("uri", uri)
            payload?.let { put("payload", it) }
        }
        webSocket?.send(json.toString())
    }

    private fun sendButton(button: String) {
        val json = JSONObject().apply {
            put("type", "button")
            put("name", button)
        }
        val success = inputWebSocket?.send(json.toString()) ?: false
        if (!success) {
            // Fallback to service URI if input socket fails
            val uri = when(button) {
                "UP" -> "ssap://system.navigation/up"
                "DOWN" -> "ssap://system.navigation/down"
                "LEFT" -> "ssap://system.navigation/left"
                "RIGHT" -> "ssap://system.navigation/right"
                "ENTER" -> "ssap://system.navigation/ok"
                "BACK" -> "ssap://system.navigation/back"
                "HOME" -> "ssap://system.launcher/open"
                else -> ""
            }
            if (uri.isNotEmpty()) sendCommand(uri)
        }
    }

    override suspend fun powerToggle() = sendCommand("ssap://system/turnOff")
    override suspend fun volumeUp() = sendCommand("ssap://audio/volumeUp")
    override suspend fun volumeDown() = sendCommand("ssap://audio/volumeDown")
    override suspend fun mute() = sendCommand("ssap://audio/setMute", JSONObject().apply { put("mute", true) })
    
    override suspend fun dpad(direction: DpadKey) {
        val btn = when (direction) {
            DpadKey.UP -> "UP"
            DpadKey.DOWN -> "DOWN"
            DpadKey.LEFT -> "LEFT"
            DpadKey.RIGHT -> "RIGHT"
            DpadKey.SELECT -> "ENTER"
        }
        sendButton(btn)
    }

    override suspend fun home() = sendButton("HOME")
    override suspend fun back() = sendButton("BACK")
    override suspend fun launchApp(appId: String) = sendCommand("ssap://system.launcher/launch", JSONObject().apply { put("id", appId) })
    
    override fun disconnect() {
        webSocket?.close(1000, "User disconnect")
        inputWebSocket?.close(1000, "User disconnect")
    }
}
