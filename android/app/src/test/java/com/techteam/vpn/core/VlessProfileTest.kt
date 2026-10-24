package com.techteam.vpn.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class VlessProfileTest {
    private val reality =
        "vless://0f6e1a3c-7c6e-4a59-9f2a-3d1a7c2b9e01@nl1.techteam.example:443" +
            "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com&fp=chrome" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179e30d4fc2&spx=%2F&type=tcp" +
            "#🇳🇱 Нидерланды"

    @Test
    fun parsesRealityLink() {
        val p = VlessProfile.parse(reality)!!
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
        val p = VlessProfile.parse("vless://abc@[2001:db8::1]:8443?type=raw")!!
        assertEquals("2001:db8::1", p.address)
        assertEquals("tcp", p.network)
        assertEquals("none", p.security)
        assertEquals("none", p.encryption)
        assertEquals("2001:db8::1:8443", p.name)
    }

    @Test
    fun keepsPlusInValues() {
        val p = VlessProfile.parse("vless://abc@h:1?security=reality&pbk=a+b/c&sid=1")!!
        assertEquals("a+b/c", p.publicKey)
    }

    @Test
    fun rejectsInvalidLinks() {
        assertNull(VlessProfile.parse("vmess://abc"))
        assertNull(VlessProfile.parse("vless://@host:443"))
        assertNull(VlessProfile.parse("vless://id@host"))
        assertNull(VlessProfile.parse("not a link"))
    }

    @Test
    fun buildsRealityOutbound() {
        val p = VlessProfile.parse(reality)!!
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
    fun buildsTransports() {
        val ws = XrayConfig.outbound(VlessProfile.parse("vless://id@h:443?type=ws&security=tls&path=%2Fws&host=cdn.example&alpn=h2,http/1.1")!!)
            .getJSONObject("streamSettings")
        assertEquals("/ws", ws.getJSONObject("wsSettings").getString("path"))
        assertEquals("cdn.example", ws.getJSONObject("tlsSettings").getString("serverName"))
        assertEquals(2, ws.getJSONObject("tlsSettings").getJSONArray("alpn").length())

        val grpc = XrayConfig.outbound(VlessProfile.parse("vless://id@h:443?type=grpc&serviceName=svc&mode=multi&security=tls")!!)
            .getJSONObject("streamSettings").getJSONObject("grpcSettings")
        assertEquals("svc", grpc.getString("serviceName"))
        assertTrue(grpc.getBoolean("multiMode"))

        val xhttp = XrayConfig.outbound(
            VlessProfile.parse("vless://id@h:443?type=xhttp&path=%2Fx&mode=auto&extra=%7B%22xPaddingBytes%22%3A%22100-1000%22%7D")!!,
        ).getJSONObject("streamSettings").getJSONObject("xhttpSettings")
        assertEquals("/x", xhttp.getString("path"))
        assertEquals("100-1000", xhttp.getString("xPaddingBytes"))
    }

    @Test
    fun validationConfigHasNoTunFd() {
        val config = JSONObject(XrayConfig.build(VlessProfile.parse(reality)!!, null))
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
    }
}
