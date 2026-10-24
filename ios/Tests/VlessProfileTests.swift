import XCTest

final class VlessProfileTests: XCTestCase {
    private let reality =
        "vless://0f6e1a3c-7c6e-4a59-9f2a-3d1a7c2b9e01@nl1.techteam.example:443" +
        "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com&fp=chrome" +
        "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179e30d4fc2&spx=%2F&type=tcp" +
        "#🇳🇱 Нидерланды"

    func testParsesRealityLink() throws {
        let p = try XCTUnwrap(VlessProfile.parse(reality))
        XCTAssertEqual(p.uuid, "0f6e1a3c-7c6e-4a59-9f2a-3d1a7c2b9e01")
        XCTAssertEqual(p.address, "nl1.techteam.example")
        XCTAssertEqual(p.port, 443)
        XCTAssertEqual(p.flow, "xtls-rprx-vision")
        XCTAssertEqual(p.security, "reality")
        XCTAssertEqual(p.network, "tcp")
        XCTAssertEqual(p.sni, "www.microsoft.com")
        XCTAssertEqual(p.publicKey, "SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc")
        XCTAssertEqual(p.shortId, "6ba85179e30d4fc2")
        XCTAssertEqual(p.spiderX, "/")
        XCTAssertEqual(p.name, "🇳🇱 Нидерланды")
    }

    func testParsesIPv6AndDefaults() throws {
        let p = try XCTUnwrap(VlessProfile.parse("vless://abc@[2001:db8::1]:8443?type=raw"))
        XCTAssertEqual(p.address, "2001:db8::1")
        XCTAssertEqual(p.network, "tcp")
        XCTAssertEqual(p.security, "none")
        XCTAssertEqual(p.encryption, "none")
    }

    func testKeepsPlusInValues() throws {
        let p = try XCTUnwrap(VlessProfile.parse("vless://abc@h:1?security=reality&pbk=a+b/c&sid=1"))
        XCTAssertEqual(p.publicKey, "a+b/c")
    }

    func testRejectsInvalidLinks() {
        XCTAssertNil(VlessProfile.parse("vmess://abc"))
        XCTAssertNil(VlessProfile.parse("vless://@host:443"))
        XCTAssertNil(VlessProfile.parse("vless://id@host"))
        XCTAssertNil(VlessProfile.parse("not a link"))
    }

    func testBuildsRealityConfig() throws {
        let p = try XCTUnwrap(VlessProfile.parse(reality))
        let json = try XrayConfig.build(p, tunFd: 42)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any])
        XCTAssertEqual((root["env"] as? [String: String])?["xray.tun.fd"], "42")

        let out = try XCTUnwrap((root["outbounds"] as? [[String: Any]])?.first)
        XCTAssertEqual(out["protocol"] as? String, "vless")
        let settings = try XCTUnwrap(out["settings"] as? [String: Any])
        XCTAssertEqual(settings["address"] as? String, "nl1.techteam.example")
        XCTAssertEqual(settings["port"] as? Int, 443)
        XCTAssertEqual(settings["flow"] as? String, "xtls-rprx-vision")

        let stream = try XCTUnwrap(out["streamSettings"] as? [String: Any])
        let r = try XCTUnwrap(stream["realitySettings"] as? [String: Any])
        XCTAssertEqual(r["serverName"] as? String, "www.microsoft.com")
        XCTAssertEqual(r["password"] as? String, "SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc")
        XCTAssertEqual(r["shortId"] as? String, "6ba85179e30d4fc2")
    }

    func testBuildsTransports() throws {
        let xhttp = try XCTUnwrap(VlessProfile.parse(
            "vless://id@h:443?type=xhttp&path=%2Fx&mode=auto&extra=%7B%22xPaddingBytes%22%3A%22100-1000%22%7D"
        ))
        let stream = try XCTUnwrap(XrayConfig.outbound(xhttp)["streamSettings"] as? [String: Any])
        let x = try XCTUnwrap(stream["xhttpSettings"] as? [String: Any])
        XCTAssertEqual(x["path"] as? String, "/x")
        XCTAssertEqual(x["xPaddingBytes"] as? String, "100-1000")

        let grpc = try XCTUnwrap(VlessProfile.parse("vless://id@h:443?type=grpc&serviceName=svc&mode=multi&security=tls"))
        let g = try XCTUnwrap((XrayConfig.outbound(grpc)["streamSettings"] as? [String: Any])?["grpcSettings"] as? [String: Any])
        XCTAssertEqual(g["serviceName"] as? String, "svc")
        XCTAssertEqual(g["multiMode"] as? Bool, true)
    }

    func testParsesSubscriptionBodies() {
        let links = "\(reality)\nvless://id2@h2:443?type=ws#Two\ntrojan://ignored@h:1\n"
        XCTAssertEqual(Subscription.parseLinks(links).count, 2)
        let b64 = Data(links.utf8).base64EncodedString().trimmingCharacters(in: CharacterSet(charactersIn: "="))
        XCTAssertEqual(Subscription.parseLinks(b64).count, 2)
    }

    func testParsesUserInfo() {
        let info = Subscription.parseUserInfo("upload=1024; download=2048; total=10737418240; expire=1767225600")
        XCTAssertEqual(info.upload, 1024)
        XCTAssertEqual(info.download, 2048)
        XCTAssertEqual(info.total, 10_737_418_240)
        XCTAssertEqual(info.expire, 1_767_225_600)
    }

    func testUnwrapsDeepLinks() {
        let sub = "https://176.0.0.1:50070/abc/def"
        XCTAssertEqual(DeepLink.unwrap("techteamvpn://add?url=https%3A%2F%2F176.0.0.1%3A50070%2Fabc%2Fdef"), sub)
        XCTAssertEqual(DeepLink.unwrap("techteamvpn://add/\(sub)"), sub)
        XCTAssertEqual(DeepLink.unwrap("techteamvpn://add/https%3A%2F%2F176.0.0.1%3A50070%2Fabc%2Fdef"), sub)
        XCTAssertEqual(DeepLink.unwrap("vless://id@h:1"), "vless://id@h:1")
        XCTAssertEqual(DeepLink.unwrap("techteamvpn://add"), "")
        XCTAssertTrue(DeepLink.wantsClipboard("techteamvpn://add"))
        XCTAssertTrue(DeepLink.wantsClipboard("techteamvpn://add/"))
        XCTAssertFalse(DeepLink.wantsClipboard("techteamvpn://add?url=https%3A%2F%2Fa.b%2Fc"))
        XCTAssertFalse(DeepLink.wantsClipboard("vless://id@h:1"))
    }
}
