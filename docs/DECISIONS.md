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

## Privileges

- Agents never touch the screen: there is no accessibility service. They can list and open apps through the package manager, install APKs they built through Shizuku (`android.install_apk`, one approval each) and run privileged commands.
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

## Chat and Ultra

- Each message is a full agent turn: `Conversation` runs with the chat history, every tool, policies and streaming. Chats live in Room.
- Two modes: **Chat** and **Ultra** (repository and heavy work). A chat can offer to move to Ultra; the move is one way. Ultra has no step limit.
- Ultra workspace: `ultra/<chat>/repo` in the runtime. `repo_clone` passes the token in a one-off header so it never lands in `.git/config`. The agent can't commit, push, merge, rebase, reset hard, write through `gh` or change git identity.
- **Pull requests only exist when the user asks** ("PR", "pull request" or `/pr`). The draft lists created/modified/deleted files with per-file diffs; the user can edit title, body and commit message, leave files out or rewrite a file before approving. Quorvi then creates the tree, an author-less commit (so the user is the only author), the branch and the PR.
- **Authorship rule:** `Co-authored-by`, `Signed-off-by` for anyone not explicitly allowed, and AI attribution lines are rejected, both when the model proposes and right before sending.
- Configurable commit style, language, branch prefix, draft PRs and allowed co-authors.
- Orchestra (optional): a coordinator delegates to specialists (think, plan, research, code, write, image, review), each with its own model chain and fallbacks. Models are chosen in one place, Settings → AI → Models, as ordered lists per use.
- Modes and mentions: `@meta` works until `finish(done|blocked)`; `@investigar` researches several sources and questions the first findings; `/plan` in Ultra plans with read-only tools and hands over a plan to approve or revise.
- Reasoning effort and extended thinking where the provider supports them; an Anthropic provider (Messages API) alongside OpenAI-compatible and Responses providers.
- GitHub tools in chat and Ultra: profile, repos (private too), files, commits, issues, PRs, Actions, search and notifications. Commenting and creating issues always ask.
- Deleting a chat deletes its runs.

## Capsules

- Capsules are mainly saved AI instructions that run in their own chat; fixed-step JSON capsules remain an advanced option, compiled against the real tools before saving or running.
- Triggers: manual, every N minutes (min 15), daily at a time on chosen days, on charging, on joining a Wi-Fi network, on a notification from an app, on a failed CI run and on boot. Schedules use alarms; events are debounced to one run per capsule every 2 minutes.
- The AI can propose capsules; the user saves them from the card.

## Voice

- Off by default. When off, the voice button is hidden and the assistant activity, voice interaction service, session and recognition service are disabled in `PackageManager`.
- Quorvi can be the system's digital assistant. Speech is transcribed by the user's own AI provider (`/audio/transcriptions`, or a chat model that takes audio), never Google's recognizer.
- Answers come from the Nexo agent and are spoken by the provider's TTS (`/audio/speech` with style, or Gemini's native TTS), sentence by sentence as the answer streams. Playback speed is adjustable on the phone.
- Voice mode inside the chat keeps the conversation going and can be interrupted by talking over it (echo-cancelled capture).

## The orb

- Drawn with AGSL shaders: the **Pearl** in chat (liquid glass with an iridescent fluid inside) and the **Singularity** in Ultra (a dark core in a spinning plasma disk with lensing, corona and an orbiting swarm).
- Five states (idle, listening, thinking, speaking, working) plus the voice level; leans with the phone.
- **Horizon transition:** switching between chat and Ultra bends the real screen. The whole chat layer goes through a `RenderEffect` runtime shader centred on the orb: the interface spirals into it, the content swaps at the moment everything is a point, and the other mode is thrown back out of the horizon, lensed until it settles. Forward 1.15 s, back 0.95 s, the same easing both ways. The orb is drawn crisp above the warp (the Pearl collapses, a short flash, the Singularity forms); nothing is drawn over the text any more.
- **Living background:** each mode has a field tied to the orb's position. Chat: nacre light from the Pearl that ripples and winds while Nexo is busy. Ultra: star dust lensed by the Singularity into arcs, with tilt parallax. The two fields morph into each other during the transition. Without an orb on screen the light comes softly from behind the header.
- **The orb lights the interface:** suggestion cards, the composer and user bubbles carry a rim light on the edge that faces the orb, in the mode's tint, breathing slowly and following the orb through the transition.
- **Touch:** touching anywhere sends a ripple through the field (nacre in chat, a gravitational wave through the stars in Ultra), and lit edges brighten as the ripple passes. Tapping the orb squishes it like jelly and sends a stronger wave.
- **Messages:** user bubbles launch from the composer with an elastic stretch; when an answer finishes streaming a light sweep runs across it. Sending the first message folds the greeting and suggestions up into the orb.
- The Chat/Ultra thumb stretches like liquid toward the new side. Voice mode uses the same living field, reacting to the voice level while listening and speaking.
- Below Android 13 the transition is a fade and the field is the plain gradient.
- Falls back to a Canvas orb below Android 13 or if a shader fails to compile.

## Time limits and background jobs

- Limits are configurable in Settings → Agents → Time limits and stored in `agent_config` (key `limits`) so the `:work` process reads them live: terminal commands (`shell.exec`, Ultra `ws_run`, default 20 min, up to 180), SSH (default 10 min), model silence (default 120 s) and longest model reply (default 15 min). The per-tool watchdog is the larger command limit plus one minute; the runtime's hard cap is 181 min.
- Long work goes to background jobs (`job.start`, `job.status`, `job.stop`, `job.list`), which have no time limit. A job runs detached in the runtime (`setsid`, output to `~/jobs/<id>/out.log`, exit code to `~/jobs/<id>/exit`). Its state lives in a chat message of role `job` (no schema change), which is also the card the user sees and can stop.
- `job.start` goes through the same shell policy as `shell.exec` (category SHELL, git push refused, Ultra commit rules).
- `CoreService` polls running jobs every 10 s, stays in the foreground with a wake lock while any run, and when one ends posts a notification and submits a new turn in that chat (`RunSpec.jobId`) with the result and the end of the log. If the chat is busy (a run in progress, or waiting for the user for less than 30 min) the follow-up waits. A job the agent already saw finish with `job.status`, or that it stopped itself, gets no follow-up.
- Jobs are managed from the AI agent panel (the home widget shows live jobs and opens it): pause and resume (SIGSTOP/SIGCONT to the job's process group, silent), stop (TERM, then KILL after 2 s) and kill (KILL at once). Stopping or killing there gives the job's chat a turn saying the user ended it, and Nexo is told not to restart it. Stopping from the chat card is silent, since the user is already in that chat.
- Deleting a chat (or all chats) stops its jobs first, with no follow-up.

## Glass highlight

- The tilt-following specular highlight (miuix-blur `BloomStroke`) is only drawn on chrome glass (page pill, search bar, dock). On panels it left a thin vertical streak inside tiles that hold clipped children, so panels rely on the rim alone.

## AI switch

- AI is optional. A switch at the top of Settings (and on the first-run screen) turns it off; it lives in `agent_config` (key `ai`) so `:app` and `:work` both see it live. A missing key means on.
- Off hides every trace of Nexo: the AI and Ultra settings, policy, limits and runs, Copilot and AI providers, Discord, the agent indicator, agent search results and CI diagnosis, the chat (it closes itself if opened) and the Nexo page template. The agent panel, the agents block and the AI agent tile stay on the home screen with a note to turn AI on.
- Turning it off stops runs and background jobs (with an inline confirmation when something is running), skips AI capsule triggers and alarms, disables the voice components (so Quorvi stops being the system assistant) and gives up the microphone permission on Android 13+.
- Data is kept: chats, memory, keys and capsules come back unchanged when AI is turned back on. Fixed-step capsules, the terminal, GitHub, servers and monitors keep working, since they need no model. When AI returns and voice was on, Settings offers to pick Nexo as the assistant again (Android requires the user to choose it).
- The engine refuses `submit` while AI is off; `submitCapsule` (fixed steps) is untouched. A first run without AI seeds the terminal panel instead of the agent panel.

## Backup

- The scene export (layout and appearance) never contains secrets.
- The full backup is a `.qbk` file sealed with a password: PBKDF2-SHA256 (310 000 iterations) → AES-256-GCM. Parts: home and settings, AI (providers, API keys, models), chats and memory, capsules and agents, GitHub and servers.
- Import decrypts and validates, stages the files and applies them at the next start in `QuorviApp.onCreate`, before Hilt opens Room or DataStore, under a cross-process file lock. Credentials are re-encrypted with the new phone's key; only backups of the same database version are accepted.

## Release signing

- Release builds are signed with the owner's key from `keystore.properties` (gitignored: store file, passwords, alias). Without that file the release falls back to the debug key so the project still builds.

## Licenses

- Third-party notices live in `licenses/` (summary in `THIRD_PARTY.md`, full Apache-2.0, Bouncy Castle and OFL texts) and in the app under Settings → About → Licenses, read from `res/raw`.

## Open questions

1. Raising the target SDK requires running binaries another way (`linker64` + preload).
2. The remote worker protocol (spec §10.2).
