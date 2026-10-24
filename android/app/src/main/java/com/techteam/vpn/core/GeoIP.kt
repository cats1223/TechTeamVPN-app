package com.techteam.vpn.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Where an IP address is, from ipwho.is: the phone's own address before the VPN, the server's after. */
object GeoIP {
    data class Location(
        val ip: String,
        val country: String,
        val countryCode: String,
        val city: String,
        val latitude: Double,
        val longitude: Double,
    ) {
        val flag: String get() = flag(countryCode)
        val place: String get() = listOf(city, country).filter { it.isNotEmpty() }.distinct().joinToString(", ")
    }

    /** [ip] null asks about the address the request comes from. */
    fun url(ip: String?): String = "https://ipwho.is/" + (ip ?: "")

    fun parse(json: String): Location? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (!o.optBoolean("success", false)) return null
        return Location(
            ip = o.optString("ip"),
            country = o.optString("country"),
            countryCode = o.optString("country_code"),
            city = o.optString("city"),
            latitude = o.optDouble("latitude", Double.NaN).takeUnless { it.isNaN() } ?: return null,
            longitude = o.optDouble("longitude", Double.NaN).takeUnless { it.isNaN() } ?: return null,
        )
    }

    /** "NL" -> 🇳🇱; anything else -> 🌐. */
    fun flag(countryCode: String): String {
        val cc = countryCode.uppercase()
        if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return "🌐"
        return cc.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }

    /** Blocking; call from a background thread. Returns null when the lookup fails. */
    fun lookup(ip: String? = null): Location? = runCatching {
        val connection = URL(url(ip)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.useCaches = false
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
