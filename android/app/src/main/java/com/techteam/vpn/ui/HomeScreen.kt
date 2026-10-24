package com.techteam.vpn.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import com.techteam.vpn.core.GeoIP
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techteam.vpn.R
import com.techteam.vpn.core.SubscriptionInfo
import com.techteam.vpn.data.ProfilesState
import com.techteam.vpn.vpn.VpnState
import kotlinx.coroutines.delay

/**
 * The globe fills the screen and shows where sites see you: your own location before the VPN,
 * the server's after. A panel at the bottom holds the status, the button and the servers, and
 * pulls up for the subscription, all servers and settings.
 */
@Composable
fun HomeScreen(
    state: ProfilesState,
    vpn: VpnState,
    homeLocation: GeoIP.Location?,
    visibleLocation: GeoIP.Location?,
    serverLocations: Map<String, GeoIP.Location>,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val connected = vpn is VpnState.Connected
    val server = state.selected?.let { serverLocations[it.address] }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val pins = buildList {
        homeLocation?.let { add(GlobePin(it.latitude, it.longitude, "${it.flag} " + if (connected) "Реальный адрес" else "Вы здесь", false)) }
        server?.let {
            add(GlobePin(it.latitude, it.longitude, "${it.flag} " + if (connected) "Вы сейчас здесь" else state.selected?.name.orEmpty(), connected))
        }
    }
    val focus = if (connected) server ?: homeLocation else homeLocation ?: server

    Box(Modifier.fillMaxSize()) {
        Globe(
            centerLat = focus?.latitude ?: 55.75,
            centerLon = focus?.longitude ?: 37.62,
            pins = pins,
            line = if (connected && pins.size == 2) pins[0] to pins[1] else null,
            modifier = Modifier.fillMaxSize(),
        )

        StatusPill(vpn, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Brand.Surface.copy(alpha = 0.88f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -12) expanded = true
                        if (dragAmount > 12) expanded = false
                    }
                }
                .animateContentSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(40.dp, 5.dp).clip(CircleShape).background(Brand.TextMuted.copy(alpha = 0.6f)))
            }

            StatusCard(state, vpn, homeLocation, visibleLocation, onToggle, onAdd)

            if (state.profiles.isNotEmpty()) {
                Text("Серверы", color = Brand.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.profiles.forEach { item ->
                        val p = item.profile
                        val selected = p.id == state.selected?.id
                        Row(
                            Modifier
                                .height(46.dp)
                                .clip(CircleShape)
                                .background(if (selected) Brand.Cyan.copy(alpha = 0.22f) else Brand.SurfaceHigh)
                                .border(1.dp, if (selected) Brand.Cyan else Color.Transparent, CircleShape)
                                .clickable { onSelect(p.id) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(leadingFlag(p.name) ?: serverLocations[p.address]?.flag ?: "\uD83C\uDF10", fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stripFlag(p.name), color = if (selected) Brand.Cyan else Brand.Text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (expanded) {
                state.subscriptionInfo?.let { SubscriptionCard(it) }
                Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Brand.SurfaceHigh)) {
                    MenuRow(Icons.Rounded.Dns, "Все серверы", onOpenServers)
                    MenuRow(Icons.Rounded.Add, stringResource(R.string.add_key), onAdd)
                    MenuRow(Icons.Rounded.Settings, "Настройки", onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(vpn: VpnState, modifier: Modifier) {
    val (text, color) = when (vpn) {
        is VpnState.Connected -> stringResource(R.string.status_connected) to Brand.Green
        is VpnState.Connecting -> stringResource(R.string.status_connecting) to Brand.Amber
        is VpnState.Error -> stringResource(R.string.status_error) to Brand.Red
        VpnState.Disconnected -> stringResource(R.string.status_disconnected) to Brand.Red
    }
    Row(
        modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusCard(
    state: ProfilesState,
    vpn: VpnState,
    homeLocation: GeoIP.Location?,
    visibleLocation: GeoIP.Location?,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
) {
    val connected = vpn is VpnState.Connected
    val connecting = vpn is VpnState.Connecting
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brand.SurfaceHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    connected -> Icons.Rounded.VerifiedUser
                    connecting -> Icons.Rounded.Shield
                    else -> Icons.Rounded.WarningAmber
                },
                null,
                tint = when {
                    connected -> Brand.Green
                    connecting -> Brand.Amber
                    else -> Brand.TextMuted
                },
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    when {
                        connected -> "Вы защищены"
                        connecting -> stringResource(R.string.status_connecting)
                        else -> "Вы не защищены"
                    },
                    color = Brand.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                )
                val subtitle = when {
                    vpn is VpnState.Error -> vpn.message
                    connected -> {
                        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(vpn) {
                            while (true) {
                                now = System.currentTimeMillis()
                                delay(1000)
                            }
                        }
                        locationLine(visibleLocation, "Сайты видят") + " · " + formatDuration((now - (vpn as VpnState.Connected).since) / 1000)
                    }
                    homeLocation != null -> locationLine(homeLocation, "Ваш адрес")
                    else -> "Подключитесь к VPN для защиты"
                }
                Text(subtitle, color = Brand.TextMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        val (label, color, action) = when {
            state.selected == null -> Triple(stringResource(R.string.add_key), Brand.Cyan, onAdd)
            connected -> Triple(stringResource(R.string.action_disconnect), Brand.Red, onToggle)
            connecting -> Triple(stringResource(R.string.cancel), Brand.Red, onToggle)
            else -> Triple("Подключиться", Brand.Cyan, onToggle)
        }
        Button(
            onClick = action,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Brand.Text)
        Spacer(Modifier.width(14.dp))
        Text(title, color = Brand.Text, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = Brand.TextMuted)
    }
}

private fun locationLine(location: GeoIP.Location?, prefix: String): String =
    if (location == null) "$prefix: определяю…" else "$prefix: ${location.flag} ${location.place}"

/** A flag emoji at the start of a server name, like "🇳🇱 Нидерланды". */
private fun leadingFlag(name: String): String? {
    if (name.length < 4) return null
    val a = name.codePointAt(0)
    val b = name.codePointAt(Character.charCount(a))
    return if (a in 0x1F1E6..0x1F1FF && b in 0x1F1E6..0x1F1FF) name.substring(0, 4) else null
}

private fun stripFlag(name: String): String = if (leadingFlag(name) != null) name.substring(4).trim() else name

@Composable
private fun SubscriptionCard(info: SubscriptionInfo) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                info.title.ifEmpty { stringResource(R.string.subscription) },
                color = Brand.Text, fontWeight = FontWeight.SemiBold,
            )
            val used = info.upload + info.download
            if (info.total > 0) {
                LinearProgressIndicator(
                    progress = { (used.toFloat() / info.total).coerceIn(0f, 1f) },
                    color = Brand.Cyan,
                    trackColor = Brand.SurfaceHigh,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(
                    stringResource(R.string.traffic_of, formatBytes(used), formatBytes(info.total)),
                    color = Brand.TextMuted, fontSize = 13.sp,
                )
            } else {
                Text(stringResource(R.string.traffic_used, formatBytes(used)), color = Brand.TextMuted, fontSize = 13.sp)
            }
            if (info.expire > 0) {
                val days = ((info.expire * 1000 - System.currentTimeMillis()) / 86_400_000).coerceAtLeast(0)
                Text(
                    stringResource(R.string.expires_in_days, days),
                    color = if (days <= 3) Brand.Amber else Brand.TextMuted, fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).then(modifier).background(Brand.Surface).padding(16.dp),
    ) { content() }
}

fun formatDuration(seconds: Long): String =
    "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)

fun formatBytes(bytes: Long): String {
    val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (i == 0) "$bytes ${units[0]}" else "%.1f %s".format(value, units[i])
}
