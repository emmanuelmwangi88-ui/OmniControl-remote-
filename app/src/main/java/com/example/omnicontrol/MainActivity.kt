package com.example.omnicontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.omnicontrol.ui.navigation.Screen
import com.example.omnicontrol.ui.remote.DiscoveryViewModel
import com.example.omnicontrol.ui.remote.RemoteViewModel
import com.example.omnicontrol.ui.screens.DeviceListScreen
import com.example.omnicontrol.ui.screens.EditShortcutsScreen
import com.example.omnicontrol.ui.screens.RemoteScreen
import com.example.omnicontrol.ui.screens.SettingsScreen
import com.example.omnicontrol.ui.screens.SetupScreen
import com.example.omnicontrol.ui.theme.OmniControlTheme
import com.example.omnicontrol.ui.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Hide system bars for immersive experience
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
            
            OmniControlTheme(darkTheme = isDarkMode) {
                MainApp()
            }
        }
    }
}

data class NavigationItem(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
fun MainApp(viewModel: DiscoveryViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val pairedDevices by viewModel.pairedDevices.collectAsState(initial = emptyList())

    var hasNavigatedInitial by remember { mutableStateOf(false) }

    LaunchedEffect(pairedDevices) {
        if (!hasNavigatedInitial && pairedDevices.isNotEmpty()) {
            navController.navigate(Screen.Remote.route) {
                popUpTo(Screen.Setup.route) { inclusive = true }
            }
            hasNavigatedInitial = true
        }
    }

    Scaffold(
        bottomBar = {
            val showBottomBar = currentDestination?.route in listOf(
                Screen.Remote.route,
                Screen.DeviceList.route,
                Screen.Apps.route
            )
            
            if (showBottomBar) {
                NavigationBar(
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .height(64.dp), // Compact height
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    val items = listOf(
                        NavigationItem(Screen.Remote, "Remote", Icons.Default.SettingsRemote),
                        NavigationItem(Screen.DeviceList, "Devices", Icons.Default.Devices)
                    )

                    items.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp)) },
                            label = { Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                                selectedTextColor = MaterialTheme.colorScheme.onSecondary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.4f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.4f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Setup.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Setup.route) {
                SetupScreen(
                    onDevicePaired = {
                        navController.navigate(Screen.Remote.route) {
                            popUpTo(Screen.Setup.route) { inclusive = true }
                        }
                    },
                    onBack = { 
                        if (pairedDevices.isNotEmpty()) navController.popBackStack()
                    }
                )
            }
            composable(Screen.DeviceList.route) {
                val remoteViewModel: RemoteViewModel = hiltViewModel()
                DeviceListScreen(
                    onDeviceSelected = { device ->
                        remoteViewModel.selectDevice(device)
                        navController.navigate(Screen.Remote.route)
                    },
                    onAddDevice = {
                        navController.navigate(Screen.Setup.route)
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }
            composable(Screen.Remote.route) {
                RemoteScreen(
                    onEditShortcuts = { navController.navigate(Screen.EditShortcuts.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Apps.route) {
                EditShortcutsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.EditShortcuts.route) {
                EditShortcutsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
