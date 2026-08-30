package com.example.omnicontrol.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.omnicontrol.data.model.Device
import com.example.omnicontrol.data.model.DeviceType
import com.example.omnicontrol.ui.remote.DiscoveryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onDeviceSelected: (Device) -> Unit,
    onAddDevice: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val devices by viewModel.pairedDevices.collectAsState(initial = emptyList())

    DeviceListScreenContent(
        devices = devices,
        onDeviceSelected = onDeviceSelected,
        onAddDevice = onAddDevice,
        onOpenSettings = onOpenSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreenContent(
    devices: List<Device>,
    onDeviceSelected: (Device) -> Unit,
    onAddDevice: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text("Devices", 
                        color = MaterialTheme.colorScheme.onSurface, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { /* Menu */ }) {
                        Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDevice,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp).size(64.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(32.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Your Devices",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "Select a device to control or manage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(devices) { device ->
                    DeviceListItem(
                        device = device,
                        onClick = { onDeviceSelected(device) }
                    )
                }
                
                if (devices.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            Text("No devices saved yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceListItem(device: Device, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.secondary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(device.type) {
                        DeviceType.ANDROID_TV -> Icons.Default.Cast
                        else -> Icons.Default.Tv
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${device.type.name} TV • Online",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DeviceListScreenDarkPreview() {
    com.example.omnicontrol.ui.theme.OmniControlTheme(darkTheme = true) {
        DeviceListScreenContent(
            devices = listOf(
                Device(id = "1", name = "Living Room TV", ipAddress = "192.168.1.10", type = DeviceType.SAMSUNG),
                Device(id = "2", name = "Bedroom Roku", ipAddress = "192.168.1.11", type = DeviceType.ROKU)
            ),
            onDeviceSelected = {},
            onAddDevice = {},
            onOpenSettings = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DeviceListScreenLightPreview() {
    com.example.omnicontrol.ui.theme.OmniControlTheme(darkTheme = false) {
        DeviceListScreenContent(
            devices = listOf(
                Device(id = "1", name = "Living Room TV", ipAddress = "192.168.1.10", type = DeviceType.SAMSUNG),
                Device(id = "2", name = "Bedroom Roku", ipAddress = "192.168.1.11", type = DeviceType.ROKU)
            ),
            onDeviceSelected = {},
            onAddDevice = {},
            onOpenSettings = {}
        )
    }
}
