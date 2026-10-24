package com.techteam.vpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VpnState {
    data object Disconnected : VpnState
    data class Connecting(val server: String) : VpnState
    data class Connected(val server: String, val since: Long) : VpnState
    data class Error(val message: String) : VpnState
}

/** Process-wide connection state shared by the service, the UI and the quick settings tile. */
object VpnStatus {
    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    fun set(state: VpnState) {
        _state.value = state
    }
}
