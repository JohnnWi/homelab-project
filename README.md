# 🏠 Homelab Dashboard

[![Swift](https://img.shields.io/badge/Swift-6.0-orange.svg?logo=swift)](https://swift.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-iOS%2026%2B-blue.svg)](https://developer.apple.com/ios/)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Made with SwiftUI](https://img.shields.io/badge/Made%20with-SwiftUI-blue.svg?logo=swift)](https://developer.apple.com/xcode/swiftui/)
[![Made with Jetpack Compose](https://img.shields.io/badge/Made%20with-Jetpack%20Compose-green.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)

A premium, fully native dual-platform solution for monitoring and managing your personal Homelab ecosystem. This project features two distinct, purpose-built native applications sharing the same design soul but optimized for their respective platforms.

> **Disclaimer:** This is a **vibe-coding** project built for fun and personal use. It is provided as-is with no guarantees. The author assumes no responsibility for any issues, data loss, or damages resulting from the use of this software.

[![Star History Chart](https://api.star-history.com/svg?repos=JohnnWi/homelab-project&type=Date)](https://star-history.com/#JohnnWi/homelab-project&Date)

---

## Table of Contents

- [Highlights](#highlights)
- [iOS Version](#-ios-version-swift-native--liquid-glass)
- [Android Version](#-android-version-kotlin-native--material-expressive-3)
- [Project & Author](#-project--author)
- [Install via AltStore / SideStore](#-install-via-altstore--sidestore)
- [Getting Started](#-getting-started)
- [Integrated Services](#-integrated-services)
- [Usage & License](#-usage--license)

---

## Highlights

- **12 integrated services** — Portainer, Pi-hole, Beszel, Gitea, Nginx Proxy Manager, AdGuard DNS, Healthcheck, Patchmon, Jellystat, Plex, Tailscale, Bookmarks.
- **Multi-instance support** — Add multiple instances of the same service and switch between them seamlessly.
- **Cyberpunk mode** — Toggle a unique cyberpunk visual theme for your service cards.
- **Multilingual** — English, Italian, French, Spanish, German — auto-detected from your system language.
- **2 native apps** — Swift 6 + SwiftUI (iOS) and Kotlin + Jetpack Compose (Android).

---

## 📱 iOS Version (Swift Native + Liquid Glass)
Developed with **Swift 6** and **SwiftUI**, utilizing the latest native iOS 26 technologies. The UI is built around the **Liquid Glass** design system, leveraging frosted glass effects and fluid animations for a high-end feel.

<table align="center">
  <tr>
    <th>Dashboard</th>
    <th>Portainer</th>
    <th>Nginx Proxy</th>
    <th>Beszel</th>
    <th>Pi-hole</th>
  </tr>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/Dashboard.png" width="150" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9187.PNG" width="150" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9190.PNG" width="150" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9193.PNG" width="150" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9199.PNG" width="150" /></td>
  </tr>
</table>

<table align="center">
  <tr>
    <th>Gitea</th>
    <th>AdGuard DNS</th>
    <th>Healthcheck</th>
    <th>Patchmon</th>
    <th>Jellystat</th>
    <th>Plex</th>
    <th>Bookmarks</th>
  </tr>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9200.jpg" width="115" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9218.PNG" width="115" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9238.PNG" width="115" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9269.PNG" width="115" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9275.PNG" width="115" /></td>
    <td align="center"><img src="media-docs/foto-ios/plex.PNG" width="115" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9201.PNG" width="115" /></td>
  </tr>
</table>

<details>
<summary><b>View all iOS screenshots</b></summary>
<br>

**Portainer**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9187.PNG" width="180" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9188.PNG" width="180" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9189.PNG" width="180" /></td>
  </tr>
</table>

**Nginx Proxy Manager**
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

**AdGuard DNS**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9218.PNG" width="180" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9219.PNG" width="180" /></td>
  </tr>
</table>

**Healthcheck**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9238.PNG" width="180" /></td>
    <td align="center"><img src="media-docs/foto-ios/IMG_9239.PNG" width="180" /></td>
  </tr>
</table>

**Patchmon**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9269.PNG" width="180" /></td>
  </tr>
</table>

**Jellystat**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/IMG_9275.PNG" width="180" /></td>
  </tr>
</table>

**Plex**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-ios/plex.PNG" width="180" /></td>
  </tr>
</table>

</details>

---

## 🤖 Android Version (Kotlin Native + Material Expressive 3)
Built with **Kotlin** and **Jetpack Compose**, following the **Material Expressive 3** design language. It focuses on dynamic color integration, haptic-rich interactions, and modern Android architecture.

<table align="center">
  <tr>
    <th>Dashboard</th>
    <th>Portainer</th>
    <th>Beszel</th>
    <th>Gitea</th>
    <th>Nginx Proxy</th>
  </tr>
  <tr>
    <td align="center"><img src="media-docs/foto-android/Dashboard.jpg" width="150" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_1_2026-03-16_20-24-21.jpg" width="150" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_4_2026-03-16_20-24-21.jpg" width="150" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_12_2026-03-16_20-24-21.jpg" width="150" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_13_2026-03-16_20-24-21.jpg" width="150" /></td>
  </tr>
</table>

<table align="center">
  <tr>
    <th>Pi-hole</th>
    <th>AdGuard DNS</th>
    <th>Healthcheck</th>
    <th>Patchmon</th>
    <th>Jellystat</th>
    <th>Plex</th>
    <th>Bookmarks</th>
  </tr>
  <tr>
    <td align="center"><img src="media-docs/foto-android/photo_16_2026-03-16_20-24-21.jpg" width="115" /></td>
    <td align="center"><img src="media-docs/foto-android/adguard1.jpg" width="115" /></td>
    <td align="center"><img src="media-docs/foto-android/healthcheck1.jpg" width="115" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_1_2026-03-21_01-00-34.jpg" width="115" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_2_2026-03-21_01-00-34.jpg" width="115" /></td>
    <td align="center"><img src="media-docs/foto-android/plex.jpg" width="115" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_18_2026-03-16_20-24-21.jpg" width="115" /></td>
  </tr>
</table>

<details>
<summary><b>View all Android screenshots</b></summary>
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

**Nginx Proxy Manager**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/photo_13_2026-03-16_20-24-21.jpg" width="180" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_14_2026-03-16_20-24-21.jpg" width="180" /></td>
  </tr>
</table>

**Pi-hole**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/photo_16_2026-03-16_20-24-21.jpg" width="180" /></td>
    <td align="center"><img src="media-docs/foto-android/photo_17_2026-03-16_20-24-21.jpg" width="180" /></td>
  </tr>
</table>

**AdGuard DNS**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/adguard1.jpg" width="180" /></td>
    <td align="center"><img src="media-docs/foto-android/adguard2.jpg" width="180" /></td>
  </tr>
</table>

**Healthcheck**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/healthcheck1.jpg" width="180" /></td>
    <td align="center"><img src="media-docs/foto-android/healthcheck2.jpg" width="180" /></td>
  </tr>
</table>

**Patchmon**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/photo_1_2026-03-21_01-00-34.jpg" width="180" /></td>
  </tr>
</table>

**Jellystat**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/photo_2_2026-03-21_01-00-34.jpg" width="180" /></td>
  </tr>
</table>

**Plex**
<table>
  <tr>
    <td align="center"><img src="media-docs/foto-android/plex.jpg" width="180" /></td>
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

## 👨‍🎓 Project & Author
This project is a solo endeavor developed by a single **University Student**. It was born from the need for a beautiful, unified way to manage home servers without sacrificing the performance and "feel" of native development.

### ☕ Support the Project
If you find this dashboard useful, consider supporting my studies with a donation. Every bit helps!

**EVM Wallet (Ethereum, BSC, Polygon, etc.):**
`0x649641868e6876c2c1f04584a95679e01c1aaf0d`

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

The app will update automatically when new versions are released.

> **Note:** SideStore can re-sign the app automatically without needing a Mac every 7 days.

---

## 🚀 Getting Started

### 🍎 Build for iOS
1. **Open Xcode**: Open `HomelabSwift/Homelab.xcodeproj` in Xcode 26+.
2. **Signing**: Go to the project settings, select the **Homelab** target, and under **Signing & Capabilities**, select your development team.
3. **Run**: Connect your iPhone or select a simulator and press `Cmd + R` to build and run.

### 🤖 Build for Android
1. **Open Android Studio**: Import the `HomelabAndroid` folder.
2. **Setup**: Let Gradle sync and download all dependencies.
3. **Run**: Connect your Android device or start an emulator and press `Shift + F10`.

---

## ✨ Integrated Services

### 🐳 Portainer
Monitor your Docker environments in real-time. Peek into container statuses, CPU/Memory usage, and perform quick actions like Start, Stop, or Restart directly from your mobile device.

### 🛡️ Pi-hole
Keep your network clean. View real-time query statistics, see your total blocked domains, and toggle ad-blocking on the fly with customizable timers.

### 📊 Beszel
A lightweight, efficient system monitor. Track global CPU, Memory, and Disk usage across all your connected nodes with beautiful percentage-based visualizations.

### 🦊 Gitea
Manage your code natively. Browse repositories, view contribution heatmaps, read code files with full syntax highlighting, and keep track of your latest commits.

### 🔐 Tailscale
Integrated Tailscale support helps you securely reach your homelab from anywhere, with quick launch actions and connection status surfaced directly inside the app experience.

### 🔀 Nginx Proxy Manager
Manage your reverse proxy directly from your phone. Browse proxy hosts, redirection hosts, dead hosts, streams, access lists, and SSL certificates — all in one native interface.

### 🛡️ AdGuard DNS
Monitor and manage your AdGuard Home DNS server. View real-time query statistics, check filtering status, and control DNS protection directly from your phone.

### 💓 Healthcheck
Monitor the uptime and health of your services. View check statuses, response times, and get notified when services go down — all from a clean native interface.

### 🩹 Patchmon
Track software updates and patches across your infrastructure. Monitor version status, pending updates, and keep your homelab systems up to date from one place.

### 🍿 Jellystat
Monitor your Jellyfin media server usage. Track active streams, playback statistics, and library activity from a clean native interface.

### 🎬 Plex
Monitor your Plex Media Server. View libraries, recently added media, active sessions, and server status from a native mobile interface.

### 🔖 Bookmarks
Keep all your most-used homelab links in one place with a native bookmarks feature that supports organization, quick access, and a cleaner daily workflow.

---

## 📜 Usage & License
- ✅ **Authorized**: Personal use, modifications for personal homelab environments, and code contributions/improvements.
- ❌ **NOT Authorized**: Use of this code in paid applications, apps with subscriptions, or any form of commercial redistribution.

The code is free to explore and improve for the community. Build something great for your home!
