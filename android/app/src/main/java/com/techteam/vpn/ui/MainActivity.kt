package com.techteam.vpn.ui

import android.Manifest
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
        if (savedInstanceState == null) intent?.dataString?.let(vm::addInput)

        setContent {
            TechTeamTheme {
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
        intent.dataString?.let(vm::addInput)
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
