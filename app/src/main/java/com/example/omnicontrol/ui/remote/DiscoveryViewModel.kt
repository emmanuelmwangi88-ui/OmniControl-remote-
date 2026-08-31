package com.example.omnicontrol.ui.remote

import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omnicontrol.data.discovery.DiscoveryService
import com.example.omnicontrol.data.model.Device
import com.example.omnicontrol.data.model.DeviceType
import com.example.omnicontrol.data.remote.IrController
import com.example.omnicontrol.data.repository.DeviceRepository
import com.example.omnicontrol.domain.ConnectionResult
import com.example.omnicontrol.domain.ConnectionState
import com.example.omnicontrol.domain.RemoteController
import com.example.omnicontrol.domain.RemoteControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val discoveryService: DiscoveryService,
    private val repository: DeviceRepository,
    private val factory: RemoteControllerFactory,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val pairedDevices = repository.allDevices
    private var currentPairingController: RemoteController? = null

    val isIrSupported: Boolean by lazy {
        val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        irManager != null && irManager.hasIrEmitter()
    }
    
    fun startDiscovery() {
        viewModelScope.launch {
            _isScanning.value = true
            discoveryService.startScanning().collect {
                _discoveredDevices.value = it
            }
            _isScanning.value = false
        }
    }

    fun initiateConnection(device: Device) {
        viewModelScope.launch {
            _error.value = null
            currentPairingController?.disconnect()
            
            val controller = factory.create(device)
            currentPairingController = controller
            
            launch {
                controller?.connectionState?.collectLatest {
                    _connectionState.value = it
                }
            }

            val result = controller?.connect()
            if (result is ConnectionResult.Failure) {
                _error.value = result.message
            } else if (result is ConnectionResult.Success) {
                repository.saveDevice(device)
            }
        }
    }

    fun submitPairingCode(pin: String) {
        viewModelScope.launch {
            try {
                _error.value = null
                currentPairingController?.pair(pin)
            } catch (e: Exception) {
                _error.value = "Pairing failed: ${e.message}"
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }

    fun cancelConnection() {
        viewModelScope.launch {
            currentPairingController?.disconnect()
            currentPairingController = null
            _connectionState.value = ConnectionState.DISCONNECTED
            _error.value = null
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun pairDevice(device: Device) {
        viewModelScope.launch {
            repository.saveDevice(device)
        }
    }

    fun removeDevice(device: Device) {
        viewModelScope.launch {
            repository.removeDevice(device)
        }
    }

    fun testIrCommand(brand: String, command: String) {
        val irController = IrController(context)
        irController.setBrand(brand)
        viewModelScope.launch {
            if (command == "power") irController.powerToggle()
        }
    }

    fun saveIrDevice(brand: String) {
        viewModelScope.launch {
            repository.saveDevice(
                Device(
                    id = "ir-$brand-${System.currentTimeMillis()}",
                    name = "$brand TV (IR)",
                    ipAddress = "IR",
                    type = DeviceType.IR,
                    authToken = brand // Store brand in authToken field for IR
                )
            )
        }
    }
}
