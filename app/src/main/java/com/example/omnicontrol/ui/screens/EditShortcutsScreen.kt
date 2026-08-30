package com.example.omnicontrol.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.omnicontrol.data.model.AppShortcut
import com.example.omnicontrol.ui.remote.RemoteViewModel
import com.example.omnicontrol.util.HapticUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShortcutsScreen(
    onBack: () -> Unit,
    viewModel: RemoteViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val shortcuts by viewModel.shortcuts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("App Shortcuts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF080808)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "Manage your quick access apps",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(shortcuts) { shortcut ->
                    EditableShortcutItem(
                        shortcut = shortcut,
                        onDelete = { 
                            HapticUtil.vibrate(context)
                            viewModel.deleteShortcut(shortcut) 
                        }
                    )
                }
                item {
                    AddShortcutPlaceholder(onClick = { 
                        HapticUtil.vibrate(context)
                        showAddDialog = true 
                    })
                }
            }
        }
    }

    if (showAddDialog) {
        AddShortcutDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, appId, color ->
                viewModel.addShortcut(AppShortcut(name = name, appId = appId, colorHex = color))
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddShortcutPlaceholder(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = Color.White.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Add New", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EditableShortcutItem(shortcut: AppShortcut, onDelete: () -> Unit) {
    val backgroundColor = remember(shortcut.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(shortcut.colorHex))
        } catch (e: Exception) {
            Color(0xFF333333)
        }
    }

    Box(contentAlignment = Alignment.TopEnd) {
        Surface(
            modifier = Modifier
                .aspectRatio(0.8f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = backgroundColor.copy(alpha = 0.8f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(shortcut.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(shortcut.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(28.dp)
                .offset(x = 6.dp, y = (-6).dp)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = 0.9f))
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun AddShortcutDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#FF0266") }

    val colors = listOf("#FF0266", "#00D4FF", "#03DAC5", "#FFB300", "#9C27B0", "#E91E63", "#4CAF50")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        title = { Text("App Shortcut", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray,
                        unfocusedLabelColor = Color.Gray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = appId, 
                    onValueChange = { appId = it }, 
                    label = { Text("App ID / Action") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray,
                        unfocusedLabelColor = Color.Gray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Column {
                    Text("Theme Color", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        colors.forEach { colorHex ->
                            val color = Color(android.graphics.Color.parseColor(colorHex))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (selectedColor == colorHex) 2.dp else 0.dp,
                                        Color.White,
                                        CircleShape
                                    )
                                    .clickable { selectedColor = colorHex }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if(name.isNotBlank()) onConfirm(name, appId, selectedColor) }) { Text("ADD") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.Gray) }
        }
    )
}
