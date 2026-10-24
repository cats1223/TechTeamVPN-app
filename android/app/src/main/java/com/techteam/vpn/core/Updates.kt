package com.techteam.vpn.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub for a newer release of the app. */
object Updates {
    const val LATEST_URL = "https://api.github.com/repos/cats1223/TechTeamVPN-app/releases/latest"

    /** [downloadUrl] is the APK itself when the release has one, otherwise the release page. */
    data class Release(val version: String, val pageUrl: String, val downloadUrl: String)

    fun parse(json: String): Release {
        val o = JSONObject(json)
        val page = o.getString("html_url")
        val assets = o.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
        return Release(o.getString("tag_name").removePrefix("v"), page, apk ?: page)
    }

    /**
     * Compares dotted versions number by number: 1.10.0 is newer than 1.9.2. A beta
     * (1.2.0-beta.1) is older than its release, so beta testers are offered 1.2.0.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = numbers(latest)
        val b = numbers(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return isBeta(current) && !isBeta(latest)
    }

    fun isBeta(version: String): Boolean = '-' in version

    private fun numbers(version: String): List<Int> =
        version.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    /** Blocking; call from a background thread. */
    fun fetchLatest(): Release {
        val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != 200) throw IllegalStateException("GitHub: HTTP ${connection.responseCode}")
            return parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
