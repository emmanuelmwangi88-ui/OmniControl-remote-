package com.example.omnicontrol.domain

sealed class ConnectionResult {
    object Success : ConnectionResult()
    data class Failure(val message: String) : ConnectionResult()
    object PairingRequired : ConnectionResult()
}
