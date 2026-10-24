package com.techteam.vpn.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/** Theme and developer mode; the VPN service reads devMode to decide whether to keep the Xray log. */
class AppSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _lightTheme = MutableStateFlow(prefs.getBoolean("lightTheme", false))
    val lightTheme: StateFlow<Boolean> = _lightTheme.asStateFlow()

    private val _devMode = MutableStateFlow(prefs.getBoolean("devMode", false))
    val devMode: StateFlow<Boolean> = _devMode.asStateFlow()

    fun setLightTheme(light: Boolean) {
        _lightTheme.value = light
        prefs.edit().putBoolean("lightTheme", light).apply()
    }

    /** Returns false for a wrong password. */
    fun unlockDevMode(password: String): Boolean {
        if (sha256(password) != DEV_PASSWORD_HASH) return false
        setDevMode(true)
        return true
    }

    fun setDevMode(enabled: Boolean) {
        _devMode.value = enabled
        prefs.edit().putBoolean("devMode", enabled).apply()
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** SHA-256 of the developer password, so the password itself is not in the source. */
        private const val DEV_PASSWORD_HASH = "ebf975deece2e0b3b0f84f343e7b235ec1bcc459910dfb6ebace4ec35a56df6d"

        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
