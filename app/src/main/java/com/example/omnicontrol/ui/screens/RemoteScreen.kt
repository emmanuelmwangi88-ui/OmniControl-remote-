package com.example.omnicontrol.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.example.omnicontrol.data.model.AppShortcut
import com.example.omnicontrol.data.model.Device
import com.example.omnicontrol.domain.ConnectionState
import com.example.omnicontrol.domain.DpadKey
import com.example.omnicontrol.ui.remote.RemoteCommand
import com.example.omnicontrol.ui.remote.RemoteViewModel
import com.example.omnicontrol.ui.settings.SettingsViewModel
import com.example.omnicontrol.util.HapticUtil
import kotlinx.coroutines.delay
import com.example.omnicontrol.ui.components.PairingCodeDialog
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

// NOTE ON WIRING:
// This file adds a few capabilities that need small additions elsewhere in the project:
//
// 1) RemoteCommand needs five new cases for the features below to compile:
//       CH_UP, CH_DOWN, PLAY_PAUSE, REWIND, FAST_FORWARD
//
// 2) The multi-device picker and pairing-code dialog are wired with sensible no-op
//    defaults (empty device list, empty lambdas) so this file compiles as-is even
//    before RemoteViewModel exposes them. When ready, extend RemoteViewModel with
//    something like `availableDevices: StateFlow<List<Device>>`, `selectDevice()`,
//    `scanForDevices()`, and `submitPairingCode()`, then pass them through from
//    RemoteScreen() the same way selectedDevice/shortcuts are passed today.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    onSwitchDevice: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: RemoteViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val activeDevice by viewModel.selectedDevice.collectAsState()
    val shortcuts by viewModel.shortcuts.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val hapticEnabled by settingsViewModel.hapticFeedback.collectAsState()

    RemoteScreenContent(
        activeDevice = activeDevice,
        shortcuts = shortcuts,
        connectionState = connectionState,
        hapticEnabled = hapticEnabled,
        onSwitchDevice = onSwitchDevice,
        onOpenSettings = onOpenSettings,
        onSendCommand = { viewModel.sendCommand(it) },
        onSendText = { viewModel.sendText(it) },
        onLaunchApp = { viewModel.launchApp(it) },
        onReconnect = { viewModel.reconnect() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreenContent(
    activeDevice: Device?,
    shortcuts: List<AppShortcut>,
    connectionState: ConnectionState,
    hapticEnabled: Boolean,
    onSwitchDevice: () -> Unit,
    onOpenSettings: () -> Unit,
    onSendCommand: (RemoteCommand) -> Unit,
    onSendText: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    onReconnect: () -> Unit,
    availableDevices: List<Device> = emptyList(),
    onSelectDevice: (Device) -> Unit = {},
    onScanDevices: () -> Unit = {},
    onEnterChannel: (String) -> Unit = {},
    onSubmitPairingCode: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var isTouchpadMode by remember { mutableStateOf(false) }
    var showTypeDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showPowerConfirm by remember { mutableStateOf(false) }
    var showChannelKeypad by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.getOrNull(0)
            if (!spokenText.isNullOrBlank()) {
                onSendText(spokenText)
            }
        }
    }

    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceClick: (() -> Unit) -> Unit = { action ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > 100) {
            action()
            lastClickTime = currentTime
        }
    }

    val hapticFeedback: () -> Unit = {
        if (hapticEnabled) HapticUtil.vibrate(context)
    }

    if (showTypeDialog) {
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text("Type on TV") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Enter text to send...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSendText(textInput)
                            textInput = ""
                        }
                        showTypeDialog = false
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTypeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPowerConfirm) {
        AlertDialog(
            onDismissRequest = { showPowerConfirm = false },
            icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFFFF3B30)) },
            title = { Text("Turn off ${activeDevice?.name ?: "this device"}?") },
            confirmButton = {
                TextButton(onClick = {
                    showPowerConfirm = false
                    hapticFeedback()
                    onSendCommand(RemoteCommand.POWER)
                }) {
                    Text("Turn off", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPowerConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showChannelKeypad) {
        ChannelKeypadDialog(
            onDismiss = { showChannelKeypad = false },
            onSubmit = { digits -> hapticFeedback(); onEnterChannel(digits) }
        )
    }

    if (showPairingDialog) {
        PairingCodeDialog(
            onDismiss = { showPairingDialog = false },
            onSubmit = { code -> onSubmitPairingCode(code) }
        )
    }

    if (showDeviceSheet) {
        DeviceSelectorSheet(
            devices = availableDevices,
            activeDevice = activeDevice,
            onSelect = { device -> hapticFeedback(); onSelectDevice(device) },
            onScan = onScanDevices,
            onDismiss = { showDeviceSheet = false }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSwitchDevice) {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = "Switch device",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Remote",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        radius = 1000f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConnectionStateBanner(
                    state = connectionState,
                    onRetry = onReconnect,
                    onEnterCode = { showPairingDialog = true }
                )

                // Header: Device Selector, Antenna (Reconnect) & Touchpad Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Device Selector & Reconnect
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onReconnect, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.SettingsInputAntenna,
                                contentDescription = "Reconnect to device",
                                tint = if (connectionState == ConnectionState.CONNECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showDeviceSheet = true }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .semantics { contentDescription = "Choose a device to control" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        when (connectionState) {
                                            ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                            ConnectionState.CONNECTING -> Color(0xFFFFC107)
                                            else -> Color(0xFFF44336)
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeDevice?.let { "${it.type.name} - ${it.name}" } ?: "Select Device",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Touchpad Toggle
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { debounceClick { isTouchpadMode = !isTouchpadMode; hapticFeedback() } }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .semantics { contentDescription = if (isTouchpadMode) "Switch to D-pad mode" else "Switch to touchpad mode" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = if (isTouchpadMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isTouchpadMode) "TOUCH" else "DPAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTouchpadMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                // Mute and Power
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularActionBtn(
                        icon = Icons.AutoMirrored.Filled.VolumeMute,
                        label = "Mute"
                    ) { debounceClick { hapticFeedback(); onSendCommand(RemoteCommand.MUTE) } }

                    CircularActionBtn(
                        icon = Icons.Default.PowerSettingsNew,
                        label = "Power",
                        tint = Color(0xFFFF3B30),
                        borderColor = Color(0xFFFF3B30).copy(alpha = 0.3f)
                    ) { debounceClick { showPowerConfirm = true } }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Navigation Section
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTouchpadMode) {
                            ModernTouchpadArea(size = 220.dp) { direction: DpadKey ->
                                hapticFeedback()
                                onSendCommand(
                                    when (direction) {
                                        DpadKey.UP -> RemoteCommand.UP
                                        DpadKey.DOWN -> RemoteCommand.DOWN
                                        DpadKey.LEFT -> RemoteCommand.LEFT
                                        DpadKey.RIGHT -> RemoteCommand.RIGHT
                                        DpadKey.SELECT -> RemoteCommand.SELECT
                                    }
                                )
                            }
                        } else {
                            Image1DPad(onCommand = { cmd: RemoteCommand -> debounceClick { hapticFeedback(); onSendCommand(cmd) } })
                        }
                    }
                }

                // Lower Control Section: 3-Column Ergonomic Layout (Type & Voice in Center Column)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left Column: Back + Volume
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularActionBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back") {
                                debounceClick { hapticFeedback(); onSendCommand(RemoteCommand.BACK) }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("VOL", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                StripControl(
                                    topIcon = Icons.Default.Add,
                                    bottomIcon = Icons.Default.Remove,
                                    onTop = { hapticFeedback(); onSendCommand(RemoteCommand.VOL_UP) },
                                    onBottom = { hapticFeedback(); onSendCommand(RemoteCommand.VOL_DOWN) },
                                    repeatEnabled = true,
                                    topLabel = "Volume up",
                                    bottomLabel = "Volume down"
                                )
                            }
                        }

                        // Center Column: Home + Type & Voice Buttons
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularActionBtn(Icons.Default.Home, "Home") {
                                debounceClick { hapticFeedback(); onSendCommand(RemoteCommand.HOME) }
                            }

                            // Typing Button
                            CircularActionBtn(
                                icon = Icons.Default.Keyboard,
                                label = "Type on TV",
                                tint = MaterialTheme.colorScheme.primary,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            ) { debounceClick { hapticFeedback(); showTypeDialog = true } }

                            // Voice Button
                            CircularActionBtn(
                                icon = Icons.Default.Mic,
                                label = "Voice input",
                                tint = Color(0xFF4CAF50),
                                borderColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                            ) {
                                debounceClick {
                                    hapticFeedback()
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to type on TV...")
                                    }
                                    try {
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        // Fallback or ignore if speech recognizer is not available
                                    }
                                }
                            }
                        }

                        // Right Column: TV + Channel
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularActionBtn(Icons.Default.Tv, "TV input") {
                                debounceClick { hapticFeedback(); onSendCommand(RemoteCommand.TV) }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "CH",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { debounceClick { hapticFeedback(); showChannelKeypad = true } }
                                        .padding(2.dp)
                                        .semantics { contentDescription = "Enter a channel number" }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                StripControl(
                                    topIcon = Icons.Default.KeyboardArrowUp,
                                    bottomIcon = Icons.Default.KeyboardArrowDown,
                                    onTop = { hapticFeedback(); onSendCommand(RemoteCommand.CH_UP) },
                                    onBottom = { hapticFeedback(); onSendCommand(RemoteCommand.CH_DOWN) },
                                    repeatEnabled = true,
                                    topLabel = "Channel up",
                                    bottomLabel = "Channel down"
                                )
                            }
                        }
                    }
                }

                // Media Controls Section
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
                        Text(
                            "MEDIA",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            CircularActionBtn(Icons.Default.FastRewind, "Rewind") {
                                debounceClick { hapticFeedback(); onSendCommand(RemoteCommand.REWIND) }
                            }
                            CircularActionBtn(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                label = if (isPlaying) "Pause" else "Play"
                            ) {
                                debounceClick {
                                    hapticFeedback()
                                    isPlaying = !isPlaying
                                    onSendCommand(RemoteCommand.PLAY_PAUSE)
                                }
                            }
                            CircularActionBtn(Icons.Default.FastForward, "Fast forward") {
                                debounceClick { hapticFeedback(); onSendCommand(RemoteCommand.FAST_FORWARD) }
                            }
                        }
                    }
                }

                // Quick Launch Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        "QUICK LAUNCH",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    if (shortcuts.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Add your favorite apps for one-tap access",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(shortcuts) { shortcut ->
                            QuickLaunchTile(shortcut, onClick = { appId -> debounceClick { hapticFeedback(); onLaunchApp(appId) } })
                        }
                    }
                }
            }

            if (connectionState == ConnectionState.CONNECTING) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun ConnectionStateBanner(state: ConnectionState, onRetry: () -> Unit, onEnterCode: () -> Unit = {}) {
    val (text, color) = when (state) {
        ConnectionState.CONNECTED -> "Connected to TV" to Color(0xFF4CAF50)
        ConnectionState.CONNECTING -> "Connecting to TV..." to Color(0xFFFFC107)
        ConnectionState.PAIRING_REQUIRED -> "Check TV for pairing code" to Color(0xFF558BFF)
        ConnectionState.ERROR -> "Connection failed" to Color(0xFFF44336)
        ConnectionState.DISCONNECTED -> "TV is offline" to Color(0xFF757575)
        else -> "" to Color.Transparent
    }

    if (text.isNotEmpty()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (state == ConnectionState.CONNECTING) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Text(
                    text = text,
                    color = color,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                if (state == ConnectionState.ERROR || state == ConnectionState.DISCONNECTED) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "RETRY",
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onRetry() }
                            .padding(4.dp)
                    )
                }

                if (state == ConnectionState.PAIRING_REQUIRED) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "ENTER CODE",
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onEnterCode() }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StripControl(
    topIcon: ImageVector,
    bottomIcon: ImageVector,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    repeatEnabled: Boolean = false,
    topLabel: String = "",
    bottomLabel: String = ""
) {
    Surface(
        modifier = Modifier
            .width(54.dp)
            .height(120.dp),
        shape = RoundedCornerShape(27.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            if (repeatEnabled) {
                RepeatIconButton(onTop, modifier = Modifier.size(44.dp).semantics { contentDescription = topLabel }) {
                    Icon(topIcon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            } else {
                IconButton(onClick = onTop, modifier = Modifier.size(44.dp)) {
                    Icon(topIcon, contentDescription = topLabel, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }

            Box(modifier = Modifier
                .width(20.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))

            if (repeatEnabled) {
                RepeatIconButton(onBottom, modifier = Modifier.size(44.dp).semantics { contentDescription = bottomLabel }) {
                    Icon(bottomIcon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            } else {
                IconButton(onClick = onBottom, modifier = Modifier.size(44.dp)) {
                    Icon(bottomIcon, contentDescription = bottomLabel, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
fun RepeatIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown()
                        currentOnClick()
                        val job = scope.launch {
                            delay(500)
                            while (isActive) {
                                currentOnClick()
                                delay(180)
                            }
                        }
                        waitForUpOrCancellation()
                        job.cancel()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircularActionBtn(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "buttonPressScale"
    )

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        Surface(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .clickable(
                    indication = ripple(),
                    interactionSource = interactionSource
                ) { onClick() }
                .semantics { contentDescription = label },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun Image1DPad(onCommand: (RemoteCommand) -> Unit) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                        )
                    ),
                    shape = CircleShape
                )
        )

        DpadPart(Icons.Default.KeyboardArrowUp, Alignment.TopCenter, Modifier.padding(top = 12.dp), "Up") { onCommand(RemoteCommand.UP) }
        DpadPart(Icons.Default.KeyboardArrowDown, Alignment.BottomCenter, Modifier.padding(bottom = 12.dp), "Down") { onCommand(RemoteCommand.DOWN) }
        DpadPart(Icons.Default.KeyboardArrowLeft, Alignment.CenterStart, Modifier.padding(start = 12.dp), "Left") { onCommand(RemoteCommand.LEFT) }
        DpadPart(Icons.Default.KeyboardArrowRight, Alignment.CenterEnd, Modifier.padding(end = 12.dp), "Right") { onCommand(RemoteCommand.RIGHT) }

        Surface(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .clickable { onCommand(RemoteCommand.SELECT) }
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)
                .semantics { contentDescription = "Select" },
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("OK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun BoxScope.DpadPart(icon: ImageVector, alignment: Alignment, modifier: Modifier, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .align(alignment)
            .size(48.dp)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
    }
}

@Composable
fun QuickLaunchTile(shortcut: AppShortcut, onClick: (String) -> Unit) {
    val color = remember(shortcut.colorHex) {
        try { Color(android.graphics.Color.parseColor(shortcut.colorHex)) } catch (e: Exception) { Color(0xFF3A3A3C) }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable { onClick(shortcut.appId) }
    ) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            color = if (shortcut.iconUrl != null || shortcut.iconRes != null) Color.Transparent else color,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (shortcut.iconRes != null) {
                    Icon(
                        painter = painterResource(id = shortcut.iconRes),
                        contentDescription = shortcut.name,
                        tint = Color.Unspecified,
                        modifier = Modifier.fillMaxSize().padding(10.dp)
                    )
                } else if (shortcut.iconUrl != null) {
                    AsyncImage(
                        model = shortcut.iconUrl,
                        contentDescription = shortcut.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = shortcut.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = shortcut.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AddShortcutTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable { onClick() }
            .semantics { contentDescription = "Add a quick launch app" }
    ) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}

@Composable
fun ModernTouchpadArea(size: androidx.compose.ui.unit.Dp, onSwipe: (DpadKey) -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var feedbackDirection by remember { mutableStateOf<DpadKey?>(null) }
    val threshold = 50f

    LaunchedEffect(feedbackDirection) {
        if (feedbackDirection != null) {
            delay(250)
            feedbackDirection = null
        }
    }

    Surface(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        if (kotlin.math.abs(offsetX) > threshold) {
                            val dir = if (offsetX > 0) DpadKey.RIGHT else DpadKey.LEFT
                            feedbackDirection = dir
                            onSwipe(dir)
                            offsetX = 0f; offsetY = 0f
                        } else if (kotlin.math.abs(offsetY) > threshold) {
                            val dir = if (offsetY > 0) DpadKey.DOWN else DpadKey.UP
                            feedbackDirection = dir
                            onSwipe(dir)
                            offsetX = 0f; offsetY = 0f
                        }
                    },
                    onDragEnd = { offsetX = 0f; offsetY = 0f }
                )
            }
            .clickable { onSwipe(DpadKey.SELECT) }
            .semantics { contentDescription = "Touchpad — swipe to navigate, tap to select" },
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            val directionalIcon = when (feedbackDirection) {
                DpadKey.UP -> Icons.Default.KeyboardArrowUp
                DpadKey.DOWN -> Icons.Default.KeyboardArrowDown
                DpadKey.LEFT -> Icons.Default.KeyboardArrowLeft
                DpadKey.RIGHT -> Icons.Default.KeyboardArrowRight
                else -> null
            }
            if (directionalIcon != null) {
                Icon(
                    directionalIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Text(
                    "SWIPE TO NAVIGATE",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectorSheet(
    devices: List<Device>,
    activeDevice: Device?,
    onSelect: (Device) -> Unit,
    onScan: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Select a device", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

            if (devices.isEmpty()) {
                Text(
                    "No other devices found on this network yet.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                devices.forEach { device ->
                    val isSelected = device.id == activeDevice?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(device); onDismiss() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                device.type.name,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Currently connected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan for devices")
            }
        }
    }
}

@Composable
fun ChannelKeypadDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var digits by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter channel") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = digits.ifEmpty { "—" },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("⌫", "0", "OK")
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            Surface(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        when (key) {
                                            "⌫" -> digits = digits.dropLast(1)
                                            "OK" -> if (digits.isNotEmpty()) { onSubmit(digits); onDismiss() }
                                            else -> if (digits.length < 4) digits += key
                                        }
                                    },
                                shape = CircleShape,
                                color = if (key == "OK") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(key, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun RemoteScreenDarkPreview() {
    com.example.omnicontrol.ui.theme.OmniControlTheme(darkTheme = true) {
        RemoteScreenContent(
            activeDevice = com.example.omnicontrol.data.model.Device(id = "1", name = "Living Room TV", ipAddress = "192.168.1.10", type = com.example.omnicontrol.data.model.DeviceType.SAMSUNG),
            shortcuts = listOf(
                com.example.omnicontrol.data.model.AppShortcut(name = "YouTube", appId = "y", colorHex = "#FF0000"),
                com.example.omnicontrol.data.model.AppShortcut(name = "Netflix", appId = "n", colorHex = "#E50914"),
                com.example.omnicontrol.data.model.AppShortcut(name = "Spotify", appId = "s", colorHex = "#1DB954")
            ),
            connectionState = com.example.omnicontrol.domain.ConnectionState.CONNECTED,
            hapticEnabled = true,
            onSwitchDevice = {},
            onOpenSettings = {},
            onSendCommand = {},
            onSendText = {},
            onLaunchApp = {},
            onReconnect = {}
        )
    }
}