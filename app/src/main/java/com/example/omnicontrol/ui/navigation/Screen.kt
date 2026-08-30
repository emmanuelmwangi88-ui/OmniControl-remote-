package com.example.omnicontrol.ui.navigation

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object DeviceList : Screen("device_list")
    object Remote : Screen("remote")
    object Settings : Screen("settings")
    object EditShortcuts : Screen("edit_shortcuts")
    object Apps : Screen("apps")
}
