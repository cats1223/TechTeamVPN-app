package com.techteam.vpn.core

import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/**
 * A VLESS server parsed from a share link:
 * vless://<uuid>@<host>:<port>?type=tcp&security=reality&pbk=...&sid=...&sni=...&fp=chrome&flow=xtls-rprx-vision#Name
 */
data class VlessProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val encryption: String = "none",
    val flow: String = "",
    val network: String = "tcp",
    val security: String = "none",
    val sni: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val mldsa65Verify: String = "",
    val alpn: List<String> = emptyList(),
    val pinnedPeerCertSha256: String = "",
    val verifyPeerCertByName: String = "",
    val echConfigList: String = "",
    val host: String = "",
    val path: String = "",
    val serviceName: String = "",
    val authority: String = "",
    val mode: String = "",
    val headerType: String = "",
    val extra: String = "",
    /** The original link, kept so the profile can be shared or re-parsed. */
    val link: String = "",
) {
    companion object {
        /** Returns null for anything that is not a well-formed vless:// link. */
        fun parse(raw: String): VlessProfile? {
            val text = raw.trim()
            if (!text.startsWith("vless://", ignoreCase = true)) return null
            // Names often hold raw spaces and emoji that java.net.URI rejects, so cut the fragment off first.
            val hash = text.indexOf('#')
            val body = if (hash >= 0) text.substring(0, hash) else text
            val fragment = if (hash >= 0) text.substring(hash + 1) else null
            val uri = try {
                URI(body.replace(" ", "%20"))
            } catch (_: Exception) {
                return null
            }
            val uuid = uri.rawUserInfo?.let(::decode)?.takeIf { it.isNotBlank() } ?: return null
            val host = uri.host?.removePrefix("[")?.removeSuffix("]")?.takeIf { it.isNotBlank() } ?: return null
            val port = uri.port.takeIf { it in 1..65535 } ?: return null
            val q = parseQuery(uri.rawQuery)
            val network = when (val t = q["type"].orEmpty().lowercase()) {
                "", "raw" -> "tcp"
                "splithttp" -> "xhttp"
                else -> t
            }
            val name = fragment?.let { runCatching { decode(it) }.getOrDefault(it) }?.trim()?.takeIf { it.isNotBlank() } ?: "$host:$port"
            return VlessProfile(
                name = name,
                address = host,
                port = port,
                uuid = uuid,
                encryption = q["encryption"]?.takeIf { it.isNotBlank() } ?: "none",
                flow = q["flow"].orEmpty(),
                network = network,
                security = q["security"]?.lowercase()?.takeIf { it.isNotBlank() } ?: "none",
                sni = q["sni"] ?: q["peer"].orEmpty(),
                fingerprint = q["fp"].orEmpty(),
                publicKey = q["pbk"].orEmpty(),
                shortId = q["sid"].orEmpty(),
                spiderX = q["spx"].orEmpty(),
                mldsa65Verify = q["pqv"].orEmpty(),
                alpn = q["alpn"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                pinnedPeerCertSha256 = q["pcs"].orEmpty(),
                verifyPeerCertByName = q["vcn"].orEmpty(),
                echConfigList = q["ech"].orEmpty(),
                host = q["host"].orEmpty(),
                path = q["path"].orEmpty(),
                serviceName = q["serviceName"].orEmpty(),
                authority = q["authority"].orEmpty(),
                mode = q["mode"].orEmpty(),
                headerType = q["headerType"].orEmpty(),
                extra = q["extra"].orEmpty(),
                link = text,
            )
        }

        private fun parseQuery(raw: String?): Map<String, String> {
            if (raw.isNullOrEmpty()) return emptyMap()
            return raw.split('&').filter { it.isNotEmpty() }.associate { pair ->
                val i = pair.indexOf('=')
                if (i < 0) decode(pair) to "" else decode(pair.substring(0, i)) to decode(pair.substring(i + 1))
            }
        }

        // URLDecoder turns '+' into a space, which breaks base64 values such as pbk; keep it literal.
        private fun decode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    }
}
