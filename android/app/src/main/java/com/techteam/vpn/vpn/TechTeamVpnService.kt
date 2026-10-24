package com.techteam.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.techteam.vpn.R
import com.techteam.vpn.core.VlessProfile
import com.techteam.vpn.core.XrayConfig
import com.techteam.vpn.core.XrayCore
import com.techteam.vpn.data.AppSettings
import com.techteam.vpn.data.ProfileRepository
import com.techteam.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TechTeamVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> scope.launch { disconnect() }
            // null action: the system restarted us for always-on VPN.
            else -> {
                startForegroundCompat()
                scope.launch { connect() }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        scope.launch { disconnect() }
    }

    override fun onDestroy() {
        stopIfRunning()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun connect() = lock.withLock {
        if (tun != null) return@withLock
        val profile = ProfileRepository.get(this).state.value.selected
        if (profile == null) {
            VpnStatus.set(VpnState.Error(getString(R.string.error_no_server)))
            stopSelfCompletely()
            return@withLock
        }
        VpnStatus.set(VpnState.Connecting(profile.name))
        try {
            val fd = establish(profile)
            tun = fd
            // The detailed Xray log is only kept in developer mode.
            val log = java.io.File(filesDir, LOG_FILE).apply { writeText("") }
            val logPath = if (AppSettings.get(this).devMode.value) log.path else null
            XrayCore.start(XrayConfig.build(profile, fd.fd, logPath), "${XrayConfig.DNS_SERVERS.first()}:53") { protect(it) }
            VpnStatus.set(VpnState.Connected(profile.name, System.currentTimeMillis()))
            updateNotification(getString(R.string.notification_connected, profile.name))
        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            stopCore()
            VpnStatus.set(VpnState.Error(e.message ?: e.javaClass.simpleName))
            stopSelfCompletely()
        }
    }

    private suspend fun disconnect() = lock.withLock {
        stopCore()
        VpnStatus.set(VpnState.Disconnected)
        stopSelfCompletely()
    }

    private fun establish(profile: VlessProfile): ParcelFileDescriptor {
        val builder = Builder()
            .setSession(profile.name)
            .setMtu(XrayConfig.TUN_MTU)
            .addAddress("172.19.0.1", 30)
            .addAddress("fdfe:dcba:9876::1", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .setBlocking(false)
            .setConfigureIntent(mainActivityIntent())
        XrayConfig.DNS_SERVERS.forEach { builder.addDnsServer(it) }
        // Our own sockets (Xray's outbound connections) must bypass the tunnel.
        builder.addDisallowedApplication(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        return builder.establish() ?: throw IllegalStateException(getString(R.string.error_vpn_permission))
    }

    private fun stopCore() {
        runCatching { XrayCore.stop() }.onFailure { Log.w(TAG, "stopXray", it) }
        runCatching { tun?.close() }
        tun = null
    }

    private fun stopIfRunning() {
        if (tun != null) {
            stopCore()
            VpnStatus.set(VpnState.Disconnected)
        }
    }

    private fun stopSelfCompletely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(getString(R.string.status_connecting)), type)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TechTeamVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(mainActivityIntent())
            .addAction(0, getString(R.string.action_disconnect), stop)
            .build()
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "TechTeamVPN"
        private const val CHANNEL_ID = "vpn"
        private const val NOTIFICATION_ID = 1
        const val LOG_FILE = "xray.log"
        const val ACTION_CONNECT = "com.techteam.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.techteam.vpn.DISCONNECT"

        /** The caller must have obtained consent through [VpnService.prepare] first. */
        fun connect(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, TechTeamVpnService::class.java).setAction(ACTION_CONNECT),
            )
        }

        fun disconnect(context: Context) {
            context.startService(Intent(context, TechTeamVpnService::class.java).setAction(ACTION_DISCONNECT))
        }
    }
}
