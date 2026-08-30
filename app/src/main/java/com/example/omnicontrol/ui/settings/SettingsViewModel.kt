package com.example.omnicontrol.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omnicontrol.data.local.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val isDarkMode: StateFlow<Boolean> = settingsManager.darkTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val largeButtonMode: StateFlow<Boolean> = settingsManager.largeButtons.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val hapticFeedback: StateFlow<Boolean> = settingsManager.hapticsEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setDarkTheme(enabled) }
    }

    fun toggleLargeButtons(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setLargeButtons(enabled) }
    }

    fun toggleHaptics(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setHapticsEnabled(enabled) }
    }
}
