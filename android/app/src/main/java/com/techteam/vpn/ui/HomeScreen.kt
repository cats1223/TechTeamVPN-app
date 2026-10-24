package com.techteam.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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

@Composable
fun HomeScreen(
    state: ProfilesState,
    vpn: VpnState,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onOpenServers: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.app_name),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Brand.Text,
        )
        Text(stringResource(R.string.tagline), color = Brand.TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(40.dp))

        PowerButton(vpn, onToggle)

        Spacer(Modifier.height(28.dp))
        StatusText(vpn)
        Spacer(Modifier.height(36.dp))

        val selected = state.selected
        if (selected == null) {
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Cyan, contentColor = Brand.Background),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_key), fontWeight = FontWeight.SemiBold)
            }
        } else {
            Card(Modifier.clickable(onClick = onOpenServers)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Brand.SurfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Public, null, tint = Brand.Cyan) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.current_server), color = Brand.TextMuted, fontSize = 12.sp)
                        Text(
                            selected.name, color = Brand.Text, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Brand.TextMuted)
                }
            }
        }

        state.subscriptionInfo?.let {
            Spacer(Modifier.height(12.dp))
            SubscriptionCard(it)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PowerButton(vpn: VpnState, onToggle: () -> Unit) {
    val connected = vpn is VpnState.Connected
    val connecting = vpn is VpnState.Connecting
    val ring by animateColorAsState(
        when {
            connected -> Brand.Green
            connecting -> Brand.Amber
            else -> Brand.SurfaceHigh
        },
        label = "ring",
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (connecting) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )

    Box(
        Modifier.size(210.dp).scale(scale).clip(CircleShape)
            .background(Brush.radialGradient(listOf(ring.copy(alpha = 0.25f), Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(160.dp).clip(CircleShape)
                .background(if (connected) Brand.Gradient else Brush.linearGradient(listOf(Brand.Surface, Brand.SurfaceHigh)))
                .border(3.dp, ring, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = stringResource(if (connected) R.string.action_disconnect else R.string.action_connect),
                tint = if (connected) Color.White else Brand.Cyan,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun StatusText(vpn: VpnState) {
    val (title, color) = when (vpn) {
        is VpnState.Connected -> stringResource(R.string.status_connected) to Brand.Green
        is VpnState.Connecting -> stringResource(R.string.status_connecting) to Brand.Amber
        is VpnState.Error -> stringResource(R.string.status_error) to Brand.Red
        VpnState.Disconnected -> stringResource(R.string.status_disconnected) to Brand.TextMuted
    }
    Text(title, color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    when (vpn) {
        is VpnState.Connected -> {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(vpn.since) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(1000)
                }
            }
            Text(formatDuration((now - vpn.since) / 1000), color = Brand.Text, fontSize = 16.sp)
        }
        is VpnState.Error -> Text(vpn.message, color = Brand.TextMuted, fontSize = 13.sp, maxLines = 3)
        else -> Text(stringResource(R.string.status_hint), color = Brand.TextMuted, fontSize = 13.sp)
    }
}

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
