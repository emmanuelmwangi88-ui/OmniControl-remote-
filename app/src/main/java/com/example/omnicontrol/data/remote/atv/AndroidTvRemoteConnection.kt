package com.example.omnicontrol.data.remote.atv

import android.util.Log
import com.example.omnicontrol.proto.remote.RemoteAppLinkLaunchRequest
import com.example.omnicontrol.proto.remote.RemoteConfigure
import com.example.omnicontrol.proto.remote.RemoteDeviceInfo
import com.example.omnicontrol.proto.remote.RemoteDirection
import com.example.omnicontrol.proto.remote.RemoteKeyCode
import com.example.omnicontrol.proto.remote.RemoteKeyInject
import com.example.omnicontrol.proto.remote.RemoteMessage
import com.example.omnicontrol.proto.remote.RemotePingResponse
import com.example.omnicontrol.proto.remote.RemoteSetActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.net.ssl.SSLSocket

class AndroidTvRemoteException(message: String) : Exception(message)

// Feature bits the TV reports/accepts in RemoteConfigure.code1 — subset of what real Android TV
// Remote Service clients (e.g. the official Google TV app) advertise.
private val FEATURE_PING = 1 shl 0
private val FEATURE_KEY = 1 shl 1
private val FEATURE_POWER = 1 shl 5
private val FEATURE_VOLUME = 1 shl 6
private val FEATURE_APP_LINK = 1 shl 9
private val REQUESTED_FEATURES = FEATURE_PING or FEATURE_KEY or FEATURE_POWER or FEATURE_VOLUME or FEATURE_APP_LINK

/**
 * The persistent command connection to an already-paired Android TV, port 6466
 * (see remotemessage.proto). Handles the RemoteConfigure/RemoteSetActive handshake, answers
 * the TV's keepalive pings, and exposes key-press / app-link injection.
 */
class AndroidTvRemoteConnection(
    private val ipAddress: String,
    private val identity: ClientIdentity,
    private val port: Int = 6466
) {
    private val tag = "AndroidTvRemote"

    private var socket: SSLSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var readerJob: Job? = null
    private val writeLock = Mutex()
    private var activeFeatures = REQUESTED_FEATURES

    var onDisconnected: ((Throwable?) -> Unit)? = null
    var onPowerStateChanged: ((Boolean) -> Unit)? = null

    /** Suspends until the TV sends remote_start (i.e. the connection is ready for commands). */
    suspend fun connect(scope: CoroutineScope) = withContext(Dispatchers.IO) {
        val sslSocket = TvSslContextFactory.create(identity).createSocket(ipAddress, port) as SSLSocket
        sslSocket.soTimeout = 20_000
        sslSocket.startHandshake()
        socket = sslSocket
        val inputStream = sslSocket.inputStream
        val outputStream = sslSocket.outputStream
        input = inputStream
        output = outputStream

        var ready = false
        while (!ready) {
            val msg = RemoteMessage.parseDelimitedFrom(inputStream)
                ?: throw AndroidTvRemoteException("TV closed the connection while starting up")
            ready = handleIncoming(msg)
        }

        // Keepalive pings and any further state changes arrive as long as the screen is open;
        // hand that off to a background reader tied to the caller's lifecycle.
        readerJob = scope.launch(Dispatchers.IO) { readLoop(inputStream) }
    }

    suspend fun sendKey(keyCode: RemoteKeyCode, direction: RemoteDirection = RemoteDirection.SHORT) {
        sendMessage(
            RemoteMessage.newBuilder()
                .setRemoteKeyInject(
                    RemoteKeyInject.newBuilder()
                        .setKeyCode(keyCode)
                        .setDirection(direction)
                )
        )
    }

    suspend fun sendAppLink(uri: String) {
        sendMessage(
            RemoteMessage.newBuilder()
                .setRemoteAppLinkLaunchRequest(RemoteAppLinkLaunchRequest.newBuilder().setAppLink(uri))
        )
    }

    fun close() {
        readerJob?.cancel()
        readerJob = null
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    private suspend fun readLoop(inputStream: InputStream) {
        try {
            while (true) {
                val msg = RemoteMessage.parseDelimitedFrom(inputStream) ?: break
                handleIncoming(msg)
            }
            onDisconnected?.invoke(null)
        } catch (e: Exception) {
            Log.d(tag, "Remote connection closed: ${e.message}")
            onDisconnected?.invoke(e)
        }
    }

    /** Returns true once remote_start has been seen (connection fully ready). */
    private suspend fun handleIncoming(msg: RemoteMessage): Boolean {
        when {
            msg.hasRemoteConfigure() -> {
                activeFeatures = msg.remoteConfigure.code1 and REQUESTED_FEATURES
                sendMessage(
                    RemoteMessage.newBuilder().setRemoteConfigure(
                        RemoteConfigure.newBuilder()
                            .setCode1(activeFeatures)
                            .setDeviceInfo(
                                // unknown1/unknown2 are magic values expected by the TV side;
                                // their real meaning isn't publicly documented, but every
                                // working client reports them as 1 / "1".
                                RemoteDeviceInfo.newBuilder()
                                    .setUnknown1(1)
                                    .setUnknown2("1")
                                    .setPackageName("com.example.omnicontrol")
                                    .setAppVersion("1.0.0")
                            )
                    )
                )
            }
            msg.hasRemoteSetActive() -> sendMessage(
                RemoteMessage.newBuilder().setRemoteSetActive(RemoteSetActive.newBuilder().setActive(activeFeatures))
            )
            msg.hasRemotePingRequest() -> sendMessage(
                RemoteMessage.newBuilder().setRemotePingResponse(
                    RemotePingResponse.newBuilder().setVal1(msg.remotePingRequest.val1)
                )
            )
            msg.hasRemoteStart() -> {
                onPowerStateChanged?.invoke(msg.remoteStart.started)
                return true
            }
            else -> Unit
        }
        return false
    }

    private suspend fun sendMessage(message: RemoteMessage.Builder) {
        writeLock.withLock {
            val out = output ?: throw AndroidTvRemoteException("Not connected")
            withContext(Dispatchers.IO) {
                message.build().writeDelimitedTo(out)
                out.flush()
            }
        }
    }
}
