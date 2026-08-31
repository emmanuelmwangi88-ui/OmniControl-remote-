package com.example.omnicontrol.domain

import android.content.Context
import com.example.omnicontrol.data.model.Device
import com.example.omnicontrol.data.model.DeviceType
import com.example.omnicontrol.data.remote.AndroidTvController
import com.example.omnicontrol.data.remote.BluetoothController
import com.example.omnicontrol.data.remote.IrController
import com.example.omnicontrol.data.remote.LgController
import com.example.omnicontrol.data.remote.RokuController
import com.example.omnicontrol.data.remote.SamsungController
import com.example.omnicontrol.di.WebSocketClient
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteControllerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    @WebSocketClient private val wsClient: OkHttpClient
) {
    fun create(device: Device): RemoteController? {
        return when (device.type) {
            DeviceType.ROKU -> RokuController(device.ipAddress, httpClient)
            DeviceType.SAMSUNG -> SamsungController(device.ipAddress, wsClient, token = device.authToken)
            DeviceType.LG -> LgController(device.ipAddress, wsClient, clientKey = device.authToken)
            DeviceType.IR -> IrController(context)
            DeviceType.ANDROID_TV -> AndroidTvController(context, device.ipAddress, device.authToken)
            DeviceType.BLUETOOTH -> BluetoothController(device.ipAddress, context)
        }
    }
}
