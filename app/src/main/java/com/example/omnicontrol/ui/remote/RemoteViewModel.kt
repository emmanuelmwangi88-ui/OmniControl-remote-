package com.example.omnicontrol.ui.remote

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omnicontrol.data.discovery.DiscoveryService
import com.example.omnicontrol.data.model.Device
import com.example.omnicontrol.data.model.DeviceType
import com.example.omnicontrol.data.model.AppShortcut
import com.example.omnicontrol.data.remote.RokuController
import com.example.omnicontrol.data.repository.DeviceRepository
import com.example.omnicontrol.data.repository.ShortcutRepository
import com.example.omnicontrol.domain.ConnectionResult
import com.example.omnicontrol.domain.ConnectionState
import com.example.omnicontrol.domain.DpadKey
import com.example.omnicontrol.domain.RemoteController
import com.example.omnicontrol.domain.RemoteControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val shortcutRepository: ShortcutRepository,
    private val factory: RemoteControllerFactory,
    private val discoveryService: DiscoveryService
) : ViewModel() {

    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    private val _shortcuts = MutableStateFlow<List<AppShortcut>>(emptyList())
    val shortcuts: StateFlow<List<AppShortcut>> = _shortcuts.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentController: RemoteController? = null
    private var connectionStateJob: Job? = null

    init {
        viewModelScope.launch {
            repository.allDevices.collectLatest { devices ->
                if ((_selectedDevice.value == null) && devices.isNotEmpty()) {
                    val last = devices.maxByOrNull { it.lastConnected } ?: devices.first()
                    _selectedDevice.value = last
                    selectDevice(last)
                }
            }
        }
        viewModelScope.launch {
            shortcutRepository.allShortcuts.collectLatest {
                if (it.isEmpty()) {
                    populateDefaultShortcuts()
                } else {
                    _shortcuts.value = it
                    // Ensure mandatory apps are present
                    if (it.size < 5) { // Simple heuristic
                        populateDefaultShortcuts()
                    }
                }
            }
        }
        
        // Periodic IP Recovery
        viewModelScope.launch {
            while (isActive) {
                delay(60000) // Every minute
                val current = _selectedDevice.value ?: continue
                if (current.type == DeviceType.IR) continue
                
                Log.d("RemoteViewModel", "Running periodic re-discovery for IP recovery")
                discoveryService.startScanning().take(1).collect { discovered ->
                    val match = discovered.find { it.id == current.id }
                    if (match != null && match.ipAddress != current.ipAddress) {
                        Log.d("RemoteViewModel", "IP change detected for ${current.name}: ${match.ipAddress}")
                        repository.updateIpAddress(current.id, match.ipAddress)
                        selectDevice(match)
                    }
                }
            }
        }
    }

    private suspend fun populateDefaultShortcuts() {
        val defaults = listOf(
            AppShortcut(
                name = "YouTube",
                appId = "youtube",
                colorHex = "#FF0000",
                iconType = "ICON",
                iconUrl = "https://cdn.pixabay.com/photo/2021/06/15/12/51/youtube-6338464_1280.png"
            ),
            AppShortcut(
                name = "Spotify",
                appId = "spotify",
                colorHex = "#1DB954",
                iconType = "ICON",
                iconUrl = "https://cdn.iconscout.com/icon/free/png-256/free-spotify-logo-icon-download-in-svg-png-gif-file-formats--brand-social-media-card-pack-logos-icons-226521.png"
            ),
            AppShortcut(
                name = "Netflix",
                appId = "netflix",
                colorHex = "#E50914",
                iconType = "ICON",
                iconUrl = "https://cdn.iconscout.com/icon/free/png-256/free-netflix-logo-icon-download-in-svg-png-gif-file-formats--brand-social-media-card-pack-logos-icons-226514.png"
            ),
            AppShortcut(
                name = "Prime Video",
                appId = "primevideo",
                colorHex = "#00A8E1",
                iconType = "ICON",
                iconUrl = "https://cdn.iconscout.com/icon/free/png-256/free-amazon-prime-video-logo-icon-download-in-svg-png-gif-file-formats--brand-social-media-pack-logos-icons-226487.png"
            ),
            AppShortcut(
                name = "Plex",
                appId = "plex",
                colorHex = "#EBAF00",
                iconType = "ICON",
                iconUrl = "https://cdn.iconscout.com/icon/free/png-256/free-plex-logo-icon-download-in-svg-png-gif-file-formats--brand-social-media-pack-logos-icons-226519.png"
            ),
            AppShortcut(
                name = "Play Store",
                appId = "playstore",
                colorHex = "#41F0BB",
                iconType = "ICON",
                iconUrl = "https://cdn.iconscout.com/icon/free/png-256/free-google-play-store-logo-icon-download-in-svg-png-gif-file-formats--brand-social-media-pack-logos-icons-226492.png"
            ),
            AppShortcut(
                name = "Browser",
                appId = "browser",
                colorHex = "#4285F4",
                iconType = "ICON",
                iconUrl = "https://cdn.iconscout.com/icon/free/png-256/free-google-chrome-logo-icon-download-in-svg-png-gif-file-formats--brand-social-media-card-pack-logos-icons-226491.png"
            ),
            AppShortcut(
                name = "Disney+",
                appId = "disneyplus",
                colorHex = "#113CCF",
                iconType = "ICON",
                iconUrl = "https://cdn.iconscout.com/icon/free/png-256/free-disney-plus-logo-icon-download-in-svg-png-gif-file-formats--brand-social-media-pack-logos-icons-226490.png"
            )
        )
        defaults.forEach { shortcutRepository.addShortcut(it) }
    }

    fun selectDevice(device: Device) {
        viewModelScope.launch {
            repository.updateLastConnected(device.id)
            currentController?.disconnect()
            connectionStateJob?.cancel()
            
            _selectedDevice.value = device
            val controller = factory.create(device)
            currentController = controller
            
            connectionStateJob = launch {
                controller?.connectionState?.collect { state ->
                    _connectionState.value = state
                }
            }
            
            controller?.setTokenListener { token ->
                viewModelScope.launch {
                    repository.updateToken(device.id, token)
                }
            }
            
            val result = controller?.connect()
            Log.d("RemoteViewModel", "Connection result for ${device.name}: $result")
            
            // If Roku, sync apps
            if (device.type == DeviceType.ROKU && controller is RokuController) {
                val apps = controller.queryApps()
                if (apps.isNotEmpty()) {
                    if (_shortcuts.value.isEmpty()) {
                        for (app in apps) {
                            shortcutRepository.addShortcut(app)
                        }
                    }
                }
            }
        }
    }

    fun launchApp(appId: String) {
        viewModelScope.launch {
            currentController?.launchApp(appId)
        }
    }

    fun sendText(text: String) {
        viewModelScope.launch {
            currentController?.sendText(text)
        }
    }

    fun addShortcut(shortcut: AppShortcut) {
        viewModelScope.launch {
            shortcutRepository.addShortcut(shortcut)
        }
    }

    fun deleteShortcut(shortcut: AppShortcut) {
        viewModelScope.launch {
            shortcutRepository.deleteShortcut(shortcut)
        }
    }

    fun reconnect() {
        _selectedDevice.value?.let { selectDevice(it) }
    }

    fun submitPairingCode(pin: String) {
        viewModelScope.launch {
            currentController?.pair(pin)
        }
    }

    fun sendCommand(command: RemoteCommand) {
        Log.d("RemoteViewModel", "Sending command: $command to ${selectedDevice.value?.name}")
        viewModelScope.launch {
            try {
                currentController?.let { controller ->
                    when (command) {
                        RemoteCommand.POWER -> controller.powerToggle()
                        RemoteCommand.VOL_UP -> controller.volumeUp()
                        RemoteCommand.VOL_DOWN -> controller.volumeDown()
                        RemoteCommand.MUTE -> controller.mute()
                        RemoteCommand.UP -> controller.dpad(DpadKey.UP)
                        RemoteCommand.DOWN -> controller.dpad(DpadKey.DOWN)
                        RemoteCommand.LEFT -> controller.dpad(DpadKey.LEFT)
                        RemoteCommand.RIGHT -> controller.dpad(DpadKey.RIGHT)
                        RemoteCommand.SELECT -> controller.dpad(DpadKey.SELECT)
                        RemoteCommand.HOME -> controller.home()
                        RemoteCommand.BACK -> controller.back()
                        RemoteCommand.PLAY_PAUSE -> controller.playPause()
                        RemoteCommand.TV -> controller.tv()
                        RemoteCommand.CH_UP -> controller.channelUp()
                        RemoteCommand.CH_DOWN -> controller.channelDown()
                        RemoteCommand.REWIND -> controller.rewind()
                        RemoteCommand.FAST_FORWARD -> controller.fastForward()
                    }
                } ?: run {
                    Log.w("RemoteViewModel", "No controller available for command: $command")
                }
            } catch (e: Exception) {
                Log.e("RemoteViewModel", "Error sending command $command", e)
            }
        }
    }
}

enum class RemoteCommand {
    POWER, VOL_UP, VOL_DOWN, MUTE, UP, DOWN, LEFT, RIGHT, SELECT, HOME, BACK, PLAY_PAUSE, TV,
    CH_UP, CH_DOWN, REWIND, FAST_FORWARD
}
