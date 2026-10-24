# TechTeamVPN

Нативные клиенты VPN-сервиса TechTeamVPN для **Android** (Kotlin + Jetpack Compose) и **iOS** (Swift + SwiftUI).
Протокол — **VLESS** (Reality / TLS) на ядре [Xray-core](https://github.com/XTLS/Xray-core) через официальную обёртку [libXray](https://github.com/XTLS/libXray).

## Возможности

- Подключение одной кнопкой, таймер сессии, статус в уведомлении (Android) и в системных настройках VPN (iOS)
- Добавление ключа `vless://…`: вставка, буфер обмена, сканирование QR-кода, открытие ссылки `vless://` из браузера или мессенджера
- Ссылка подписки (`https://…`): список серверов, обновление, трафик и срок действия из заголовка `subscription-userinfo`, название из `profile-title`
- Список серверов, выбор, проверка пинга (TCP), удаление своих ключей
- Транспорты: TCP (RAW), XHTTP, WebSocket, gRPC, HTTPUpgrade; безопасность: REALITY (включая `pqv`), TLS (`alpn`, `fp`, `pcs`, `vcn`, `ech`), flow `xtls-rprx-vision`
- DNS идёт через туннель, локальные сети (`10.0.0.0/8`, `192.168.0.0/16` и т. д.) — напрямую
- Android: плитка в шторке быстрых настроек, поддержка «Постоянной VPN»

## Как устроено

```
android/                    Kotlin, Jetpack Compose
  core/VlessProfile.kt      разбор ссылок vless://
  core/XrayConfig.kt        генерация JSON-конфига Xray (TUN-вход + VLESS-выход)
  core/Subscription.kt      загрузка и разбор подписки
  core/XrayCore.kt          вызовы libXray (runXray / stopXray)
  vpn/TechTeamVpnService.kt VpnService: поднимает TUN и передаёт его fd в Xray
ios/                        Swift, SwiftUI (проект генерируется XcodeGen)
  Shared/                   общий код приложения и расширения: разбор ссылок, конфиг, подписка, хранилище
  TechTeamVPN/              приложение: UI, NETunnelProviderManager
  PacketTunnel/             Network Extension: находит fd utun и запускает Xray
scripts/build-libxray.sh    сборка ядра libXray для обеих платформ
```

Ядро Xray само читает пакеты из TUN-интерфейса: приложение кладёт номер дескриптора в `env["xray.tun.fd"]` конфига.
Отдельный tun2socks не нужен.

## Сборка

### 1. Ядро libXray

Нужны Go 1.25+ и Python 3. Скрипт скачивает libXray (закреплённая версия `v26.10.21`) и собирает его через gomobile.

```bash
# Android: нужен Android NDK
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<версия>
scripts/build-libxray.sh android   # -> android/app/libs/libXray.aar

# iOS: только на macOS с Xcode
scripts/build-libxray.sh ios       # -> ios/Frameworks/LibXray.xcframework
```

### 2. Android

```bash
cd android
./gradlew testDebugUnitTest   # юнит-тесты разбора ссылок и конфига
./gradlew assembleDebug       # APK в app/build/outputs/apk/debug/
```

Или откройте папку `android/` в Android Studio. Для публикации в Google Play настройте подпись release-сборки
и объясните использование `FOREGROUND_SERVICE_SPECIAL_USE` (подтип `vpn`) в консоли Play.

### 3. iOS — сборка и подпись в GitHub (Mac не нужен)

Каждый пуш в `main` или `feature/**` собирает iOS на macOS-раннере GitHub. В разделе Actions → запуск → Artifacts:

- `TechTeamVPN-ios-unsigned` — неподписанный IPA (можно подписать на телефоне, например в ESign/Feather);
- `TechTeamVPN-ios-signed` — подписанный IPA, если заданы секреты ниже.

#### Подпись iOS в GitHub

Settings → Secrets and variables → Actions → New repository secret:

| Секрет | Что положить |
|---|---|
| `IOS_P12_BASE64` | сертификат `.p12` в base64 |
| `IOS_P12_PASSWORD` | пароль от `.p12` (если нет — пустая строка) |
| `IOS_PROFILE_APP_BASE64` | `.mobileprovision` для приложения, base64 |
| `IOS_PROFILE_TUNNEL_BASE64` | `.mobileprovision` для VPN-расширения, base64 |

Base64 делается так: `base64 -i файл | pbcopy` (macOS), `base64 -w0 файл` (Linux), `[Convert]::ToBase64String([IO.File]::ReadAllBytes("файл"))` (Windows PowerShell)
или любым онлайн-конвертером «file to base64» на телефоне.

Требования к профилям (иначе сборка остановится с понятной ошибкой):

- два **явных** App ID (не `*`): приложение, например `com.techteam.vpn`, и расширение `com.techteam.vpn.tunnel`;
- у обоих включены **Network Extensions → Packet Tunnel** и **App Groups** с одной и той же группой;
- профиль подходит к сертификату из `.p12`; для Ad Hoc/Development — в профиль добавлен UDID вашего iPhone.

Bundle id, команда и App Group берутся из профилей автоматически.

### CI

`.github/workflows/build.yml` собирает libXray, прогоняет тесты, собирает APK (артефакт `TechTeamVPN-android`)
и iOS-сборки (см. выше).

## Формат ключа

```
vless://<uuid>@<host>:<port>?type=tcp&security=reality&pbk=<публичный ключ>&sid=<short id>&sni=<домен>&fp=chrome&flow=xtls-rprx-vision#Название
```

Подписка — обычный текст или base64 со ссылками по одной на строку (совместимо с Marzban, 3x-ui, Remnawave и т. п.).

> Xray запрещает VLESS без шифрования (`security=none`) к публичным адресам — такие ключи не подключатся.
> Используйте REALITY или TLS.
