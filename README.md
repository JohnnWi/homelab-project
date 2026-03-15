# 🏠 Homelab Dashboard

A premium, fully native dual-platform solution for monitoring and managing your personal Homelab ecosystem. This project features two distinct, purpose-built native applications sharing the same design soul but optimized for their respective platforms.

## Highlights

- **Tailscale support**: quickly open Tailscale, check VPN connectivity status, and secure access to homelab services when they are not directly reachable.
- **Bookmarks**: save your favorite internal tools and dashboards with categories, tags, and custom icons for faster access from both apps.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=JohnnWi/homelab-project&type=date&legend=top-left)](https://www.star-history.com/#JohnnWi/homelab-project&type=date&legend=top-left)

---

## 📱 iOS Version (Swift Native + Liquid Glass)
Developed with **Swift 6** and **SwiftUI**, utilizing the latest native iOS 26 technologies. The UI is built around the **Liquid Glass** design system, leveraging frosted glass effects and fluid animations for a high-end feel.

<div align="center">
  <img src="media-docs/foto-ios/IMG_8909.PNG" width="18%" />
  <img src="media-docs/foto-ios/IMG_8910.PNG" width="18%" />
  <img src="media-docs/foto-ios/IMG_8911.PNG" width="18%" />
  <img src="media-docs/foto-ios/IMG_8913.PNG" width="18%" />
  <img src="media-docs/foto-ios/IMG_8914.PNG" width="18%" />
  <br />
  <img src="media-docs/foto-ios/IMG_8915.PNG" width="18%" />
  <img src="media-docs/foto-ios/IMG_8916.PNG" width="18%" />
  <img src="media-docs/foto-ios/IMG_8917.jpeg" width="18%" />
  <img src="media-docs/foto-ios/IMG_8918.jpeg" width="18%" />
  <img src="media-docs/foto-ios/IMG_8919.PNG" width="18%" />
</div>

---

## 🤖 Android Version (Kotlin Native + Material Expressive 3)
Built with **Kotlin** and **Jetpack Compose**, following the **Material Expressive 3** design language. It focuses on dynamic color integration, haptic-rich interactions, and modern Android architecture.

<div align="center">
  <img src="media-docs/foto-android/photo_1_2026-02-23_16-50-09.jpg" width="18%" />
  <img src="media-docs/foto-android/photo_2_2026-02-23_16-50-09.jpg" width="18%" />
  <img src="media-docs/foto-android/photo_3_2026-02-23_16-50-09.jpg" width="18%" />
  <img src="media-docs/foto-android/photo_4_2026-02-23_16-50-09.jpg" width="18%" />
  <img src="media-docs/foto-android/photo_5_2026-02-23_16-50-09.jpg" width="18%" />
  <br />
  <img src="media-docs/foto-android/photo_6_2026-02-23_16-50-09.jpg" width="18%" />
  <img src="media-docs/foto-android/photo_7_2026-02-23_16-50-09.jpg" width="18%" />
  <img src="media-docs/foto-android/photo_8_2026-02-23_16-50-09.jpg" width="18%" />
  <img src="media-docs/foto-android/photo_9_2026-02-23_16-50-09.jpg" width="18%" />
</div>

---

## 👨‍🎓 Project & Author
This project is a solo endeavor developed by a single **University Student**. It was born from the need for a beautiful, unified way to manage home servers without sacrificing the performance and "feel" of native development.

### ☕ Support the Project
If you find this dashboard useful, consider supporting my studies with a donation. Every bit helps!

**EVM Wallet (Ethereum, BSC, Polygon, etc.):**
`0x649641868e6876c2c1f04584a95679e01c1aaf0d`

---

## 🚀 Getting Started

### 🍎 Build for iOS
1. **Open Xcode**: Open `HomelabSwift/Homelab.xcodeproj` in Xcode 16.0+.
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

### 🔖 Bookmarks
Keep all your most-used homelab links in one place with a native bookmarks feature that supports organization, quick access, and a cleaner daily workflow.

---

## 🌍 Multilingual
Both apps are fully localized and automatically adapt to your system language. Supported languages include:
**English 🇺🇸, Italian 🇮🇹, French 🇫🇷, Spanish 🇪🇸, German 🇩🇪.**

---

## 🛠 Tech Summary
- **iOS**: Swift 6, SwiftUI, Keychain (Security), URLSession (Networking), Glassmorphism UI.
- **Android**: Kotlin, Jetpack Compose, DataStore, Retrofit, Material Expressive 3 UI.

---

## 📜 Usage & License
- ✅ **Authorized**: Personal use, modifications for personal homelab environments, and code contributions/improvements.
- ❌ **NOT Authorized**: Use of this code in paid applications, apps with subscriptions, or any form of commercial redistribution.

The code is free to explore and improve for the community. Build something great for your home!
