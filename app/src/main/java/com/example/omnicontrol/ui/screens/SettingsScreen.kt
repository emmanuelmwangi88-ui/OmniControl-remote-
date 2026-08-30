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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.omnicontrol.data.model.Device
import com.example.omnicontrol.ui.remote.DiscoveryViewModel
import com.example.omnicontrol.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    discoveryViewModel: DiscoveryViewModel = hiltViewModel()
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val largeButtonMode by viewModel.largeButtonMode.collectAsState()
    val hapticFeedback by viewModel.hapticFeedback.collectAsState()
    val savedDevices by discoveryViewModel.pairedDevices.collectAsState(initial = emptyList())

    SettingsScreenContent(
        isDarkMode = isDarkMode,
        largeButtonMode = largeButtonMode,
        hapticFeedback = hapticFeedback,
        savedDevices = savedDevices,
        onBack = onBack,
        onToggleDarkMode = { viewModel.toggleDarkMode(it) },
        onToggleHaptics = { viewModel.toggleHaptics(it) },
        onToggleLargeButtons = { viewModel.toggleLargeButtons(it) },
        onDeleteDevice = { discoveryViewModel.removeDevice(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    isDarkMode: Boolean,
    largeButtonMode: Boolean,
    hapticFeedback: Boolean,
    savedDevices: List<Device>,
    onBack: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleLargeButtons: (Boolean) -> Unit,
    onDeleteDevice: (Device) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // General Section
            item {
                Text(
                    "General",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsToggle(
                            icon = Icons.Default.DarkMode,
                            title = "Dark Mode",
                            subtitle = "Switch between dark and light themes",
                            checked = isDarkMode,
                            onCheckedChange = onToggleDarkMode
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsToggle(
                            icon = Icons.Default.Vibration,
                            title = "Haptic Feedback",
                            subtitle = "Vibrate on button press",
                            checked = hapticFeedback,
                            onCheckedChange = onToggleHaptics
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsToggle(
                            icon = Icons.Default.ZoomIn,
                            title = "Large Controls",
                            subtitle = "Increase size of remote buttons",
                            checked = largeButtonMode,
                            onCheckedChange = onToggleLargeButtons
                        )
                    }
                }
            }

            // Saved Devices Section
            item {
                Text(
                    "Saved Devices",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            items(savedDevices) { device ->
                SavedDeviceItem(device, onDelete = { onDeleteDevice(device) })
            }

            // About Section
            item {
                Text(
                    "About",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SettingsInputAntenna, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Remote Pro", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Version 2.4.1 (Build 8902)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text("Privacy Policy", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape).align(Alignment.CenterVertically))
                            Text("Terms of Service", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
fun SavedDeviceItem(device: Device, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tv, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${device.type.name} • Connected", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            IconButton(onClick = { /* Edit */ }) {
                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SettingsScreenDarkPreview() {
    com.example.omnicontrol.ui.theme.OmniControlTheme(darkTheme = true) {
        SettingsScreenContent(
            isDarkMode = true,
            largeButtonMode = false,
            hapticFeedback = true,
            savedDevices = listOf(
                Device(id = "1", name = "Living Room TV", ipAddress = "192.168.1.10", type = com.example.omnicontrol.data.model.DeviceType.SAMSUNG)
            ),
            onBack = {},
            onToggleDarkMode = {},
            onToggleHaptics = {},
            onToggleLargeButtons = {},
            onDeleteDevice = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SettingsScreenLightPreview() {
    com.example.omnicontrol.ui.theme.OmniControlTheme(darkTheme = false) {
        SettingsScreenContent(
            isDarkMode = false,
            largeButtonMode = true,
            hapticFeedback = false,
            savedDevices = listOf(
                Device(id = "1", name = "Living Room TV", ipAddress = "192.168.1.10", type = com.example.omnicontrol.data.model.DeviceType.SAMSUNG)
            ),
            onBack = {},
            onToggleDarkMode = {},
            onToggleHaptics = {},
            onToggleLargeButtons = {},
            onDeleteDevice = {}
        )
    }
}
