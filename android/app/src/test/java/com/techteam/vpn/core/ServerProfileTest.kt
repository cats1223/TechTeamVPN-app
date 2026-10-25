package com.techteam.vpn.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ServerProfileTest {
    private val reality =
        "vless://0f6e1a3c-7c6e-4a59-9f2a-3d1a7c2b9e01@nl1.techteam.example:443" +
            "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com&fp=chrome" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179e30d4fc2&spx=%2F&type=tcp" +
            "#🇳🇱 Нидерланды"

    @Test
    fun parsesRealityLink() {
        val p = ServerProfile.parse(reality)!!
        assertEquals("0f6e1a3c-7c6e-4a59-9f2a-3d1a7c2b9e01", p.uuid)
        assertEquals("nl1.techteam.example", p.address)
        assertEquals(443, p.port)
        assertEquals("xtls-rprx-vision", p.flow)
        assertEquals("reality", p.security)
        assertEquals("tcp", p.network)
        assertEquals("www.microsoft.com", p.sni)
        assertEquals("SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc", p.publicKey)
        assertEquals("6ba85179e30d4fc2", p.shortId)
        assertEquals("/", p.spiderX)
        assertEquals("🇳🇱 Нидерланды", p.name)
    }

    @Test
    fun parsesIpv6AndDefaults() {
        val p = ServerProfile.parse("vless://abc@[2001:db8::1]:8443?type=raw")!!
        assertEquals("2001:db8::1", p.address)
        assertEquals("tcp", p.network)
        assertEquals("none", p.security)
        assertEquals("none", p.encryption)
        assertEquals("2001:db8::1:8443", p.name)
    }

    @Test
    fun keepsPlusInValues() {
        val p = ServerProfile.parse("vless://abc@h:1?security=reality&pbk=a+b/c&sid=1")!!
        assertEquals("a+b/c", p.publicKey)
    }

    @Test
    fun rejectsInvalidLinks() {
        assertNull(ServerProfile.parse("vmess://abc"))
        assertNull(ServerProfile.parse("vless://@host:443"))
        assertNull(ServerProfile.parse("vless://id@host"))
        assertNull(ServerProfile.parse("not a link"))
    }

    @Test
    fun buildsRealityOutbound() {
        val p = ServerProfile.parse(reality)!!
        val config = JSONObject(XrayConfig.build(p, 42))
        assertEquals("42", config.getJSONObject("env").getString("xray.tun.fd"))
        assertEquals("tun", config.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))

        val out = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", out.getString("protocol"))
        val settings = out.getJSONObject("settings")
        assertEquals("nl1.techteam.example", settings.getString("address"))
        assertEquals(443, settings.getInt("port"))
        assertEquals("xtls-rprx-vision", settings.getString("flow"))

        val stream = out.getJSONObject("streamSettings")
        assertEquals("reality", stream.getString("security"))
        val r = stream.getJSONObject("realitySettings")
        assertEquals("www.microsoft.com", r.getString("serverName"))
        assertEquals("SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc", r.getString("password"))
        assertEquals("6ba85179e30d4fc2", r.getString("shortId"))
        assertEquals("chrome", r.getString("fingerprint"))
    }

    @Test
    fun russiaDirectRoutesRussianSitesPastTheProxy() {
        val p = ServerProfile.parse(reality)!!
        val plain = JSONObject(XrayConfig.build(p, 42))
        assertFalse(plain.getJSONObject("env").has("xray.location.asset"))
        assertEquals(2, plain.getJSONObject("routing").getJSONArray("rules").length())

        val config = JSONObject(XrayConfig.build(p, 42, russiaDirect = true, assetDir = "/geo"))
        assertEquals("/geo", config.getJSONObject("env").getString("xray.location.asset"))
        val rules = config.getJSONObject("routing").getJSONArray("rules")
        val direct = (0 until rules.length()).map { rules.getJSONObject(it) }.filter { it.getString("outboundTag") == "direct" }
        assertTrue(direct.any { it.optJSONArray("ip")?.toString() == "[\"geoip:ru\"]" })
        assertTrue(direct.any { it.optJSONArray("domain")?.toString()?.contains("domain:ru") == true })
        val dns = config.getJSONObject("dns").getJSONArray("servers")
        assertEquals(XrayConfig.RUSSIAN_DNS, dns.getJSONObject(0).getString("address"))
    }

    @Test
    fun buildsTransports() {
        val ws = XrayConfig.outbound(ServerProfile.parse("vless://id@h:443?type=ws&security=tls&path=%2Fws&host=cdn.example&alpn=h2,http/1.1")!!)
            .getJSONObject("streamSettings")
        assertEquals("/ws", ws.getJSONObject("wsSettings").getString("path"))
        assertEquals("cdn.example", ws.getJSONObject("tlsSettings").getString("serverName"))
        assertEquals(2, ws.getJSONObject("tlsSettings").getJSONArray("alpn").length())

        val grpc = XrayConfig.outbound(ServerProfile.parse("vless://id@h:443?type=grpc&serviceName=svc&mode=multi&security=tls")!!)
            .getJSONObject("streamSettings").getJSONObject("grpcSettings")
        assertEquals("svc", grpc.getString("serviceName"))
        assertTrue(grpc.getBoolean("multiMode"))

        val xhttp = XrayConfig.outbound(
            ServerProfile.parse("vless://id@h:443?type=xhttp&path=%2Fx&mode=auto&extra=%7B%22xPaddingBytes%22%3A%22100-1000%22%7D")!!,
        ).getJSONObject("streamSettings").getJSONObject("xhttpSettings")
        assertEquals("/x", xhttp.getString("path"))
        assertEquals("100-1000", xhttp.getString("xPaddingBytes"))
    }

    @Test
    fun validationConfigHasNoTunFd() {
        val config = JSONObject(XrayConfig.build(ServerProfile.parse(reality)!!, null))
        assertFalse(config.has("env"))
    }

    @Test
    fun parsesSubscriptionBodies() {
        val links = "$reality\nvless://id2@h2:443?type=ws#Two\ntrojan://ignored@h:1\n"
        assertEquals(2, Subscription.parseLinks(links).size)
        val b64 = Base64.getEncoder().encodeToString(links.toByteArray()).trimEnd('=')
        assertEquals(2, Subscription.parseLinks(b64).size)
    }

    @Test
    fun parsesUserInfo() {
        val info = Subscription.parseUserInfo("upload=1024; download=2048; total=10737418240; expire=1767225600")
        assertEquals(1024L, info.upload)
        assertEquals(2048L, info.download)
        assertEquals(10737418240L, info.total)
        assertEquals(1767225600L, info.expire)
        assertNotNull(Subscription.parseUserInfo(null))
    }

    @Test
    fun unwrapsDeepLinks() {
        val sub = "https://176.0.0.1:50070/abc/def"
        assertEquals(sub, DeepLink.unwrap("techteamvpn://add?url=https%3A%2F%2F176.0.0.1%3A50070%2Fabc%2Fdef"))
        assertEquals(sub, DeepLink.unwrap("techteamvpn://add/$sub"))
        assertEquals(sub, DeepLink.unwrap("techteamvpn://add/https%3A%2F%2F176.0.0.1%3A50070%2Fabc%2Fdef"))
        assertEquals("vless://id@h:1", DeepLink.unwrap("vless://id@h:1"))
        assertEquals("", DeepLink.unwrap("techteamvpn://add"))
        assertTrue(DeepLink.wantsClipboard("techteamvpn://add"))
        assertTrue(DeepLink.wantsClipboard("techteamvpn://add/"))
        assertFalse(DeepLink.wantsClipboard("techteamvpn://add?url=https%3A%2F%2Fa.b%2Fc"))
        assertFalse(DeepLink.wantsClipboard("vless://id@h:1"))
    }

    @Test
    fun comparesVersions() {
        assertTrue(Updates.isNewer("1.1.0", "1.0.1"))
        assertTrue(Updates.isNewer("v1.10.0", "1.9.2"))
        assertTrue(Updates.isNewer("1.0.1", "1.0"))
        assertFalse(Updates.isNewer("1.0.1", "1.0.1"))
        assertFalse(Updates.isNewer("1.0.0", "1.1.0"))
        // The latest-release endpoint never returns betas; a beta tester is offered the release.
        assertTrue(Updates.isNewer("1.2.0", "1.2.0-beta.1"))
        assertFalse(Updates.isNewer("1.1.0", "1.2.0-beta.1"))
    }

    @Test
    fun parsesGeoIp() {
        val l = GeoIP.parse("""{"ip":"176.124.220.95","success":true,"country":"Netherlands","country_code":"NL","city":"Amsterdam","latitude":52.37,"longitude":4.89}""")!!
        assertEquals("Amsterdam, Netherlands", l.place)
        assertEquals(52.37, l.latitude, 1e-9)
        assertEquals("\uD83C\uDDF3\uD83C\uDDF1", l.flag)
        assertNull(GeoIP.parse("""{"success":false,"message":"Reserved range"}"""))
        assertEquals("\uD83C\uDF10", GeoIP.flag(""))
    }

    @Test
    fun parsesLatestRelease() {
        val json = """{"tag_name":"v1.1.0","html_url":"https://github.com/o/r/releases/tag/v1.1.0","assets":[
            {"name":"TechTeamVPN-v1.1.0-unsigned.ipa","browser_download_url":"https://x/a.ipa"},
            {"name":"TechTeamVPN-v1.1.0.apk","browser_download_url":"https://x/a.apk"}]}"""
        val r = Updates.parse(json)
        assertEquals("1.1.0", r.version)
        assertEquals("https://x/a.apk", r.downloadUrl)
        assertEquals("https://github.com/o/r/releases/tag/v1.1.0", Updates.parse("""{"tag_name":"v1.1.0","html_url":"https://github.com/o/r/releases/tag/v1.1.0"}""").downloadUrl)
    }

    @Test
    fun recognizesRussianApps() {
        assertTrue(RussianApps.isRussian("ru.sberbankmobile"))
        assertTrue(RussianApps.isRussian("com.idamob.tinkoff.android"))
        assertFalse(RussianApps.isRussian("org.telegram.messenger"))
    }

    @Test
    fun globeProjectionRoundTrips() {
        val g = GlobeMath(55.75, 37.62)
        val center = g.project(55.75, 37.62)!!
        assertEquals(0.0, center.first, 1e-9)
        assertEquals(0.0, center.second, 1e-9)
        val helsinki = g.project(60.17, 24.94)!!
        val back = g.unproject(helsinki.first, helsinki.second)!!
        assertEquals(60.17, back.first, 1e-6)
        assertEquals(24.94, back.second, 1e-6)
        assertNull(g.project(-55.75, 37.62 - 180))
        val path = GlobeMath.greatCircle(55.75 to 37.62, 60.17 to 24.94)
        assertEquals(55.75, path.first().first, 1e-6)
        assertEquals(24.94, path.last().second, 1e-6)
    }

    @Test
    fun parsesTrojanAndHysteria2() {
        val trojan = ServerProfile.parse("trojan://p%40ss@t.example:443?type=ws&path=%2Ft&sni=cdn.example#Trojan")!!
        assertEquals(ServerProfile.TROJAN, trojan.protocol)
        assertEquals("p@ss", trojan.password)
        assertEquals("tls", trojan.security)
        assertEquals("ws", trojan.network)
        val tOut = XrayConfig.outbound(trojan)
        assertEquals("trojan", tOut.getString("protocol"))
        assertEquals("p@ss", tOut.getJSONObject("settings").getString("password"))

        val hy = ServerProfile.parse("hy2://user:1234-5678@h.example:443,20000-30000/?sni=real.example&obfs=salamander&obfs-password=secret#HY")!!
        assertEquals(ServerProfile.HYSTERIA2, hy.protocol)
        assertEquals("user:1234-5678", hy.password)
        assertEquals(443, hy.port)
        assertEquals("secret", hy.obfsPassword)
        val hOut = XrayConfig.outbound(hy)
        assertEquals("hysteria", hOut.getString("protocol"))
        val stream = hOut.getJSONObject("streamSettings")
        assertEquals("hysteria", stream.getString("network"))
        assertEquals("user:1234-5678", stream.getJSONObject("hysteriaSettings").getString("auth"))
        assertEquals("real.example", stream.getJSONObject("tlsSettings").getString("serverName"))
        assertTrue(stream.has("finalmask"))

        assertTrue(ServerProfile.isShareLink("HYSTERIA2://a@b:1"))
        assertNull(ServerProfile.parse("vmess://abc"))
    }
}
