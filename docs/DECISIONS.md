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

## Policy and approvals

- Order: hard limits → per-category rule (WORKSPACE, SHELL, SSH, NETWORK, EXPORT, DEVICES, PUBLISH, UNUSUAL) shaped by the autonomy mode → grants → device conditions.
- MANUAL turns everything except reads into ASK; BYPASS turns ASK into ALLOW except PUBLISH.
- Approvals park the run in WAITING_USER. Approving creates a grant: ONCE (that resource, one use), RUN or HOUR. Rejecting tells the agent to find another way; "reject with a comment" tells it what to do instead.
- **Root always asks, even in BYPASS**, and the approval covers that exact command once. Unusual or dangerous commands (`rm -rf`, `mkfs`, `dd` to a disk, `curl | sh`, reboot, touching `/system`, `~/.ssh` or other apps' data) also ask, with "allow for this session" (6 h).
- WAIT_FOR_CONTEXT (battery or metered network) parks the run in WAITING_CAPABILITY and `:work` resumes it when the condition is met.
- Config lives in Room (read by `:work`); the audit log rotates at 5 000 rows.
- Configurable guardrails: "remove shell limits" and "reach the local network". "Free web" lets network tools run without asking in any mode.

## Connections

- SSH with sshj + full BouncyCastle registered at the lowest priority so it doesn't interfere with the Keystore. Ed25519 keys are generated on the phone and stored in the vault. Host keys are pinned by SHA256 fingerprint; first contact needs explicit confirmation and any change aborts.
- Docker over SSH with the CLI (`docker ps --format json`, logs, start/stop/restart); container ids validated with a strict regex.
- Plain HTTP is allowed by the platform config but restricted in code: `http://` only to loopback, private networks, ULA or 100.64/10 (Tailscale). Provider endpoints on the internet must be HTTPS.
- Hosts refresh every 2 min with Quorvi on screen and every 15 min in the background; HTTP monitors every `refreshSec` (min 15 s). GitHub has its own backoff.
- Monitors can be HTTP, HTTPS or TCP (`tcp://host:port`, measuring latency).
- Home Assistant over REST plus a `state_changed` WebSocket while on screen; cards never change state optimistically.
- GitLab shares the CI table with GitHub (`provider = gitlab`).
- Discord only through official webhooks; posting always asks.
- Saving to Drive without OAuth: `ACTION_CREATE_DOCUMENT` copies the exported file to the provider the user picks.

## Remote worker

- Go, stdlib only, HTTP/JSON with long-polling (simpler through NAT and Tailscale than gRPC for v1).
- Idempotency keys, append-only events with per-run sequences, one-time pairing codes; the worker stores only the token hash.
- On the phone the key is `sha256(runId|stepId|command)` so a resumed step reattaches to the same remote run.

## Memory

- FTS4 (the system SQLite doesn't guarantee FTS5). Only reviewed memories are used; agent proposals stay pending. Memories are injected with their source and a staleness warning.
- The chat coordinator gets pinned and relevant memories; with auto-memory on it saves what the user says about themselves (no secrets).

## Screen control and privileges

- Optional accessibility service in `:work`: semantic actions, tree reading with password fields hidden, and arbitration (a user touch preempts the agent). `android.act` rereads after acting.
- Optional privileged shells: root (`su`), Shizuku (direct API; only `newProcess` via reflection) and ADB over TCP. Each is enabled separately and verified when enabled and on every use; a backend that stops answering is marked degraded in memory instead of being switched off.
- Root levels read < control < exec < modify < experimental with a user-set ceiling; MODIFY uses `su --mount-master`.

## UI

- Calm default home: a large frameless clock and thumb-reachable tiles (AI agent, GitHub, Data, System) that grow into their panels.
- Pages switch with the top pill or by swiping; the bottom panel bar is optional.
- Real liquid glass refraction (rounded lens with chromatic dispersion and a specular rim) on top of the wallpaper; falls back to tint + grain + rim without a wallpaper bitmap.
- Surface styles: Glass, Material You (dynamic palette) or Material 3 (palette from the accent).
- Geist and Geist Mono (SIL OFL 1.1). Never weight 700.
- All visual customization lives in one serialized `Look` object; unknown keys are ignored, so new options need no migration.
- Start presets: Clean, Full, From scratch and Minimal; applying one backs up the previous layout.
- Settings is one screen with nine modules and live summaries; every shortcut opens the matching module.
- One long-press menu on the home (edit, widgets, glass space, wallpaper, settings).
- The ○ button returns to the page you were on; pressing it on home goes to the first page.
- Edit mode only selects; Android widgets resize freely from 1×1.
- Glass spaces are a backdrop layer: they only collide with each other, so apps and widgets sit on top.
- "Don't suggest" removes an app from suggestions without hiding it.
- Weather from Open-Meteo (no key, CC BY 4.0) with coarse location or a city.
- App import from Launcher3/Quickstep/Lawnchair `favorites` tables (root copy or file).
- English is the default language with a complete Spanish translation.
