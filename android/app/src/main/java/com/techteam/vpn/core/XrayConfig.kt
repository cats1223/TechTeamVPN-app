package com.techteam.vpn.core

import org.json.JSONArray
import org.json.JSONObject

/** Builds the Xray-core JSON that libXray runs inside the VPN service. */
object XrayConfig {
    const val TUN_MTU = 1500
    /** What the system resolver is pointed at; Xray hijacks these queries. */
    val DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")
    /** Upstreams Xray resolves through the proxy. DoH rides a TCP stream, so DNS does not depend on UDP via the server. */
    val UPSTREAM_DNS = listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query")

    private val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "169.254.0.0/16",
        "100.64.0.0/10", "fc00::/7", "fe80::/10", "::1/128",
    )

    /**
     * @param tunFd the VpnService tun descriptor; Xray reads it from the `xray.tun.fd` env key.
     *              Pass null to build a config for validation only.
     * @param logPath file for Xray's error log at info level, used for troubleshooting.
     */
    fun build(profile: VlessProfile, tunFd: Int?, logPath: String? = null): String {
        val root = JSONObject()
        root.put(
            "log",
            if (logPath == null) JSONObject().put("loglevel", "warning")
            else JSONObject().put("loglevel", "info").put("access", "none").put("error", logPath),
        )
        if (tunFd != null) {
            root.put("env", JSONObject().put("xray.tun.fd", tunFd.toString()))
        }
        root.put(
            "dns",
            JSONObject()
                .put("servers", JSONArray(UPSTREAM_DNS))
                .put("queryStrategy", "UseIPv4"),
        )
        root.put(
            "inbounds",
            JSONArray().put(
                JSONObject()
                    .put("tag", "tun-in")
                    .put("protocol", "tun")
                    .put("settings", JSONObject().put("name", "xray0").put("mtu", TUN_MTU))
                    .put(
                        "sniffing",
                        JSONObject()
                            .put("enabled", true)
                            .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
                            .put("routeOnly", true),
                    ),
            ),
        )
        root.put(
            "outbounds",
            JSONArray()
                .put(outbound(profile))
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
                .put(JSONObject().put("tag", "dns-out").put("protocol", "dns")),
        )
        root.put(
            "routing",
            JSONObject()
                .put("domainStrategy", "AsIs")
                .put(
                    "rules",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("inboundTag", JSONArray(listOf("tun-in")))
                                .put("port", "53")
                                .put("outboundTag", "dns-out"),
                        )
                        .put(JSONObject().put("ip", JSONArray(PRIVATE_CIDRS)).put("outboundTag", "direct")),
                ),
        )
        return root.toString()
    }

    fun outbound(p: VlessProfile): JSONObject {
        val settings = JSONObject()
            .put("address", p.address)
            .put("port", p.port)
            .put("id", p.uuid)
            .put("encryption", p.encryption)
        if (p.flow.isNotEmpty()) settings.put("flow", p.flow)

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", settings)
            .put("streamSettings", streamSettings(p))
    }

    private fun streamSettings(p: VlessProfile): JSONObject {
        val s = JSONObject().put("network", p.network).put("security", p.security)
        val serverName = p.sni.ifEmpty { p.host }

        when (p.security) {
            "reality" -> s.put(
                "realitySettings",
                JSONObject()
                    .put("serverName", serverName)
                    .put("fingerprint", p.fingerprint.ifEmpty { "chrome" })
                    .put("password", p.publicKey)
                    .put("shortId", p.shortId)
                    .putIfNotEmpty("spiderX", p.spiderX)
                    .putIfNotEmpty("mldsa65Verify", p.mldsa65Verify),
            )
            "tls" -> s.put(
                "tlsSettings",
                JSONObject()
                    .putIfNotEmpty("serverName", serverName)
                    .putIfNotEmpty("fingerprint", p.fingerprint)
                    .apply { if (p.alpn.isNotEmpty()) put("alpn", JSONArray(p.alpn)) }
                    .putIfNotEmpty("pinnedPeerCertSha256", p.pinnedPeerCertSha256)
                    .putIfNotEmpty("verifyPeerCertByName", p.verifyPeerCertByName)
                    .putIfNotEmpty("echConfigList", p.echConfigList),
            )
        }

        when (p.network) {
            "ws" -> s.put("wsSettings", JSONObject().put("path", p.path.ifEmpty { "/" }).putIfNotEmpty("host", p.host))
            "httpupgrade" -> s.put(
                "httpupgradeSettings",
                JSONObject().put("path", p.path.ifEmpty { "/" }).putIfNotEmpty("host", p.host),
            )
            "grpc" -> s.put(
                "grpcSettings",
                JSONObject()
                    .put("serviceName", p.serviceName)
                    .putIfNotEmpty("authority", p.authority)
                    .put("multiMode", p.mode == "multi"),
            )
            "xhttp" -> {
                val x = JSONObject()
                    .put("path", p.path.ifEmpty { "/" })
                    .putIfNotEmpty("host", p.host)
                    .putIfNotEmpty("mode", p.mode)
                if (p.extra.isNotEmpty()) {
                    runCatching { JSONObject(p.extra) }.getOrNull()?.let { extra ->
                        extra.keys().forEach { k -> x.put(k, extra.get(k)) }
                    }
                }
                s.put("xhttpSettings", x)
            }
            "tcp" -> if (p.headerType == "http") {
                val request = JSONObject().put("path", JSONArray(listOf(p.path.ifEmpty { "/" })))
                if (p.host.isNotEmpty()) {
                    request.put("headers", JSONObject().put("Host", JSONArray(p.host.split(',').map { it.trim() })))
                }
                s.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http").put("request", request)))
            }
        }
        return s
    }

    private fun JSONObject.putIfNotEmpty(key: String, value: String): JSONObject =
        if (value.isEmpty()) this else put(key, value)
}
