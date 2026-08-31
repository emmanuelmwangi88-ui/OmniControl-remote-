package com.example.omnicontrol.data.remote

import android.content.Context
import android.util.Log
import com.example.omnicontrol.data.remote.atv.AndroidTvPairingClient
import com.example.omnicontrol.data.remote.atv.AndroidTvRemoteConnection
import com.example.omnicontrol.data.remote.atv.CertificateManager
import com.example.omnicontrol.data.remote.atv.ClientIdentity
import com.example.omnicontrol.domain.ConnectionResult
import com.example.omnicontrol.domain.ConnectionState
import com.example.omnicontrol.domain.DpadKey
import com.example.omnicontrol.domain.RemoteController
import com.example.omnicontrol.proto.remote.RemoteKeyCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Talks the real Android TV Remote v2 protocol (see the `atv` package) instead of relying on
 * the Google Cast SDK, which only manages casting sessions and was never actually connected to
 * this device's IP. The client certificate is the same across every TV (see
 * [CertificateManager]); what's per-device is whether that cert has been through the pairing
 * handshake with *this* TV yet, tracked via [Device.authToken] the same way Samsung/LG already
 * store their own per-device tokens — here it's just a "paired" marker rather than a secret.
 */
class AndroidTvController(
    private val context: Context,
    private val ipAddress: String,
    initialAuthToken: String? = null
) : RemoteController {

    private val tag = "AndroidTvController"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var authToken: String? = initialAuthToken
    private var tokenListener: ((String) -> Unit)? = null

    private var pairingClient: AndroidTvPairingClient? = null
    private var remoteConnection: AndroidTvRemoteConnection? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override fun setTokenListener(listener: (String) -> Unit) {
        this.tokenListener = listener
    }

    override suspend fun connect(): ConnectionResult = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.CONNECTING
        try {
            val identity = CertificateManager.getOrCreateClientIdentity()

            if (authToken == PAIRED_MARKER) {
                // Already paired with this specific TV in a previous session — the client cert
                // is the same, so we should be able to go straight to the command channel.
                if (openRemoteConnection(identity)) {
                    _connectionState.value = ConnectionState.CONNECTED
                    return@withContext ConnectionResult.Success
                }
                Log.w(tag, "Stored pairing no longer accepted by $ipAddress, re-pairing")
            }

            beginPairing(identity)
        } catch (e: Exception) {
            Log.e(tag, "Connect failed for $ipAddress", e)
            _connectionState.value = ConnectionState.ERROR
            ConnectionResult.Failure(e.message ?: "Connection failed")
        }
    }

    override suspend fun pair(pin: String) = withContext(Dispatchers.IO) {
        val client = pairingClient
            ?: throw IllegalStateException("Pairing wasn't started — call connect() first")
        try {
            client.finishPairing(pin)
            client.close()
            pairingClient = null
            authToken = PAIRED_MARKER
            tokenListener?.invoke(PAIRED_MARKER)

            val identity = CertificateManager.getOrCreateClientIdentity()
            if (!openRemoteConnection(identity)) {
                throw IllegalStateException("Paired, but the TV closed the command connection")
            }
            _connectionState.value = ConnectionState.CONNECTED
        } catch (e: Exception) {
            Log.e(tag, "Pairing failed for $ipAddress", e)
            _connectionState.value = ConnectionState.ERROR
            throw e
        }
        Unit
    }

    override suspend fun powerToggle() = send(RemoteKeyCode.KEYCODE_POWER)
    override suspend fun volumeUp() = send(RemoteKeyCode.KEYCODE_VOLUME_UP)
    override suspend fun volumeDown() = send(RemoteKeyCode.KEYCODE_VOLUME_DOWN)
    override suspend fun mute() = send(RemoteKeyCode.KEYCODE_VOLUME_MUTE)
    override suspend fun playPause() = send(RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE)
    override suspend fun home() = send(RemoteKeyCode.KEYCODE_HOME)
    override suspend fun back() = send(RemoteKeyCode.KEYCODE_BACK)
    override suspend fun tv() = send(RemoteKeyCode.KEYCODE_TV)
    override suspend fun channelUp() = send(RemoteKeyCode.KEYCODE_CHANNEL_UP)
    override suspend fun channelDown() = send(RemoteKeyCode.KEYCODE_CHANNEL_DOWN)
    override suspend fun rewind() = send(RemoteKeyCode.KEYCODE_MEDIA_REWIND)
    override suspend fun fastForward() = send(RemoteKeyCode.KEYCODE_MEDIA_FAST_FORWARD)

    override suspend fun dpad(direction: DpadKey) = send(
        when (direction) {
            DpadKey.UP -> RemoteKeyCode.KEYCODE_DPAD_UP
            DpadKey.DOWN -> RemoteKeyCode.KEYCODE_DPAD_DOWN
            DpadKey.LEFT -> RemoteKeyCode.KEYCODE_DPAD_LEFT
            DpadKey.RIGHT -> RemoteKeyCode.KEYCODE_DPAD_RIGHT
            DpadKey.SELECT -> RemoteKeyCode.KEYCODE_DPAD_CENTER
        }
    )

    override suspend fun launchApp(appId: String): Unit = withContext(Dispatchers.IO) {
        // The real protocol launches apps via a deep-link URI (e.g. "https://www.youtube.com/"),
        // not an arbitrary app id — Roku-style ids like "youtube" won't resolve to anything on
        // Android TV. We pass it through best-effort for callers that already have a real link.
        try {
            remoteConnection?.sendAppLink(appId)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch app link '$appId' on $ipAddress", e)
        }
        Unit
    }

    override fun disconnect() {
        pairingClient?.close()
        pairingClient = null
        remoteConnection?.close()
        remoteConnection = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private suspend fun beginPairing(identity: ClientIdentity): ConnectionResult {
        val client = AndroidTvPairingClient(ipAddress, identity)
        client.startPairing()
        pairingClient = client
        _connectionState.value = ConnectionState.PAIRING_REQUIRED
        return ConnectionResult.PairingRequired
    }

    /** Returns false (rather than throwing) if the TV rejects our cert, so callers can fall back to pairing. */
    private suspend fun openRemoteConnection(identity: ClientIdentity): Boolean {
        return try {
            val connection = AndroidTvRemoteConnection(ipAddress, identity)
            connection.onDisconnected = {
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
            connection.connect(scope)
            remoteConnection = connection
            true
        } catch (e: Exception) {
            Log.w(tag, "Direct connection to $ipAddress failed: ${e.message}")
            false
        }
    }

    private suspend fun send(keyCode: RemoteKeyCode) = withContext(Dispatchers.IO) {
        try {
            remoteConnection?.sendKey(keyCode)
        } catch (e: Exception) {
            Log.e(tag, "Failed to send $keyCode to $ipAddress", e)
            _connectionState.value = ConnectionState.ERROR
        }
        Unit
    }

    companion object {
        const val PAIRED_MARKER = "atv_v2_paired"
    }
}
