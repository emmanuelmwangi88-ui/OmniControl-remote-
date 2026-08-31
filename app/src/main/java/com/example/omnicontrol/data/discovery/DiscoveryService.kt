package com.example.omnicontrol.data.discovery

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.omnicontrol.data.model.Device
import com.example.omnicontrol.data.model.DeviceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "DiscoveryService"
    private val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
    private val SSDP_PORT = 1900
    private val ATVR_SERVICE_TYPE = "_androidtvremote2._tcp."

    private val SEARCH_TARGETS = listOf(
        "upnp:rootdevice",
        "roku:ecp",
        "urn:roku-com:device:player:1-0",
        "urn:samsung.com:device:RemoteControlReceiver:1",
        "urn:lge:com:service:webos-second-screen:1",
        "urn:dial-multiscreen-org:service:dial:1",
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "ssdp:all"
    )

    fun startScanning(): Flow<List<Device>> = callbackFlow {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        val lock = wifi?.createMulticastLock("OmniControlSSDP")
        
        val devices = mutableSetOf<Device>()
        
        val nsdListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
            }
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Service discovery started")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Service discovery stopped")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed: $errorCode")
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        try {
                            val ip = serviceInfo.host?.hostAddress ?: ""
                            if (ip.isNotEmpty()) {
                                val device = Device(
                                    id = "atv-${serviceInfo.serviceName}",
                                    name = serviceInfo.serviceName ?: "Android TV",
                                    ipAddress = ip,
                                    type = DeviceType.ANDROID_TV
                                )
                                if (devices.add(device)) {
                                    trySend(devices.toList())
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in service resolved callback", e)
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.e(TAG, "service lost: $serviceInfo")
            }
        }

        try {
            nsdManager?.discoverServices(ATVR_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, nsdListener)
        } catch (e: Exception) {
            Log.e(TAG, "mDNS discovery start error", e)
        }

        val ssdpJob = launch(Dispatchers.IO) {
            try {
                lock?.setReferenceCounted(true)
                lock?.acquire()
                
                // Bluetooth
                try {
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val adapter = bluetoothManager?.adapter
                    if (adapter?.isEnabled == true) {
                        adapter.bondedDevices?.forEach { device ->
                            if (isTvLike(device)) {
                                val deviceName = try { device.name } catch (e: SecurityException) { "Unknown Bluetooth Device" }
                                if (devices.add(Device(
                                    id = "bt-${device.address}",
                                    name = deviceName ?: "Unknown Bluetooth TV",
                                    ipAddress = device.address,
                                    type = DeviceType.BLUETOOTH
                                ))) {
                                    trySend(devices.toList())
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Bluetooth discovery permission missing", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Bluetooth discovery error", e)
                }

                // SSDP
                var socket: DatagramSocket? = null
                try {
                    socket = DatagramSocket()
                    socket.soTimeout = 3000
                    socket.broadcast = true

                    // If the phone also has mobile data on, Android may otherwise route this
                    // multicast traffic out the cellular interface instead of Wi-Fi, so the TV
                    // never sees the M-SEARCH request. Bind explicitly to the Wi-Fi network.
                    getWifiNetwork()?.let { network ->
                        try {
                            network.bindSocket(socket)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not bind SSDP socket to Wi-Fi network", e)
                        }
                    }

                    SEARCH_TARGETS.forEach { target ->
                        val query = "M-SEARCH * HTTP/1.1\r\n" +
                                "HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT\r\n" +
                                "MAN: \"ssdp:discover\"\r\n" +
                                "MX: 3\r\n" +
                                "ST: $target\r\n" +
                                "USER-AGENT: Android/OmniControl SSDP\r\n\r\n"

                        val group = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                        val packet = DatagramPacket(query.toByteArray(), query.length, group, SSDP_PORT)

                        try {
                            socket.send(packet)
                            val startTime = System.currentTimeMillis()
                            while (System.currentTimeMillis() - startTime < 3000) {
                                val recvBuf = ByteArray(8192)
                                val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                                try {
                                    socket.receive(recvPacket)
                                    val response = String(recvPacket.data, 0, recvPacket.length)
                                    val hostAddress = recvPacket.address?.hostAddress ?: ""
                                    parseSsdpResponse(response, hostAddress)?.let {
                                        if (devices.add(it)) trySend(devices.toList())
                                    }
                                } catch (e: SocketTimeoutException) { break }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending SSDP packet for $target", e)
                        }
                        delay(100)
                    }
                } finally {
                    socket?.close()
                }
            } finally {
                if (lock?.isHeld == true) lock.release()
            }
        }

        awaitClose {
            try {
                nsdManager?.stopServiceDiscovery(nsdListener)
            } catch (e: Exception) {}
            ssdpJob.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun getWifiNetwork(): Network? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        return connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun isTvLike(device: BluetoothDevice): Boolean {
        return try {
            val deviceClass = device.bluetoothClass?.majorDeviceClass
            val deviceName = device.name
            when (deviceClass) {
                android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> true
                else -> deviceName?.contains("TV", ignoreCase = true) == true || 
                        deviceName?.contains("Remote", ignoreCase = true) == true
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException checking Bluetooth device: ${device.address}", e)
            false
        }
    }

    private fun parseSsdpResponse(response: String, packetIp: String): Device? {
        val lines = response.split("\r\n", "\n", "\r").filter { it.isNotBlank() }
        val st = lines.find { it.startsWith("ST:", ignoreCase = true) }?.substringAfter(":")?.trim() ?: ""
        val usn = lines.find { it.startsWith("USN:", ignoreCase = true) }?.substringAfter(":")?.trim() ?: ""
        val server = lines.find { it.startsWith("SERVER:", ignoreCase = true) }?.substringAfter(":")?.trim() ?: ""
        
        val uniqueId = if (usn.isNotEmpty()) {
            usn.substringAfter("uuid:").substringBefore("::")
        } else {
            ""
        }
        
        val locationLine = lines.find { it.startsWith("LOCATION:", ignoreCase = true) }
        val location = locationLine?.substringAfter(":")?.trim()
        
        val ip = if (!location.isNullOrEmpty()) {
            try {
                val cleanLocation = if (location.startsWith("//")) "http:$location" else location
                if (cleanLocation.startsWith("http", ignoreCase = true)) {
                    val uri = java.net.URI(cleanLocation)
                    uri.host ?: packetIp
                } else {
                    packetIp
                }
            } catch (e: Exception) {
                packetIp
            }
        } else {
            packetIp
        }

        val searchContent = "$st $usn $server $response"
        
        return when {
            searchContent.contains("roku", ignoreCase = true) -> {
                val id = if (uniqueId.isNotEmpty()) uniqueId else "roku-$ip"
                Device(id = id, name = "Roku TV", ipAddress = ip, type = DeviceType.ROKU)
            }
            searchContent.contains("samsung", ignoreCase = true) -> {
                val id = if (uniqueId.isNotEmpty()) uniqueId else "samsung-$ip"
                Device(id = id, name = "Samsung TV", ipAddress = ip, type = DeviceType.SAMSUNG)
            }
            searchContent.contains("lge", ignoreCase = true) || searchContent.contains("webos", ignoreCase = true) -> {
                val id = if (uniqueId.isNotEmpty()) uniqueId else "lg-$ip"
                Device(id = id, name = "LG webOS", ipAddress = ip, type = DeviceType.LG)
            }
            searchContent.contains("chromecast", ignoreCase = true) || 
            searchContent.contains("google", ignoreCase = true) || 
            searchContent.contains("android", ignoreCase = true) ||
            searchContent.contains("dial", ignoreCase = true) -> {
                val id = if (uniqueId.isNotEmpty()) uniqueId else "android-$ip"
                Device(id = id, name = "Android TV", ipAddress = ip, type = DeviceType.ANDROID_TV)
            }
            searchContent.contains("MediaRenderer", ignoreCase = true) -> {
                val id = if (uniqueId.isNotEmpty()) uniqueId else "smarttv-$ip"
                Device(id = id, name = "Smart TV", ipAddress = ip, type = DeviceType.ANDROID_TV)
            }
            else -> null
        }
    }
}
