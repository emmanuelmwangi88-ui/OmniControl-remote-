package com.example.omnicontrol.domain

import kotlinx.coroutines.flow.StateFlow

interface RemoteController {
    suspend fun connect(): ConnectionResult
    suspend fun powerToggle()
    suspend fun volumeUp()
    suspend fun volumeDown()
    suspend fun mute()
    suspend fun playPause() {}
    suspend fun dpad(direction: DpadKey)
    suspend fun home()
    suspend fun back()
    suspend fun tv() {}
    suspend fun channelUp() {}
    suspend fun channelDown() {}
    suspend fun rewind() {}
    suspend fun fastForward() {}
    suspend fun launchApp(appId: String)
    suspend fun sendText(text: String) {}
    suspend fun pair(pin: String) {}
    fun disconnect()
    val connectionState: StateFlow<ConnectionState>
    fun setTokenListener(listener: (String) -> Unit) {}
}

enum class DpadKey {
    UP, DOWN, LEFT, RIGHT, SELECT
}
