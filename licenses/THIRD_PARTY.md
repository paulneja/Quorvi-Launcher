# Third-party components

| Component | License | Used for |
|---|---|---|
| `top.yukonga.miuix.kmp:miuix-blur-android` 0.9.4 (compose-miuix-ui) | Apache-2.0 | Glass backdrop, blur and highlights |
| `app/src/main/java/com/paulneja/quorvi/ui/design/liquid/` (Lens, Vibrancy, InnerShadow, CombinedBackdrop) | Apache-2.0, © 2026 compose-miuix-ui contributors; derived from Kyant0/AndroidLiquidGlass (Apache-2.0) | Refraction lens, vibrancy, inner shadow. Only change: package name |
| Geist and Geist Mono v1.7.2 (`app/src/main/res/font/geist_*.ttf`) | SIL OFL 1.1, © 2024 The Geist Project Authors (`licenses/Geist-OFL.txt`) | Typography across the UI |
| `com.github.termux.termux-app:terminal-emulator` and `terminal-view` v0.118.1 (JitPack) | Apache-2.0 (these two modules only; the rest of termux-app is GPLv3 and not used) | Terminal emulator and view |
| `com.hierynomus:sshj` 0.41.1 | Apache-2.0 | SSH client: hosts, Docker over SSH, console and the `ssh.exec` tool |
| `org.bouncycastle:bcprov-jdk18on` and `bcpkix-jdk18on` 1.86 | Bouncy Castle License (MIT style) | Cryptography for sshj and on-device Ed25519 keys |
| `org.apache.sshd:sshd-core` 2.20.0 (tests only) | Apache-2.0 | Embedded SSH server for connector tests; not shipped in the APK |
| termux-packages bootstrap and packages | Each package's own license (GPL, MIT, BSD, etc.) | Not bundled in the APK: downloaded from the official Termux mirrors, verified by sha256 and rewritten on the device |

License text: https://www.apache.org/licenses/LICENSE-2.0

SukiSU-Ultra (GPLv3) was only a visual reference; no code from it was copied.

The APK drops the dependencies' duplicate `META-INF/LICENSE.md` files, so the notices live in this file until there's an in-app licenses screen.
