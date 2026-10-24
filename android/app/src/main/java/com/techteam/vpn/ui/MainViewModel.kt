package com.techteam.vpn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.techteam.vpn.BuildConfig
import com.techteam.vpn.R
import com.techteam.vpn.core.DeepLink
import com.techteam.vpn.core.Updates
import com.techteam.vpn.data.ProfileRepository
import com.techteam.vpn.data.ProfilesState
import com.techteam.vpn.vpn.TechTeamVpnService
import com.techteam.vpn.vpn.VpnState
import com.techteam.vpn.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProfileRepository.get(app)

    val profiles: StateFlow<ProfilesState> = repo.state
    val vpn: StateFlow<VpnState> = VpnStatus.state

    private val _pings = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Profile id to TCP connect time in ms; -1 means unreachable. */
    val pings: StateFlow<Map<String, Long>> = _pings.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _update = MutableStateFlow<Updates.Release?>(null)
    /** A newer release to offer; null hides the dialog. */
    val update: StateFlow<Updates.Release?> = _update.asStateFlow()

    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    init {
        // Pick up server list changes made in the panel since the last launch.
        if (repo.state.value.subscriptionUrl.isNotEmpty()) refreshSubscription(silent = true)
        checkForUpdate(manual = false)
    }

    /**
     * The automatic check runs at most every 12 hours and stays quiet about a version the user
     * already put off; the button in settings always asks GitHub and reports the result.
     */
    fun checkForUpdate(manual: Boolean) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("updateCheckedAt", 0) < 12 * 3600_000L) return@launch
        val release = try {
            withContext(Dispatchers.IO) { Updates.fetchLatest() }
        } catch (e: Exception) {
            if (manual) toast(str(R.string.update_error, e.message ?: ""))
            return@launch
        }
        prefs.edit().putLong("updateCheckedAt", now).apply()
        when {
            !Updates.isNewer(release.version, BuildConfig.VERSION_NAME) -> if (manual) toast(str(R.string.update_none))
            manual || prefs.getString("updateDismissed", null) != release.version -> _update.value = release
        }
    }

    /** "Later": the automatic check does not offer this version again. */
    fun dismissUpdate() {
        _update.value?.let { prefs.edit().putString("updateDismissed", it.version).apply() }
        _update.value = null
    }

    fun connect() = TechTeamVpnService.connect(getApplication())

    fun disconnect() = TechTeamVpnService.disconnect(getApplication())

    fun select(id: String) {
        repo.select(id)
        reconnectIfActive()
    }

    /** Restarts a running VPN so a new server or split tunneling setting takes effect. */
    fun reconnectIfActive() {
        if (vpn.value !is VpnState.Connected && vpn.value !is VpnState.Connecting) return
        viewModelScope.launch {
            disconnect()
            VpnStatus.state.first { it is VpnState.Disconnected || it is VpnState.Error }
            connect()
        }
    }

    fun remove(id: String) = repo.remove(id)

    fun addInput(text: String) {
        val input = DeepLink.unwrap(text)
        when {
            input.startsWith("http://", true) || input.startsWith("https://", true) -> setSubscription(input)
            else -> {
                val added = repo.addLinks(input)
                toast(
                    if (added > 0) str(R.string.msg_added, added) else str(R.string.msg_invalid_key),
                )
            }
        }
    }

    fun setSubscription(url: String) = runBusy {
        repo.setSubscription(url)
        toast(str(R.string.msg_subscription_updated))
    }

    fun refreshSubscription(silent: Boolean = false) = runBusy(silent) {
        repo.refreshSubscription()
        if (!silent) toast(str(R.string.msg_subscription_updated))
    }

    fun removeSubscription() = repo.removeSubscription()

    fun pingAll() = viewModelScope.launch {
        val list = repo.state.value.profiles.map { it.profile }
        val results = withContext(Dispatchers.IO) {
            list.map { p -> async { p.id to tcpPing(p.address, p.port) } }.awaitAll()
        }
        _pings.value = results.toMap()
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun tcpPing(host: String, port: Int): Long = try {
        Socket().use { s ->
            val start = System.nanoTime()
            s.connect(InetSocketAddress(host, port), 3000)
            (System.nanoTime() - start) / 1_000_000
        }
    } catch (_: Exception) {
        -1
    }

    private fun runBusy(silent: Boolean = false, block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
        } catch (e: Exception) {
            if (!silent) toast(str(R.string.msg_subscription_error, e.message ?: ""))
        } finally {
            _busy.value = false
        }
    }

    private fun toast(text: String) {
        _message.value = text
    }

    private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)
}
