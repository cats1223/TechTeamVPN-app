package com.techteam.vpn.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.techteam.vpn.R
import com.techteam.vpn.core.DeepLink
import com.techteam.vpn.data.AppSettings
import com.techteam.vpn.data.ProfileRepository
import com.techteam.vpn.vpn.VpnState

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) vm.connect()
        else Toast.makeText(this, R.string.error_vpn_permission, Toast.LENGTH_LONG).show()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(vm::addInput)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Links shared from a browser or messenger land here.
        if (savedInstanceState == null) intent?.dataString?.let(::handleLink)

        val settings = AppSettings.get(this)
        setContent {
            val lightTheme by settings.lightTheme.collectAsStateWithLifecycle()
            val devMode by settings.devMode.collectAsStateWithLifecycle()
            TechTeamTheme(light = lightTheme) {
                val profiles by vm.profiles.collectAsStateWithLifecycle()
                val vpn by vm.vpn.collectAsStateWithLifecycle()
                val pings by vm.pings.collectAsStateWithLifecycle()
                val busy by vm.busy.collectAsStateWithLifecycle()
                val message by vm.message.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var showAdd by remember { mutableStateOf(false) }

                LaunchedEffect(message) {
                    message?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                        vm.consumeMessage()
                    }
                }

                Scaffold(
                    containerColor = Brand.Background,
                    bottomBar = {
                        NavigationBar(containerColor = Brand.Surface) {
                            val colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Brand.Cyan,
                                selectedTextColor = Brand.Cyan,
                                indicatorColor = Brand.SurfaceHigh,
                            )
                            NavigationBarItem(
                                selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                                icon = { Icon(Icons.Rounded.Shield, null) },
                                label = { Text(stringResource(R.string.tab_home)) },
                            )
                            NavigationBarItem(
                                selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                                icon = { Icon(Icons.Rounded.Dns, null) },
                                label = { Text(stringResource(R.string.tab_servers)) },
                            )
                            NavigationBarItem(
                                selected = tab == 2, onClick = { tab = 2 }, colors = colors,
                                icon = { Icon(Icons.Rounded.Settings, null) },
                                label = { Text(stringResource(R.string.tab_settings)) },
                            )
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().background(Brand.Background).padding(padding)) {
                        when (tab) {
                            0 -> HomeScreen(
                                state = profiles,
                                vpn = vpn,
                                onToggle = ::toggle,
                                onAdd = { showAdd = true },
                                onOpenServers = { tab = 1 },
                            )
                            2 -> SettingsScreen(
                                lightTheme = lightTheme,
                                devMode = devMode,
                                hwid = ProfileRepository.get(this@MainActivity).hwid,
                                selected = profiles.selected,
                                onTheme = settings::setLightTheme,
                                onUnlock = settings::unlockDevMode,
                                onDisableDevMode = { settings.setDevMode(false) },
                            )
                            else -> ServersScreen(
                                state = profiles,
                                pings = pings,
                                busy = busy,
                                onSelect = vm::select,
                                onRemove = vm::remove,
                                onPing = vm::pingAll,
                                onRefresh = { vm.refreshSubscription() },
                                onRemoveSubscription = vm::removeSubscription,
                                onAdd = { showAdd = true },
                            )
                        }
                    }
                }

                if (showAdd) {
                    AddSheet(
                        busy = busy,
                        onDismiss = { showAdd = false },
                        onSubmit = { text ->
                            vm.addInput(text)
                            showAdd = false
                        },
                        onScan = {
                            showAdd = false
                            qrScanner.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt(getString(R.string.scan_prompt))
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false),
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let(::handleLink)
    }

    /** Set by techteamvpn://add without a link: the subscription is in the clipboard. */
    private var importFromClipboard = false

    private fun handleLink(link: String) {
        if (DeepLink.wantsClipboard(link)) {
            importFromClipboard = true
            if (hasWindowFocus()) onWindowFocusChanged(true)
        } else {
            vm.addInput(link)
        }
    }

    // Android 10+ only lets the focused app read the clipboard, so wait for focus.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !importFromClipboard) return
        importFromClipboard = false
        val clip = getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (clip.isNullOrBlank()) {
            Toast.makeText(this, R.string.msg_clipboard_empty, Toast.LENGTH_LONG).show()
        } else {
            vm.addInput(clip)
        }
    }

    private fun toggle() {
        when (vm.vpn.value) {
            is VpnState.Connected, is VpnState.Connecting -> vm.disconnect()
            else -> {
                if (vm.profiles.value.selected == null) {
                    Toast.makeText(this, R.string.error_no_server, Toast.LENGTH_SHORT).show()
                    return
                }
                val consent = VpnService.prepare(this)
                if (consent != null) vpnPermission.launch(consent) else vm.connect()
            }
        }
    }
}
