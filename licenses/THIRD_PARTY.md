# Third-party components

Everything below ships inside the APK unless noted. The same list, with the full license texts, is in the app under Settings → About → Licenses.

| Component | License | Used for |
|---|---|---|
| AndroidX (Core, Activity, Lifecycle, Compose UI/Foundation/Animation/Material 3, Navigation, Room, DataStore, Hilt Navigation, Profile Installer) | Apache-2.0, © The Android Open Source Project | UI, storage and app plumbing |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Apache-2.0, © JetBrains s.r.o. and contributors | Language runtime, concurrency, JSON |
| Dagger / Hilt 2.60 | Apache-2.0, © The Dagger Authors | Dependency injection |
| OkHttp 5.5 | Apache-2.0, © Square, Inc. | HTTP client |
| `top.yukonga.miuix.kmp:miuix-blur-android` 0.9.4 (compose-miuix-ui) | Apache-2.0 | Glass backdrop, blur and highlights |
| `app/src/main/java/com/paulneja/quorvi/ui/design/liquid/` (Lens, Vibrancy, InnerShadow, CombinedBackdrop) | Apache-2.0, © 2026 compose-miuix-ui contributors; derived from Kyant0/AndroidLiquidGlass (Apache-2.0) | Refraction lens, vibrancy, inner shadow. Only change: package name |
| `com.github.termux.termux-app:terminal-emulator` and `terminal-view` v0.118.1 | Apache-2.0 (these two modules only; the rest of termux-app is GPLv3 and not used) | Terminal emulator and view |
| `com.hierynomus:sshj` 0.41.1 | Apache-2.0 | SSH client |
| `org.bouncycastle:bcprov-jdk18on` and `bcpkix-jdk18on` 1.86 | Bouncy Castle License (MIT style), `licenses/BouncyCastle.txt` | Cryptography for SSH and on-device Ed25519 keys |
| Shizuku API and provider 13.1.5 (`dev.rikka.shizuku`) | Apache-2.0, © RikkaW | Privileged commands and app installs through Shizuku |
| Geist and Geist Mono v1.7.2 (`app/src/main/res/font/geist_*.ttf`) | SIL OFL 1.1, © 2024 The Geist Project Authors, `licenses/Geist-OFL.txt` | Typography across the UI |
| `org.apache.sshd:sshd-core` 2.20.0 (tests only) | Apache-2.0 | Embedded SSH server for connector tests; not shipped |
| termux-packages bootstrap and packages | Each package's own license (GPL, MIT, BSD, etc.) | Not bundled: downloaded on the device from the official Termux mirrors, verified by sha256 |

Full texts: `licenses/Apache-2.0.txt`, `licenses/BouncyCastle.txt`, `licenses/Geist-OFL.txt`.

SukiSU-Ultra (GPLv3) was only a visual reference; no code from it was copied.
