package com.techteam.vpn.core

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** Traffic and expiry the panel reports in the `subscription-userinfo` header. */
data class SubscriptionInfo(
    val title: String = "",
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    /** Unix seconds; 0 means no expiry. */
    val expire: Long = 0,
    val updatedAt: Long = 0,
)

data class SubscriptionResult(val profiles: List<ServerProfile>, val info: SubscriptionInfo)

object Subscription {
    const val USER_AGENT = "TechTeamVPN/1.0"

    /**
     * Blocking; call off the main thread.
     * @param headers device headers (`x-hwid` and friends) for panels that bind keys to devices.
     */
    fun fetch(url: String, headers: Map<String, String> = emptyMap()): SubscriptionResult {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val info = parseUserInfo(conn.getHeaderField("subscription-userinfo"))
                .copy(
                    title = decodeTitle(conn.getHeaderField("profile-title")),
                    updatedAt = System.currentTimeMillis() / 1000,
                )
            val profiles = parseLinks(body)
            if (profiles.isEmpty()) throw IllegalStateException("В подписке нет серверов VLESS или Trojan")
            return SubscriptionResult(profiles, info)
        } finally {
            conn.disconnect()
        }
    }

    /** Accepts plain text or base64 with one share link per line. */
    fun parseLinks(body: String): List<ServerProfile> {
        val text = body.trim()
        val plain = if (text.contains("://")) text else decodeBase64(text) ?: text
        return plain.lineSequence().mapNotNull { ServerProfile.parse(it) }.toList()
    }

    fun parseUserInfo(header: String?): SubscriptionInfo {
        if (header.isNullOrBlank()) return SubscriptionInfo()
        val values = header.split(';').mapNotNull { part ->
            val kv = part.split('=', limit = 2)
            if (kv.size == 2) kv[0].trim().lowercase() to (kv[1].trim().toDoubleOrNull()?.toLong() ?: 0L) else null
        }.toMap()
        return SubscriptionInfo(
            upload = values["upload"] ?: 0,
            download = values["download"] ?: 0,
            total = values["total"] ?: 0,
            expire = values["expire"] ?: 0,
        )
    }

    private fun decodeTitle(header: String?): String {
        if (header.isNullOrBlank()) return ""
        return if (header.startsWith("base64:")) decodeBase64(header.removePrefix("base64:")) ?: "" else header
    }

    private fun decodeBase64(s: String): String? {
        val clean = s.filterNot { it.isWhitespace() }
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        return runCatching { String(Base64.getDecoder().decode(padded)) }
            .recoverCatching { String(Base64.getUrlDecoder().decode(padded)) }
            .getOrNull()
    }
}
