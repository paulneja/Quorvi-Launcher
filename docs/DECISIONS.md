# Decisions

Architecture and product decisions for Quorvi. Each entry says what was decided and why. The full spec is in `docs/SPEC.md`.

## Foundations

| # | Decision |
|---|---|
| D1 | Quorvi is a new codebase. The only shared piece is the GitHub/Copilot bridge (device-flow login, direct Copilot and Orbit Bridge). |
| D2 | One APK. The launcher, the terminal, the chat, Ultra and every feature ship as a single app with a single name. |
| D3 | GitHub OAuth app with device flow and expiring tokens (8 h + refresh token). The public client ID lives in `local.properties` → `github.clientId`. No client secret. |
| D4 | The Orbit Bridge is configured per user (URL + pairing code). None ships by default. |
| D5 | Liquid glass uses `miuix-blur` (Apache-2.0) plus a lens/vibrancy pass adapted from Kyant0/AndroidLiquidGlass (Apache-2.0). Attributions are in `licenses/`. |
| D6 | The glass samples the wallpaper chosen in Quorvi (also applied to the system) or, with "all files access", the current one. |

## Toolchain

Latest **stable** release of each piece; nothing alpha, beta or RC.

| Piece | Version |
|---|---|
| Gradle | 9.8.1 (sha256 pinned in the wrapper) |
| AGP | 9.4.1 |
| Kotlin | 2.4.21 |
| KSP | 2.3.12 |
| Compose BOM | 2026.09.00 |
| Hilt | 2.60.1 |
| Room | 2.8.5 (`androidx.room` plugin, schemas in `app/schemas/`) |
| JDK | toolchain 21, bytecode 17 |

AGP 9 notes: don't apply `org.jetbrains.kotlin.android` (AGP brings it), and `android.disallowKotlinSourceSets=false` is still needed for KSP.

## SDK and app id

- **compileSdk 37** (`androidx.core` 1.19 requires it), **minSdk 29**.
- **targetSdk 28 for the whole app.** It's the only way to execute binaries from app data (W^X since API 29), which the terminal needs. Consequences: photos through `READ_EXTERNAL_STORAGE`, `BLUETOOTH` without `maxSdkVersion`, no Play Store, and none of Android 17's local network restrictions while the target stays at 28.
- **The application id has exactly 10 characters** (`quorvi.app`, debug `quorvi.dbg`), the same length as `com.termux`, because the runtime's binaries are rewritten byte for byte. `Prefix` rejects any other length. Set in `gradle.properties` and applied with `androidComponents`.
- Longer ids were evaluated: rebuilding the Termux packages with Quorvi's prefix and a private apt repo is the correct path; proot is slower; LD_PRELOAD breaks Go and static binaries.

## Architecture

- `:app` plus `:runtime` (an Android library merged into the app) and `:benchmark`. Modules are only extracted for a measurable reason.
- Three processes: the launcher, `:work` (agent engine, connections, `CoreService` over AIDL) and `:shell` (the Unix runtime and terminal). Room uses multi-instance invalidation.
- Hilt for DI; Room is the source of truth for the home layout, runs, chats and memory; DataStore for settings.
- Icons: `LauncherActivityInfo.getBadgedIcon` on IO plus an `LruCache` (400), so the work-profile badge comes from the system.
- Local app search in memory: accent- and case-insensitive, ranked exact > prefix > word prefix > initials > contains. No network, no LLM.

## Home recovery

- `CrashGuard` stores the crash time with `commit()`, because the process dies right away.
- **2 crashes in 2 minutes → safe mode.** On API 36 Android shows the "Select a Home app" picker after the second launcher crash, so a threshold of 3 would never be reached.
- Safe mode is a minimal searchable app list with retry, reset layout, choose another launcher and Android settings. It doesn't touch the saved layout unless asked.

## Drawer gesture

- Follows the finger and can be interrupted.
- On release: a fling over 1200 px/s wins; otherwise the **direction of the last movement** decides (opening past 15 %, closing below 85 %). A fixed 50 % threshold closed the drawer on slow fingers.
- Inside the drawer, the list hands the gesture back to the drawer through nested scroll when it's at the top.
- With the drawer open every touch stays in the drawer. Its blurred background is a 3-pass box blur at quarter resolution drawn with high filtering.

## Glass and performance

- `miuix-blur` declares minSdk 33; Quorvi keeps minSdk 29 with `tools:overrideLibrary` and only uses it with `SDK_INT >= 33` and `RuntimeShader`. Below that, glass falls back to a tinted surface.
- Three backdrops: **chrome** (wallpaper only: dock, search, folder icons), **overlay** (wallpaper + canvas: folders, widget picker) and **top** (whole screen, recorded only while a menu or the background sheet is open; recording it always cost a frame per drawer movement).
- The canvas layer is recorded inside the `graphicsLayer` that animates alpha/scale so the animation doesn't invalidate it.
- Measured on an Armor 29 Ultra (release, 120 Hz): bare launcher 0.69 % jank / p99 10 ms; first glass version 1.89 % / 20 ms; with these optimizations 1.03–1.56 % / 11–14 ms. Target < 1 %.

## Runtime

- The official Termux bootstrap is pinned by sha256 per ABI. Every `.deb` is rewritten before dpkg through a `DPkg::Pre-Install-Pkgs` hook (unpack, move `data/data/com.termux` to the app's prefix, `sed`, symlinks, script permissions, repack).
- The apt mirror is pinned to `packages-cf.termux.dev`; random mirror selection kept hitting dead mirrors.
- `IShellService` (AIDL): typed JSON `exec` with `argv`, timeout and output limit; `kill`/`killAll` for the kill switch; `export` to MediaStore.Downloads that rereads and returns both sha256 values plus the magic bytes; `read`/`write`/`list` confined to the runtime home.
- The terminal has Termux-style extra keys and "mount sdcard" (`~/storage`).

## Agent engine

- Runs in `:work`. Pipeline: rule-based classification → JSON plan with a DAG (cycles rejected, default plan if the model fails) → steps with a tool-call loop (`step_done` / `step_blocked`) → deterministic verification → COMPLETED or FAILED. A document requires an exported, verified artifact; a CI diagnosis requires `report.md`.
- Run state transitions are compare-and-set (`UPDATE … WHERE state = :from`); heartbeat, tokens and checkpoint are per-field updates, so the kill switch and the engine never overwrite each other.
- Every HTTP call is cancelled with its coroutine (a child job calls `call.cancel()`); a blocked socket read ignores cancellation otherwise.
- Tool names on the wire use `_` (`fs_write`) because OpenAI and Copilot only accept `[a-zA-Z0-9_-]`; internally they keep the dot.
- `CoreService` becomes a `dataSync` foreground service with a "Stop all" notification only while agents run, holding a wake lock. Optional protection mode (Shizuku/root): Doze whitelist, background appops, "active" standby bucket and, with root, a high OOM priority for the agent process.
- User-visible engine messages come from string resources through `EngineTexts`.
