package com.techteam.vpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techteam.vpn.BuildConfig
import com.techteam.vpn.core.ServerProfile
import com.techteam.vpn.vpn.TechTeamVpnService
import java.io.File

@Composable
fun SettingsScreen(
    lightTheme: Boolean,
    devMode: Boolean,
    hwid: String,
    selected: ServerProfile?,
    russiaDirect: Boolean,
    bypassApps: Set<String>,
    betaUpdates: Boolean,
    onTheme: (Boolean) -> Unit,
    onRussiaDirect: (Boolean) -> Unit,
    onBypassApps: (Set<String>) -> Unit,
    onCheckUpdate: () -> Unit,
    onBetaUpdates: (Boolean) -> Unit,
    onUnlock: (String) -> Boolean,
    onDisableDevMode: () -> Unit,
) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var askPassword by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Настройки", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Text,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
        )

        SectionTitle("Оформление")
        Card {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.SurfaceHigh)) {
                ThemeOption("Чёрная", selected = !lightTheme, Modifier.weight(1f)) { onTheme(false) }
                ThemeOption("Белая", selected = lightTheme, Modifier.weight(1f)) { onTheme(true) }
            }
        }

        SectionTitle("Раздельное туннелирование")
        Card(Modifier.clickable { onRussiaDirect(!russiaDirect) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Российские сайты напрямую", color = Brand.Text)
                    Text("Зоны .ru, .su, .рф и российские адреса — без VPN", color = Brand.TextMuted, fontSize = 12.sp)
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
                    Text("Приложения без VPN", color = Brand.Text)
                    Text("Банки, Госуслуги и другие не увидят VPN", color = Brand.TextMuted, fontSize = 12.sp)
                }
                Text(if (bypassApps.isEmpty()) "Нет" else bypassApps.size.toString(), color = Brand.Cyan)
            }
        }

        SectionTitle("О приложении")
        Card(
            Modifier.clickable {
                // Five taps on the version within a couple of seconds ask for the developer password.
                val now = System.currentTimeMillis()
                taps = if (now - lastTap < 1500) taps + 1 else 1
                lastTap = now
                if (taps >= 5) {
                    taps = 0
                    if (devMode) toast(context, "Режим разработчика уже включён") else askPassword = true
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Версия", color = Brand.Text, modifier = Modifier.weight(1f))
                Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Brand.TextMuted)
            }
        }
        Card(Modifier.clickable(onClick = onCheckUpdate)) { Text("Проверить обновления", color = Brand.Cyan) }
        Card(Modifier.clickable { onBetaUpdates(!betaUpdates) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Получать бета-версии", color = Brand.Text)
                    Text("Предлагать обновиться и на бету, а не только на релиз", color = Brand.TextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = betaUpdates, onCheckedChange = onBetaUpdates,
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.Cyan),
                )
            }
        }

        if (devMode) {
            SectionTitle("Разработчик")
            Card(Modifier.clickable { showLog = true }) { Text("Журнал подключения", color = Brand.Cyan) }
            CopyCard("ID устройства", hwid)
            selected?.let { CopyCard("Параметры ключа", it.summary) }
            Card(Modifier.clickable(onClick = onDisableDevMode)) { Text("Выключить режим разработчика", color = Brand.Red) }
            Text(
                "В режиме разработчика VPN ведёт подробный журнал Xray. Переподключитесь, чтобы он начал записываться.",
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
                toast(context, if (onUnlock(password)) "Режим разработчика включён" else "Неверный пароль")
            },
        )
    }
    if (showLog) LogDialog(onDismiss = { showLog = false })
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
            toast(context, "$title: скопировано")
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
        title = { Text("Режим разработчика") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }) { Text("Включить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
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
                Text("Журнал", color = Brand.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { text = readLog(context) }) { Text("Обновить") }
                TextButton(onClick = { copy(context, text); toast(context, "Журнал скопирован") }) { Text("Копировать") }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
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
    return if (lines.isEmpty()) "Журнал пуст. Включите VPN в режиме разработчика и откройте сайт." else lines.takeLast(400).joinToString("\n")
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("TechTeamVPN", text))
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
