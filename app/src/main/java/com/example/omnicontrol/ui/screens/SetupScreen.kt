package com.example.omnicontrol.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.omnicontrol.data.model.Device
import com.example.omnicontrol.ui.components.PairingCodeDialog
import com.example.omnicontrol.ui.remote.DiscoveryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onDevicePaired: () -> Unit,
    onBack: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val error by viewModel.error.collectAsState()
    val isIrSupported = viewModel.isIrSupported

    val snackbarHostState = remember { SnackbarHostState() }
    var connectingDeviceId by remember { mutableStateOf<String?>(null) }

    var permissionsGranted by remember {
        mutableStateOf(checkPermissions(context))
    }
    var showPermissionExplanation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            permissionsGranted = true
            viewModel.startDiscovery()
        } else {
            showPermissionExplanation = true
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(getRequiredPermissions())
        } else {
            viewModel.startDiscovery()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
            connectingDeviceId = null
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState == com.example.omnicontrol.domain.ConnectionState.CONNECTED) {
            onDevicePaired()
        } else if (connectionState == com.example.omnicontrol.domain.ConnectionState.ERROR) {
            connectingDeviceId = null
        }
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Permissions Required") },
            text = { Text("OmniControl needs Location and Bluetooth permissions to discover TVs on your network. Please grant them in settings.") },
            confirmButton = {
                Button(onClick = {
                    showPermissionExplanation = false
                    openAppSettings(context)
                }) { Text("OPEN SETTINGS") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("CANCEL") }
            }
        )
    }

    SetupScreenContent(
        discoveredDevices = discoveredDevices,
        isScanning = isScanning,
        connectionState = connectionState,
        connectingDeviceId = connectingDeviceId,
        isIrSupported = isIrSupported,
        onBack = onBack,
        onConnectDevice = { 
            connectingDeviceId = it.id
            viewModel.initiateConnection(it) 
        },
        onSubmitPairingCode = { viewModel.submitPairingCode(it) },
        onCancelConnection = { 
            connectingDeviceId = null
            viewModel.cancelConnection() 
        },
        onTestIrCommand = { brand, cmd -> viewModel.testIrCommand(brand, cmd) },
        onSaveIrDevice = { viewModel.saveIrDevice(it) },
        snackbarHostState = snackbarHostState
    )
}

private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun checkPermissions(context: Context): Boolean {
    return getRequiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreenContent(
    discoveredDevices: List<Device>,
    isScanning: Boolean,
    connectionState: com.example.omnicontrol.domain.ConnectionState,
    connectingDeviceId: String?,
    isIrSupported: Boolean,
    onBack: () -> Unit,
    onConnectDevice: (Device) -> Unit,
    onSubmitPairingCode: (String) -> Unit,
    onCancelConnection: () -> Unit,
    onTestIrCommand: (String, String) -> Unit,
    onSaveIrDevice: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var showManualIpDialog by remember { mutableStateOf(false) }
    var showIrSetupDialog by remember { mutableStateOf(false) }

    val showPairingDialog = connectionState == com.example.omnicontrol.domain.ConnectionState.PAIRING_REQUIRED

    if (showPairingDialog) {
        PairingCodeDialog(
            onDismiss = onCancelConnection,
            onSubmit = onSubmitPairingCode
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text("Remote Pro", 
                        color = MaterialTheme.colorScheme.primary, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Connect your TV",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = if (isScanning) "Searching for devices..." else if (connectingDeviceId != null) "Connecting..." else "Scan complete.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Large Circular TV Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "AVAILABLE DEVICES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(discoveredDevices) { device ->
                    DiscoveryItem(
                        device = device,
                        isConnecting = connectingDeviceId == device.id,
                        onClick = {
                            onConnectDevice(device)
                        }
                    )
                }
                
                if (discoveredDevices.isEmpty() && !isScanning) {
                    item {
                        Text(
                            text = "No devices found. Ensure your TV is on and connected to the same WiFi.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                }
            }

            // Info Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Check your TV screen for a PIN or prompt after selecting it.",
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            Button(
                onClick = { showManualIpDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Router, null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Enter IP Address", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                }
            }

            if (isIrSupported) {
                TextButton(
                    onClick = { showIrSetupDialog = true },
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Text("IR Setup", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
            }

            if (showManualIpDialog) {
                ManualIpDialog(
                    onDismiss = { showManualIpDialog = false },
                    onConfirm = { ip, type ->
                        onConnectDevice(
                            Device(
                                id = "manual-$ip",
                                name = "Manual Device",
                                ipAddress = ip,
                                type = type
                            )
                        )
                        showManualIpDialog = false
                    }
                )
            }

            if (showIrSetupDialog) {
                IrSetupDialog(
                    onDismiss = { showIrSetupDialog = false },
                    onConfirm = { brand ->
                        onSaveIrDevice(brand)
                        showIrSetupDialog = false
                    },
                    onTestIrCommand = onTestIrCommand
                )
            }
        }
    }
}

@Composable
fun IrSetupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit, onTestIrCommand: (String, String) -> Unit) {
    var selectedBrand by remember { mutableStateOf("Samsung") }
    var step by remember { mutableIntStateOf(1) } // 1: Pick, 2: Test
    val brands = listOf("Samsung", "Sony", "LG", "Vizio", "TCL")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if(step == 1) "Pick TV Brand" else "Test Power Button", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            if (step == 1) {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(brands) { brand ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { selectedBrand = brand },
                            color = if(selectedBrand == brand) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                        ) {
                            Text(brand, color = if(selectedBrand == brand) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Point your phone at the TV and press the button below.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(2.dp, Color(0xFFFF3B30), CircleShape)
                            .clickable { onTestIrCommand(selectedBrand, "power") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Did the TV turn on/off?", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (step == 1) step = 2
                else onConfirm(selectedBrand)
            }) {
                Text(if(step == 1) "NEXT" else "YES, IT WORKS", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }
    )
}

@Composable
fun DiscoveryItem(device: Device, isConnecting: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isConnecting) { onClick() },
        color = MaterialTheme.colorScheme.secondary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tv, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(device.type.name + " TV", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ManualIpDialog(onDismiss: () -> Unit, onConfirm: (String, com.example.omnicontrol.data.model.DeviceType) -> Unit) {
    var ip by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(com.example.omnicontrol.data.model.DeviceType.ANDROID_TV) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        title = { Text("Manual Connection", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Device Type:")
                Box {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        onClick = { expanded = true },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(selectedType.name, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        com.example.omnicontrol.data.model.DeviceType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    selectedType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("IP Address:")
                TextField(
                    value = ip,
                    onValueChange = { ip = it },
                    placeholder = { Text("e.g. 192.168.1.100") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if(ip.isNotBlank()) onConfirm(ip, selectedType) }) { 
                Text("CONNECT", fontWeight = FontWeight.Black) 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }
    )
}
