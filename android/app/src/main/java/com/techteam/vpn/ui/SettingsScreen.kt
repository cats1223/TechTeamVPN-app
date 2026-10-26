package com.techteam.vpn.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Slider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techteam.vpn.BuildConfig
import com.techteam.vpn.R
import com.techteam.vpn.core.ServerProfile
import com.techteam.vpn.core.TrafficHistory
import com.techteam.vpn.vpn.VpnState
import com.techteam.vpn.vpn.VpnStatus
import com.techteam.vpn.vpn.TechTeamVpnService
import java.io.File

@Composable
fun SettingsScreen(
    lightTheme: Boolean,
    devMode: Boolean,
    hwid: String,
    selected: ServerProfile?,
    russiaDirect: Boolean,
    blockAds: Boolean,
    dns: String,
    customDns: String,
    traffic: TrafficHistory,
    bypassApps: Set<String>,
    betaUpdates: Boolean,
    directSites: List<String>,
    autoFastest: Boolean,
    onAutoFastest: (Boolean) -> Unit,
    proxySites: List<String>,
    onTheme: (Boolean) -> Unit,
    onRussiaDirect: (Boolean) -> Unit,
    onBlockAds: (Boolean) -> Unit,
    onDns: (String, String) -> Unit,
    onLanProxy: (Boolean) -> Unit,
    onBypassApps: (Set<String>) -> Unit,
    onCheckUpdate: () -> Unit,
    onBetaUpdates: (Boolean) -> Unit,
    onDirectSites: (List<String>) -> Unit,
    onProxySites: (List<String>) -> Unit,
    onUnlock: (String) -> Boolean,
    onDisableDevMode: () -> Unit,
) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var askPassword by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var showDns by remember { mutableStateOf(false) }
    var showKillSwitch by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showRouter by remember { mutableStateOf(false) }
    var editSites by remember { mutableStateOf<Boolean?>(null) } // true: direct list, false: VPN list

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.tab_settings), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Text,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
        )

        SectionTitle(stringResource(R.string.appearance))
        Card {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.SurfaceHigh)) {
                ThemeOption(stringResource(R.string.theme_dark), selected = !lightTheme, Modifier.weight(1f)) { onTheme(false) }
                ThemeOption(stringResource(R.string.theme_light), selected = lightTheme, Modifier.weight(1f)) { onTheme(true) }
            }
        }
        val appSettings = com.techteam.vpn.data.AppSettings.get(context)
        val sounds by appSettings.sounds.collectAsState()
        val accent by appSettings.accent.collectAsState()
        var pickAccent by remember { mutableStateOf(false) }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.accent), color = Brand.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (hex in ACCENT_PRESETS) {
                        AccentDot(accentColor(hex) ?: Brand.DefaultCyan, selected = accent == hex) { appSettings.setAccent(hex) }
                    }
                    // A custom color: a rainbow dot, or the color itself once picked.
                    val custom = accent !in ACCENT_PRESETS
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(if (custom) Brush.linearGradient(listOf(Brand.Cyan, Brand.Cyan)) else Brush.sweepGradient(RAINBOW))
                            .border(if (custom) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { pickAccent = true },
                    )
                }
            }
        }
        var icon by remember { mutableStateOf(AppIcons.current(context)) }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.app_icon), color = Brand.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (option in AppIcons.all) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(option.preview), contentDescription = null,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                                    .border(if (icon == option.alias) 3.dp else 0.dp, Brand.Cyan, RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (icon != option.alias) {
                                            AppIcons.set(context, option.alias)
                                            icon = option.alias
                                            toast(context, context.getString(R.string.icon_changed))
                                        }
                                    },
                            )
                            Text(stringResource(option.name), color = Brand.TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (pickAccent) {
            AccentDialog(accentColor(accent) ?: Brand.DefaultCyan, onDismiss = { pickAccent = false }) {
                pickAccent = false
                appSettings.setAccent(it)
            }
        }
        Card(Modifier.clickable { appSettings.setSounds(!sounds) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sounds), color = Brand.Text, modifier = Modifier.weight(1f))
                Switch(checked = sounds, onCheckedChange = appSettings::setSounds, colors = SwitchDefaults.colors(checkedTrackColor = Brand.Cyan))
            }
        }

        SectionTitle(stringResource(R.string.split_title))
        Card(Modifier.clickable { onRussiaDirect(!russiaDirect) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.russia_direct), color = Brand.Text)
                    Text(stringResource(R.string.russia_direct_hint), color = Brand.TextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = russiaDirect, onCheckedChange = onRussiaDirect,
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.Cyan),
                )
            }
        }
        Card(Modifier.clickable { showApps = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.apps_title), color = Brand.Text)
                    Text(stringResource(R.string.apps_card_hint), color = Brand.TextMuted, fontSize = 12.sp)
                }
                Text(if (bypassApps.isEmpty()) stringResource(R.string.none) else bypassApps.size.toString(), color = Brand.Cyan)
            }
        }

        SectionTitle(stringResource(R.string.protection_title))
        Card(Modifier.clickable { onBlockAds(!blockAds) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.block_ads), color = Brand.Text)
                    Text(stringResource(R.string.block_ads_hint), color = Brand.TextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = blockAds, onCheckedChange = onBlockAds,
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.Cyan),
                )
            }
        }

        Card(Modifier.clickable { showKillSwitch = true }) {
            Column {
                Text(stringResource(R.string.kill_switch), color = Brand.Text)
                Text(stringResource(R.string.kill_switch_hint), color = Brand.TextMuted, fontSize = 12.sp)
            }
        }
        Card(Modifier.clickable { showDns = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DNS", color = Brand.Text, modifier = Modifier.weight(1f))
                Text(dnsTitle(dns), color = Brand.Cyan)
            }
        }

        SectionTitle(stringResource(R.string.share_title))
        val lanSettings = com.techteam.vpn.data.AppSettings.get(context)
        val lanProxy by lanSettings.lanProxy.collectAsState()
        Card(Modifier.clickable { onLanProxy(!lanProxy) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lan_proxy), color = Brand.Text)
                    Text(stringResource(R.string.lan_proxy_hint), color = Brand.TextMuted, fontSize = 12.sp)
                }
                Switch(checked = lanProxy, onCheckedChange = onLanProxy, colors = SwitchDefaults.colors(checkedTrackColor = Brand.Cyan))
            }
        }
        if (lanProxy) {
            val addresses = remember(lanProxy) { localAddresses() }
            Text(
                if (addresses.isEmpty()) stringResource(R.string.lan_proxy_no_network)
                else stringResource(R.string.lan_proxy_how, addresses.joinToString(", "), com.techteam.vpn.core.XrayConfig.LAN_HTTP_PORT),
                color = Brand.Text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Card(Modifier.clickable { showRouter = true }) {
            Column {
                Text(stringResource(R.string.router_title), color = Brand.Text)
                Text(stringResource(R.string.router_hint), color = Brand.TextMuted, fontSize = 12.sp)
            }
        }

        SectionTitle(stringResource(R.string.servers_title))
        Card(Modifier.clickable { onAutoFastest(!autoFastest) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_fastest), color = Brand.Text)
                    Text(stringResource(R.string.auto_fastest_hint), color = Brand.TextMuted, fontSize = 12.sp)
                }
                Switch(checked = autoFastest, onCheckedChange = onAutoFastest, colors = SwitchDefaults.colors(checkedTrackColor = Brand.Cyan))
            }
        }

        SectionTitle(stringResource(R.string.sites_title))
        for ((direct, list) in listOf(true to directSites, false to proxySites)) {
            Card(Modifier.clickable { editSites = direct }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (direct) R.string.sites_direct else R.string.sites_proxy),
                        color = Brand.Text, modifier = Modifier.weight(1f),
                    )
                    Text(if (list.isEmpty()) stringResource(R.string.none) else list.size.toString(), color = Brand.Cyan)
                }
            }
        }

        SectionTitle(stringResource(R.string.more_title))
        Card(Modifier.clickable { showStats = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stats_title), color = Brand.Text, modifier = Modifier.weight(1f))
                Text(formatBytes(traffic.total(1).let { it.first + it.second }), color = Brand.Cyan)
            }
        }
        Card(Modifier.clickable { showSpeed = true }) { Text(stringResource(R.string.speed_title), color = Brand.Text) }
        Card(Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL))) } }) {
            Text(stringResource(R.string.support), color = Brand.Cyan)
        }

        SectionTitle(stringResource(R.string.about))
        Card(
            Modifier.clickable {
                // Five taps on the version within a couple of seconds ask for the developer password.
                val now = System.currentTimeMillis()
                taps = if (now - lastTap < 1500) taps + 1 else 1
                lastTap = now
                if (taps >= 5) {
                    taps = 0
                    if (devMode) toast(context, context.getString(R.string.dev_already_on)) else askPassword = true
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.version), color = Brand.Text, modifier = Modifier.weight(1f))
                Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Brand.TextMuted)
            }
        }
        Card(Modifier.clickable(onClick = onCheckUpdate)) { Text(stringResource(R.string.check_updates), color = Brand.Cyan) }
        Card(Modifier.clickable { onBetaUpdates(!betaUpdates) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.beta_updates), color = Brand.Text)
                    Text(stringResource(R.string.beta_updates_hint), color = Brand.TextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = betaUpdates, onCheckedChange = onBetaUpdates,
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.Cyan),
                )
            }
        }

        if (devMode) {
            SectionTitle(stringResource(R.string.developer))
            Card(Modifier.clickable { showLog = true }) { Text(stringResource(R.string.connection_log), color = Brand.Cyan) }
            CopyCard(stringResource(R.string.device_id), hwid)
            selected?.let { CopyCard(stringResource(R.string.key_params), it.summary) }
            Card(Modifier.clickable(onClick = onDisableDevMode)) { Text(stringResource(R.string.dev_disable), color = Brand.Red) }
            Text(
                stringResource(R.string.dev_hint),
                color = Brand.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (askPassword) {
        PasswordDialog(
            onDismiss = { askPassword = false },
            onSubmit = { password ->
                askPassword = false
                toast(context, context.getString(if (onUnlock(password)) R.string.dev_enabled else R.string.wrong_password))
            },
        )
    }
    if (showLog) LogDialog(onDismiss = { showLog = false })
    if (showKillSwitch) KillSwitchDialog(onDismiss = { showKillSwitch = false })
    if (showStats) StatsDialog(traffic, onDismiss = { showStats = false })
    if (showRouter) {
        val p = selected
        if (p == null) {
            showRouter = false
            toast(context, context.getString(R.string.error_no_server))
        } else {
            RouterExportDialog(
                outbounds = com.techteam.vpn.core.XrayConfig.routerOutbounds(p),
                routing = com.techteam.vpn.core.XrayConfig.routerRouting(russiaDirect, blockAds, directSites, proxySites),
                onDismiss = { showRouter = false },
            )
        }
    }
    if (showSpeed) {
        val vpnState by VpnStatus.state.collectAsState()
        SpeedTestDialog(connected = vpnState is VpnState.Connected, onDismiss = { showSpeed = false })
    }
    if (showDns) {
        DnsDialog(
            choice = dns, custom = customDns, onDismiss = { showDns = false },
            onSave = { choice, custom ->
                showDns = false
                onDns(choice, custom)
            },
        )
    }
    editSites?.let { direct ->
        SitesDialog(
            title = stringResource(if (direct) R.string.sites_direct else R.string.sites_proxy),
            hint = stringResource(if (direct) R.string.sites_direct_hint else R.string.sites_proxy_hint),
            sites = if (direct) directSites else proxySites,
            onDismiss = { editSites = null },
            onSave = {
                editSites = null
                if (direct) onDirectSites(it) else onProxySites(it)
            },
        )
    }
    if (showApps) {
        AppsDialog(
            selected = bypassApps,
            onDismiss = { showApps = false },
            onSave = {
                showApps = false
                onBypassApps(it)
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Brand.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun ThemeOption(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(3.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) Brand.Surface else Brand.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = if (selected) Brand.Text else Brand.TextMuted, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CopyCard(title: String, value: String) {
    val context = LocalContext.current
    Card(
        Modifier.clickable {
            copy(context, value)
            toast(context, context.getString(R.string.copied, title))
        },
    ) {
        Column {
            Text(title, color = Brand.Text)
            Text(value, color = Brand.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 3)
        }
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.developer_mode)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }) { Text(stringResource(R.string.enable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The VPN service runs in this process and writes the log to filesDir, so it can be read directly. */
@Composable
private fun LogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(readLog(context)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Brand.Background).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.log), color = Brand.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { text = readLog(context) }) { Text(stringResource(R.string.refresh)) }
                TextButton(onClick = { copy(context, text); toast(context, context.getString(R.string.log_copied)) }) { Text(stringResource(R.string.copy)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
            Text(
                text, color = Brand.Text, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            )
        }
    }
}

private fun readLog(context: Context): String {
    val file = File(context.filesDir, TechTeamVpnService.LOG_FILE)
    val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
    return if (lines.isEmpty()) context.getString(R.string.log_empty) else lines.takeLast(400).joinToString("\n")
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("TechTeamVPN", text))
}

const val SUPPORT_URL = "https://t.me/TechTeamVPN_bot"

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private val RAINBOW = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

@Composable
private fun AccentDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
    )
}

/** A custom accent: hue and brightness sliders over a preview; [onPick] gets "RRGGBB". */
@Composable
private fun AccentDialog(initial: Color, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1].coerceAtLeast(0.3f)) }
    val color = Color.hsv(hue, saturation, 1f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accent)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(color))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Brush.horizontalGradient(RAINBOW)))
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..359f)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0.3f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick("%06X".format(color.toArgb() and 0xFFFFFF)) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
