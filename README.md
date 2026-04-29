# 🏠 Homelab Dashboard

[![Swift](https://img.shields.io/badge/Swift-6.0-orange.svg?logo=swift)](https://swift.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-iOS%2026%2B-blue.svg)](https://developer.apple.com/ios/)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Made with SwiftUI](https://img.shields.io/badge/Made%20with-SwiftUI-blue.svg?logo=swift)](https://developer.apple.com/xcode/swiftui/)
[![Made with Jetpack Compose](https://img.shields.io/badge/Made%20with-Jetpack%20Compose-green.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)

Homelab Dashboard is a fully native mobile app for monitoring and managing a self-hosted homelab from one place. The project ships two dedicated apps, one for iOS and one for Android, designed around the same product idea while respecting each platform's native UI patterns.

> **Disclaimer:** This is a vibe-coding project built for fun and personal use. It is provided as-is with no guarantees. The author assumes no responsibility for any issues, data loss, or damages resulting from the use of this software.

<table align="center">
  <tr>
    <th>iOS Dashboard</th>
    <th>Android Dashboard</th>
  </tr>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/Dashboard.png" width="230" /></td>
    <td align="center"><img src="media-docs/foto-android/Dashboard.jpg" width="230" /></td>
  </tr>
</table>

---

## 🚀 Highlights

- **33 integrated service dashboards** across infrastructure, networking, media automation, observability, and developer tooling.
- **One app, many instances**: add multiple instances of the same service and switch between them without friction.
- **Fully native on both platforms**: SwiftUI on iOS, Jetpack Compose on Android.
- **Practical daily-use features**: encrypted backup and restore, biometric unlock, multilingual UI, alternate icons, and fast in-app update prompts.
- **Utilities beyond services**: built-in bookmarks plus quick Tailscale launch support for remote access workflows.

---

## 🧩 Integrated Services

### Core Infrastructure

- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/portainer.png" width="18" style="vertical-align:middle"> **Portainer**: container overview, quick actions, resource usage.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/proxmox.png" width="18" style="vertical-align:middle"> **Proxmox VE**: nodes, guests, storage, networking, backups, and cluster operations.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/uptime-kuma.png" width="18" style="vertical-align:middle"> **Uptime Kuma**: monitor status, uptime visibility, and incident tracking.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/dockhand.png" width="18" style="vertical-align:middle"> **Dockhand**: native container management dashboard.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/dockmon.png" width="18" style="vertical-align:middle"> **DockMon**: Docker host and container monitoring with logs, restart, and update actions.
- <img src="HomelabSwift/Homelab/Assets.xcassets/service-komodo.imageset/komodo.png" width="18" style="vertical-align:middle"> **Komodo**: resource, deployment, stack, and server monitoring.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/beszel.png" width="18" style="vertical-align:middle"> **Beszel**: server monitoring across nodes.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/linux-update-dashboard.png" width="18" style="vertical-align:middle"> **Linux Update**: pending package updates across hosts.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/crafty-controller.png" width="18" style="vertical-align:middle"> **Crafty Controller**: game server management dashboard.
- **Pterodactyl**: game server management panel with power controls, resource stats, and live status visibility.
- **Calagopus**: next-generation game server management panel with power controls, uptime, and resource stats.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/gitea.png" width="18" style="vertical-align:middle"> **Gitea / Forgejo**: repositories, activity, and source browsing.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/pangolin.png" width="18" style="vertical-align:middle"> **Pangolin / Newt**: tunnel and peer visibility.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/healthchecks.png" width="18" style="vertical-align:middle"> **Healthchecks**: uptime checks and health status.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/patchmon.png" width="18" style="vertical-align:middle"> **PatchMon**: software update visibility across your stack.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/wakapi.png" width="18" style="vertical-align:middle"> **Wakapi**: coding activity and time tracking stats.

### Networking & DNS

- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/pi-hole.png" width="18" style="vertical-align:middle"> **Pi-hole**: queries, blocked domains, toggles, timers.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/adguard-home.png" width="18" style="vertical-align:middle"> **AdGuard Home**: filters, rewrites, blocked services, query activity.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/ubiquiti-unifi.png" width="18" style="vertical-align:middle"> **Ubiquiti Network**: gateways, switches, access points, clients, and site visibility.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/technitium.png" width="18" style="vertical-align:middle"> **Technitium DNS**: DNS metrics and health.
- <img src="HomelabSwift/Homelab/Assets.xcassets/service-maltrail.imageset/icon.png" width="18" style="vertical-align:middle"> **Maltrail**: threat detections, daily findings, and event visibility.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/nginx-proxy-manager.png" width="18" style="vertical-align:middle"> **Nginx Proxy Manager / NPMplus**: proxy hosts, streams, redirects, certificates, access lists.

### Media & Observability

- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/plex.png" width="18" style="vertical-align:middle"> **Plex**: libraries, sessions, recently added media.
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/jellystat.png" width="18" style="vertical-align:middle"> **Jellystat**: Jellyfin activity, streams, usage insights.

### Servarr Stack

- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/sonarr.png" width="18" style="vertical-align:middle"> **Sonarr**
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/radarr.png" width="18" style="vertical-align:middle"> **Radarr**
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/lidarr.png" width="18" style="vertical-align:middle"> **Lidarr**
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/prowlarr.png" width="18" style="vertical-align:middle"> **Prowlarr**
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/qbittorrent.png" width="18" style="vertical-align:middle"> **qBittorrent**
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/jellyseerr.png" width="18" style="vertical-align:middle"> **Jellyseerr**
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/bazarr.png" width="18" style="vertical-align:middle"> **Bazarr**
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/gluetun.png" width="18" style="vertical-align:middle"> **Gluetun**
- <img src="https://cdn.jsdelivr.net/gh/selfhst/icons/png/flaresolverr.png" width="18" style="vertical-align:middle"> **FlareSolverr**

The full Servarr stack is available as a unified media automation dashboard, so downloads, health, requests, VPN status, and indexer state can be checked from one place.

---

## 🍎 iOS App

The iOS version is built with **Swift 6** and **SwiftUI** for **iOS 26+**. The interface uses a polished glass-heavy visual language, native navigation, and system integrations such as alternate icons, biometric unlock, and document-based backup import/export.

<table align="center">
  <tr>
    <th>Dashboard</th>
    <th>Servarr</th>
    <th>Bookmarks</th>
  </tr>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/Dashboard.png" width="180" /></td>
    <td align="center"><img src="media-docs/foto-ios/Servarr.png" width="180" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9201.PNG" width="180" /></td>
  </tr>
</table>

<table align="center">
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9187.PNG" width="120" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9193.PNG" width="120" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9190.PNG" width="120" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9199.PNG" width="120" /></td>
    <td align="center"><img src="media-docs/foto-ios/plex.PNG" width="120" /></td>
  </tr>
  <tr>
    <td align="center"><sub>Portainer</sub></td>
    <td align="center"><sub>Beszel</sub></td>
    <td align="center"><sub>Nginx Proxy</sub></td>
    <td align="center"><sub>Pi-hole</sub></td>
    <td align="center"><sub>Plex</sub></td>
  </tr>
</table>

<details>
<summary><b>📸 View all iOS screenshots</b></summary>
<br>

**Portainer**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9187.PNG" width="180" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9188.PNG" width="180" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9189.PNG" width="180" /></td>
  </tr>
</table>

**Nginx Proxy Manager / NPMplus**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9190.PNG" width="180" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9191.PNG" width="180" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9192.PNG" width="180" /></td>
  </tr>
</table>

**Beszel**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9193.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9194.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9195.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9196.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9197.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9198.PNG" width="145" /></td>
  </tr>
</table>

**Pi-hole · AdGuard Home · Healthchecks**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9199.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9218.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9219.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9238.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9239.PNG" width="145" /></td>
  </tr>
</table>

**Gitea / Forgejo · PatchMon · Jellystat · Plex**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9200.jpg" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9269.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9275.PNG" width="145" /></td>
    <td align="center"><img src="media-docs/foto-ios/plex.PNG" width="145" /></td>
  </tr>
</table>

</details>

---

## 🤖 Android App

The Android version is built with **Kotlin** and **Jetpack Compose** for **Android 8.0+**. It uses a modern Material 3 style, dynamic color where available, expressive cards, and native Android architecture patterns.

<table align="center">
  <tr>
    <th>Dashboard</th>
    <th>Servarr</th>
    <th>Bookmarks</th>
  </tr>
  <tr>
    <td align="center"><img src="media-docs/foto-android/Dashboard.jpg" width="180" /></td>
    <td align="center"><img src="media-docs/foto-android/Servarr.jpg" width="180" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_19_2026-03-16_20-24-21.jpg" width="180" /></td>
  </tr>
</table>

<table align="center">
  <tr>
    <td align="center"><img src="media-docs/foto-android/photo_1_2026-03-16_20-24-21.jpg" width="120" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_4_2026-03-16_20-24-21.jpg" width="120" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_13_2026-03-16_20-24-21.jpg" width="120" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_16_2026-03-16_20-24-21.jpg" width="120" /></td>
    <td align="center"><img src="media-docs/foto-android/plex.jpg" width="120" /></td>
  </tr>
  <tr>
    <td align="center"><sub>Portainer</sub></td>
    <td align="center"><sub>Beszel</sub></td>
    <td align="center"><sub>Nginx Proxy</sub></td>
    <td align="center"><sub>Pi-hole</sub></td>
    <td align="center"><sub>Plex</sub></td>
  </tr>
</table>

<details>
<summary><b>📸 View all Android screenshots</b></summary>
<br>

**Portainer**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/photo_1_2026-03-16_20-24-21.jpg" width="180" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_2_2026-03-16_20-24-21.jpg" width="180" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_3_2026-03-16_20-24-21.jpg" width="180" /></td>
  </tr>
</table>

**Beszel**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/photo_4_2026-03-16_20-24-21.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_5_2026-03-16_20-24-21.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_6_2026-03-16_20-24-21.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_7_2026-03-16_20-24-21.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_8_2026-03-16_20-24-21.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_9_2026-03-16_20-24-21.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_10_2026-03-16_20-24-21.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_11_2026-03-16_20-24-21.jpg" width="110" /></td>
  </tr>
</table>

**Nginx Proxy Manager / NPMplus · Pi-hole**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/photo_13_2026-03-16_20-24-21.jpg" width="145" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_14_2026-03-16_20-24-21.jpg" width="145" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_16_2026-03-16_20-24-21.jpg" width="145" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_17_2026-03-16_20-24-21.jpg" width="145" /></td>
  </tr>
</table>

**AdGuard Home · Healthchecks · PatchMon · Jellystat · Plex**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/adguard1.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/adguard2.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/healthcheck1.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/healthcheck2.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_1_2026-03-21_01-00-34.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_2_2026-03-21_01-00-34.jpg" width="110" /></td>
    <td align="center"><img src="media-docs/foto-android/plex.jpg" width="110" /></td>
  </tr>
</table>

**Bookmarks**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/photo_18_2026-03-16_20-24-21.jpg" width="180" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_19_2026-03-16_20-24-21.jpg" width="180" /></td>
  </tr>
</table>

</details>

---

## 📲 Install via AltStore / SideStore

You can install the iOS app directly on your iPhone without Xcode using **AltStore** or **SideStore**.

1. Copy the source URL:
   ```
   https://raw.githubusercontent.com/JohnnWi/homelab-project/main/apps.json
   ```
2. Open **AltStore** or **SideStore** on your device.
3. Go to **Sources** → **Add Source** and paste the URL above.
4. Find **Homelab** in the source and tap **Install**.

The app can then be refreshed and updated from the same source.

> **Note:** SideStore can re-sign the app automatically without needing a Mac every 7 days.

---

## 🛠️ Getting Started

### Build for iOS

1. Open `HomelabSwift/Homelab.xcodeproj` in Xcode 26+.
2. Select your development team under **Signing & Capabilities**.
3. Build and run on a real device or simulator.

### Build for Android

1. Import `HomelabAndroid` into Android Studio.
2. Let Gradle sync and resolve dependencies.
3. Run on a connected device or emulator.

---

## 💖 Project & Support

This project is a solo effort maintained by a university student. If you find it useful and want to support the project:

**EVM Wallet (Ethereum, BSC, Polygon, etc.)**  
`0x649641868e6876c2c1f04584a95679e01c1aaf0d`

---

## 📄 License

This project is licensed under the **Apache License 2.0**.

See [LICENSE](LICENSE) for the full text.
