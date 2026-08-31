package com.example.omnicontrol.data.remote

import android.util.Log
import android.util.Xml
import com.example.omnicontrol.data.model.AppShortcut
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
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import javax.inject.Inject

class RokuController(
    private val ipAddress: String,
    private val client: OkHttpClient
) : RemoteController {

    private val TAG = "RokuController"
    private val baseUrl = "http://$ipAddress:8060"

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect(): ConnectionResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Connecting to Roku at $ipAddress")
        _connectionState.value = ConnectionState.CONNECTING
        
        val request = Request.Builder().url("$baseUrl/query/device-info").get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Roku connection verified")
                    _connectionState.value = ConnectionState.CONNECTED
                    ConnectionResult.Success
                } else {
                    Log.w(TAG, "Roku returned ${response.code}")
                    _connectionState.value = ConnectionState.ERROR
                    ConnectionResult.Failure("TV returned error ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Roku", e)
            _connectionState.value = ConnectionState.ERROR
            ConnectionResult.Failure("TV unreachable: ${e.message}")
        }
    }

    suspend fun queryApps(): List<AppShortcut> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/query/apps").get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) emptyList()
                else parseAppsXml(response.body?.string() ?: "")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseAppsXml(xml: String): List<AppShortcut> {
        val apps = mutableListOf<AppShortcut>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var eventType = parser.eventType
        var currentAppId = ""
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.name == "app") {
                    currentAppId = parser.getAttributeValue(null, "id") ?: ""
                }
            } else if (eventType == XmlPullParser.TEXT) {
                val appName = parser.text
                if (currentAppId.isNotEmpty() && !appName.isNullOrBlank()) {
                    apps.add(AppShortcut(name = appName, appId = currentAppId, colorHex = "#333333"))
                }
            }
            eventType = parser.next()
        }
        return apps
    }

    private suspend fun sendRequest(endpoint: String) = withContext(Dispatchers.IO) {
        val url = "$baseUrl$endpoint"
        Log.d(TAG, "Sending POST request to: $url")
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    _connectionState.value = ConnectionState.CONNECTED
                } else {
                    Log.w(TAG, "Request successful but returned code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: $url", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    override suspend fun powerToggle() { sendRequest("/keypress/Power") }
    override suspend fun volumeUp() { sendRequest("/keypress/VolumeUp") }
    override suspend fun volumeDown() { sendRequest("/keypress/VolumeDown") }
    override suspend fun mute() { sendRequest("/keypress/VolumeMute") }
    
    override suspend fun dpad(direction: DpadKey) {
        val key = when (direction) {
            DpadKey.UP -> "Up"
            DpadKey.DOWN -> "Down"
            DpadKey.LEFT -> "Left"
            DpadKey.RIGHT -> "Right"
            DpadKey.SELECT -> "Select"
        }
        sendRequest("/keypress/$key")
    }

    override suspend fun home() { sendRequest("/keypress/Home") }
    override suspend fun back() { sendRequest("/keypress/Back") }
    override suspend fun launchApp(appId: String) { sendRequest("/launch/$appId") }
    
    override suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/query/device-info").get().build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }
    
    override fun disconnect() {}
}
