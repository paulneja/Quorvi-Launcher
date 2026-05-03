---
title: "Quorvi Launcher: product and architecture specification"
version: "1.0 (design baseline)"
date: "2026-05-02"
status: "Living document"
---

# Quorvi Launcher

## Product, experience and architecture specification

**Type:** a high-end Android launcher with autonomous agents, a Unix terminal, connections and device control.
**Platforms:** Android across vendors; Linux for remote workers.
**Initial use:** private, installed from an APK. Google Play is not assumed.
**Assistant:** the AI inside the launcher is called **Nexo**.

> **The idea in one sentence:** turn Android into a personal operations environment where the launcher is exceptionally polished and customizable, the AI carries out real work on its own, and connections, processes and devices can be watched and controlled from one interface.

**This document describes the target product. It does not claim that every capability already exists.** It separates requirements, design proposals, verified constraints and pending experiments. Anything that depends on the ROM, API level, vendor or privileges has to be detected at runtime and degrade gracefully.

**Priority order:** (1) launcher reliability and safety, (2) usability and performance, (3) task engine and terminal, (4) observability, (5) connections, (6) privileged capabilities, (7) advanced visual experiments. Complexity is added in layers without blocking the basics.

**Engineering rules:**

- Don't invent APIs or assume Android allows something because root or Shizuku exists. Read the docs and prototype.
- Don't add a dependency without checking maintenance, version, license and compatibility with the chosen target SDK.
- A test only counts as passed if it ran. Missing hardware or tools means `NOT VERIFIED` plus the procedure to validate it.
- Don't copy private scripts, keys or folders from other projects without reviewing their license, security and dependencies.
- Every delivery comes with code, relevant tests, traces or benchmarks where they matter, docs, and an exact account of what works and what doesn't.
- Risky operations stay behind feature flags and recovery paths. Root is never required to start the launcher.

# 1. Settled decisions

| Area | Decision |
|---|---|
| Scope | Launcher + agent platform + operations center; not a full Android replacement |
| Compatibility | Works across brands and ROMs; Ulefone is the main test device, not the only target |
| Distribution | Privately installable APK; the Play Store does not drive early decisions |
| Privileges | Optional levels: standard, special permissions/accessibility, ADB/Shizuku, granular root and experimental modules |
| Firmware | No required changes to `/system`, `boot`, vendor or SystemUI; optional, reversible modules only |
| Visual experience | High end, distinctive, expressive and fluid; deep customization and performance modes |
| UX | Original while keeping the patterns that make it easy to learn; not Niagara-style minimalism |
| Surfaces | Canvas, Command, Operations, Control Studio and Studio |
| Agents | Long goals, planning, subtasks, memory, tools, verification, shell and screen use |
| Autonomy | Manual, automatic and bypass, with configurable limits and a full audit trail |
| Shell | Two selectable environments: an installed Termux and Quorvi's own Unix runtime, visually integrated |
| Execution | Hybrid: local Android plus remote Linux workers, chosen automatically or by hand |
| First connections | GitHub, Docker, Home Assistant, SSH/Linux, Google Drive, Android devices, HTTP services, Discord and GitLab |
| Operations | GitHub Actions-style tracking: runs, jobs, steps, DAG, logs, terminal, timing, cost, artifacts and retries |
| Agent status | Configurable indicators (cards, compact island, notifications, Operations) |

## 1.1. Non-negotiables

1. **The launcher works without AI:** opening apps, widgets, folders, gestures and local settings works offline, with no account and no model configured.
2. **The UI never blocks:** no LLM, shell, connector or heavy worker runs on the main thread.
3. **Observable autonomy:** every agent action has an identity, a reason, an input, a result and a traceable origin.
4. **Capabilities are detected, not assumed:** never show an operation as available when the device or service can't do it.
5. **Opt-in escalation:** the user decides which capabilities to grant; root is never assumed.
6. **Predictable behavior:** deep customization without unexpectedly shuffling what the user pinned.
7. **Recovery:** a failing connector, experimental view or privileged service never leaves the user without a usable home screen.
8. **No fake signals:** never invent progress, network state, percentages, metrics, costs or passing tests.

## 1.2. Out of scope for the first usable product

- A universal, complete replacement for SystemUI or the lock screen.
- Recents/Quickstep integration identical to any vendor launcher.
- Visually driving any background app while the user freely uses the same screen.
- An embedded shell automatically compatible with every Termux package without rebuilds.
- Perpetual local execution that survives force-stop, power-off, OEM restrictions and an empty battery.
- A marketplace of untrusted extensions and scripts with unlimited privileges inside the UI process.

These are not permanent bans; they are future capabilities or separate experiments.

# 2. Experience: five spaces, one interface

## 2.1. Canvas: the home screen

Canvas replaces Android's usual home screen but keeps its essentials. It should feel familiar within minutes and allow much deeper customization over time.

**Blocks:** apps, shortcuts, folders, Android widgets, connection components, agent status cards, reusable actions, device indicators, search, a compact terminal, metric views, controls and optional free text.

**Layout:** configurable pages and workspaces; a flexible grid plus fine positioning; alignment rules, snap-to-grid, anchors and safe margins; orientation and size support; locked items; visual layers; responsive on tablets and foldables where it makes sense.

**Behavior:**

- Configurable dock, folders, optional badges, labels, icon packs, per-app icons, sizes and backgrounds.
- A complete app drawer with instant search, favorites, optional categories and hidden apps.
- Real Android widgets through `AppWidgetHost`, with configuration and binding handled correctly.
- Widgets and Quorvi cards side by side, with persistent state, placeholders and refresh policies.
- A gesture to open Command, quick access to Operations, a path into Control Studio and user-defined modes.
- Pinned positions are respected: predictive suggestions never reorder the home screen on their own.
- Keyboard, accessibility, locale and profile visibility limits are supported.

**Differentiator:** a compact component can expand through a continuous transition into a full view (for example a GitHub Actions card into the workflow detail) while keeping context and state.

## 2.2. Command: the universal bar

Command is the entry point to find and act, not just a chat.

**Implicit modes:** (a) open, (b) find, (c) run an action, (d) start an agent, (e) check status. Detection uses a low-latency pipeline: local index and rules first, an LLM only when it adds value.

Examples:

- `gh actions kernel` → relevant repositories/workflows and supported actions.
- `ssh server` → a terminal session or connection status.
- `make a report on photosynthesis and put it in Downloads` → a new background run.
- `check why this build failed` → a contextual run linked to the selected workflow.
- `focus mode` → the configured actions that are available.
- `yesterday's screenshots` → local search over what Quorvi can access.

**UX rules:** opening Command never waits for an LLM; app results show instantly from cache; potentially dangerous actions show their scope and context; the query can be edited without losing the previous context; private data is not sent to a remote provider without the matching policy.

## 2.3. Operations: the activity center

Operations mixes GitHub Actions, a process manager, a session manager and an observability panel. Its core entities are `Run`, `Task`, `Step`, `ToolCall`, `Process`, `Session`, `Artifact` and `Event`.

**List view:** active, queued, waiting for input, completed, failed, paused, cancelled; grouping by project/device/connection; filters; tags; search; favorites.

**Run view:**

- The original goal, state and owner (user, automation or agent).
- A DAG of subtasks: active, blocked by a dependency, succeeded, failed and skipped.
- A timeline with real timestamps, duration, model and executor.
- Structured logs and terminal output with scrolling, filtering and search.
- Screen captures/observations when relevant and authorized.
- Generated files with preview, checksum and export actions.
- Estimated or confirmed cost/compute/tokens, with their source.
- Pause new actions, cancel, retry an idempotent step, continue, adjust instructions, switch executor when safe.
- Which authorizations and privileged controls were used.

**Progress must be verifiable:** `3/5 steps done` is fine; a `67%` made up by the model is not. An ETA only appears with enough history behind it and is labeled as an estimate.

## 2.4. Control Studio: notifications, controls and media

A configurable panel with brightness, network shortcuts, Bluetooth, volume, the media player, devices and connectors, authorized notifications and agent status.

**Normally** it works as its own screen/panel and, with the overlay permission, as a surface over regular apps within Android's limits. Toggles that can't be guaranteed open the official panel or explain why. Notifications go through `NotificationListenerService` with specific consent. Media goes through authorized APIs such as `MediaSessionManager`.

**Intentional limit:** `TYPE_APPLICATION_OVERLAY` is drawn below critical windows (status bar, IME and others) and does not fully replace the system notification shade, Quick Settings or lock screen. Deep SystemUI integration is an optional, ROM-specific extension with recovery.

## 2.5. Studio: full customization

Two editing layers:

- **Visual:** move, resize, group, align, lock, theme and assign gestures/actions. Live preview, undo/redo, history and restore.
- **Advanced:** a versioned declarative format to import/export scenes, styles, animations, panels and command mappings. Validated schema, migrations and a safe mode when an import fails.

Grid/pages, dock, iconography, typography, radii, transparency, shadows, blur, backgrounds, effects, navigation, gestures, contextual items, the Operations and Control Studio layouts, agent indicators, rules and connection components can all be changed.

**Constraint:** an arbitrary layout never runs native code inside the UI process. Custom components use a declarative DSL/manifest and actions routed through the broker.

# 3. Visual design, motion and ergonomics

## 3.1. Visual direction

An original look with the clarity of a productivity tool, the expressiveness of cinematic animation and the organization of a powerful desktop. Avoid both extreme minimalism and futuristic clutter. Complex information appears on expansion; it isn't forced onto the home screen.

**Principles:** strong visual hierarchy; generous touch targets; material surfaces with depth; legible type; responsive brightness/color; coherent icons; animation with a visible cause; content always in front of effects.

**Configurable tokens:** `surface`, `surfaceElevated`, `scrim`, `accent`, `accentMuted`, `textPrimary`, `textSecondary`, `spacingScale`, `cornerScale`, `blurRadius`, `shadowElevation`, `translucency`, `density`, `motionScale`, `springStiffness`, `springDamping`, `hapticProfile`.

**Accessibility:** contrast on essential text, scalable fonts, TalkBack/semantics, reduce-motion support, touch targets in line with Android guidelines, nothing conveyed by color alone.

## 3.2. Motion engine

- Shared geometry and content transitions: a card can expand into a detail view and return to the same element.
- Interruptible, interactive gestures: the animation follows the finger's speed and position and can be reversed before it ends.
- Physical springs with limits, consistent damping and no gratuitous bounces.
- Focus, scroll and selection continuity when a surface expands or collapses.
- Blur, transparency, parallax, depth and background blur, all configurable and degradable.
- Haptics tied to semantic events, not to every frame.
- Telemetry cards don't animate continuously; recompositions and off-screen updates are kept to a minimum.
- Honest loading/waiting animations, considered empty states and error transitions.

**Profiles:** `Instant` (minimal effects), `Balanced`, `Cinematic` (preferred) and `Custom` per parameter, configurable per component and globally, with an automatic power-saving mode.

## 3.3. Measurable goals (hypotheses, not guarantees)

- Stable 60 FPS on capable hardware, 90/120 FPS depending on the panel, load and render budget.
- An initial jank target under 1% on the main journeys of the reference device, measured and tuned per profile.
- Visual response to core interactions in about 100 ms or less at the agreed test percentiles.
- Opening the drawer, switching pages and using the dock never wait on the network, the shell or inference.
- Frame time, p50/p95/p99 interaction latency, memory, CPU, battery and regressions are recorded.

A 120 Hz frame is about 8.33 ms. 120 FPS is not promised on every ROM or device. Macrobenchmark, JankStats, Perfetto, Baseline Profiles and physical devices are the tools.

## 3.4. Navigation and ergonomics

- One configurable main gesture for Command and another for Operations; conflicts with the back gesture and vendor navigation are resolved.
- Important actions reachable with one hand and with a keyboard.
- Agent information doesn't take over the screen unless asked for or something relevant happens.
- Cards show a compact state; expanding is optional. Any component can be hidden.
- Preferences per device, orientation, workspace and visual profile.
- Backend failures never show up as a frozen home screen.

# 4. Android architecture and modules

## 4.1. Processes and responsibilities

**UI / Home process:** `HOME`, Canvas, navigation, widgets, scenes and animations. Kept minimal and resilient. It doesn't run blocking commands or hold unnecessary sockets.

**Core process / task service:** state, events, connections and the local agent. It can live in its own Android process for fault isolation, talking over Binder/AIDL.

**Accessibility service:** a special service enabled by hand and supervised by a `ScreenControlBroker`; third-party modules never get unrestricted control.

**Runtime:** a dedicated process for the Unix terminal and its storage, inside the same app.

**Privileged bridge (optional):** Shizuku, ADB or root services, tightly scoped and authenticated, with dynamic detection of UID and privileges. No privileged code in the UI process.

**Remote worker:** an independent Linux daemon whose life cycle doesn't depend on Android.

**Rule:** modules talk through stable contracts. The UI observes `Flow<State>` and sends commands; it holds no references to processes or network plugin implementations.

## 4.2. Launcher basics: hard requirements

- Register correctly for `ACTION_MAIN` / `CATEGORY_HOME`; request `ROLE_HOME` where available.
- App discovery and launch through `LauncherApps`, `PackageManager` and the right APIs, handling package visibility.
- Per-profile apps; work profiles; Private Space from Android 15 according to the launcher's role/permission.
- Static, dynamic and pinned shortcuts where they apply.
- A complete widget host (`AppWidgetHost`): bind, configure, host view, resize, update, rebind and errors.
- Grid, dock, folders, pages, hidden apps, reordering, icon packs, launch intents, uninstall/info.
- Robust handling of package changes, profile changes, reinstalls and restores.
- Static wallpapers and live wallpapers where supported; insets, bars, orientation and edge-to-edge.
- An efficient app drawer (lazy loading / cache) and a local search index.
- Home restores after a crash; access to an alternative launcher and to Settings is kept.
- Accessibility, languages, external keyboards, font size and landscape.

**Note:** Recents/Quickstep and some system gestures belong to privileged/OEM components. The experience isn't designed around replacing them without a ROM prototype.

## 4.3. Logical architecture

```mermaid
flowchart TB
  U[User] --> UI[Android Presentation]
  UI --> AC[Application Core]
  AC --> EV[Event Bus and Persistence]
  AC --> AG[Agent Orchestrator]
  AC --> CH[Connection Hub]
  AC --> CB[Capability Broker]
  AG --> TR[Tool Router]
  TR --> SH[Shell Session Manager]
  TR --> CH
  TR --> CB
  TR --> RW[Remote Worker Client]
  SH --> TERM[Termux Adapter]
  SH --> OWN[Unix Runtime]
  RW --> HOST[Linux Worker]
  EV --> UI
  HOST --> EV
```

The diagram is conceptual: RPC, local state, network events and callbacks are distinct, and an arrow never means shared memory across UIDs.

## 4.4. Architecture patterns

- `feature-first` on Android; `core` modules stay small.
- Unidirectional state flow for screens; local storage is the source of truth for runs, settings and memory.
- `StateFlow`/`Flow` for internal streams, Room for persistence; versioned IPC between processes.
- Idempotent requests where possible, with deduplication and correlation keys.
- Feature flags for work in progress, the SystemUI panel, root and devices.
- Every tool backend returns a typed result: `SUCCESS`, `UNSUPPORTED`, `UNAUTHORIZED`, `TIMED_OUT`, `FAILED`, `CANCELLED` or `INTERRUPTED`.

# 5. Shell manager: a terminal for users and agents

## 5.1. Functional requirements

A terminal isn't a text field with the occasional process. Quorvi needs real session infrastructure, with persistent IDs, multiple processes, PTYs where needed and both human and automated access.

- Create an interactive terminal; open it as a panel, tab or expandable surface.
- Run non-interactive commands capturing `stdout`, `stderr`, exit code, cwd and duration.
- `spawn`, `attach`, `writeInput`, `resizePty`, `signal`, `terminate`.
- Streaming with ANSI sequences, bounded scrollback, encoding/Unicode and backpressure.
- Shell profiles, cwd, env and per-job variables.
- Command history and bookmarks; secrets are never stored in plain text in history.
- Bounded logs and output: explicit truncation spooled to disk with artifact references, never silent cuts.
- Human actions on a running session: follow, take over when safe, stop, copy, export.
- An agent can work in a shell without stealing focus; its output is visible from Operations.

## 5.2. Backend A: an installed Termux

A first realistic implementation: a `RUN_COMMAND` adapter running in the Termux app's context. It needs `com.termux.permission.RUN_COMMAND`, `allow-external-apps=true`, package visibility and consent. It returns results where supported; it doesn't assume full PTY/interactive session control through the intent.

**Required discovery:** compatibility per Termux version, session mode, intents, callbacks, permission scope, how long a process can persist and result size limits.

**File access:** the Termux shell runs with Termux's UID and permissions. Quorvi never reads `/data/data/com.termux` directly as if both packages shared a UID; it uses IPC, SAF, explicit exchange or its own bridge.

## 5.3. Backend B: Quorvi's Unix runtime

An embedded terminal with its own runtime. Termux packages embed `com.termux` paths and Android restricts running binaries from writable data areas, so this is built on a feasibility spike, not on copying an existing `$PREFIX`.

**Options compared:**

1. A runtime with a prefix and packages rebuilt or relocated for its package name.
2. Binaries installed in an executable location compatible with the target API, without relying on an exception that isn't guaranteed.
3. A remote isolated environment when local execution isn't viable for certain tools.
4. The Termux adapter as a fallback.

**Not allowed:** lowering the main launcher's target SDK across the board to enable an unsafe shortcut without weighing the consequences and its lifetime.

**Termux reuse:** review the GPLv3 license of the main repository and the licenses/exceptions of `terminal-view` and `terminal-emulator`, and keep an attributions file before distributing derivatives, even privately.

## 5.4. Session model

```text
ShellBackend = TERMUX_EXISTING | QUORVI_RUNTIME | SSH_REMOTE | WORKER_CONTAINER | ADB_SHELL | ROOT_SHELL
ShellSession(id, backend, ownerRunId?, ownerUserId?, profileId,
             deviceId, cwd, envRefs, pty, createdAt, lastSeenAt,
             state, processGroupRef, transcriptRef, limits)
CommandExecution(id, sessionId?, argv, cwd, envRefs, timeout,
                 stdinRef?, stdoutRef, stderrRef, exitCode?, state)
```

Secrets are never serialized into `envRefs`; they reference vault entries. Sensitive arguments aren't logged. Typed `argv[]` is preferred when a shell isn't needed; shell strings only when that semantics is asked for.

## 5.5. File export and `Downloads`

The agent writes to its own workspace first. Format, integrity and size are checked; then the file is exported through `MediaStore.Downloads` or SAF, respecting permissions, scopes and Android versions. Exporting a document the app created never requires full storage access.

`Artifact(id, sourceRun, mime, size, sha256, localUri, exportedUri?, createdAt, provenance, verified)` is recorded. Final names resolve collisions; existing files are never overwritten without an explicit policy.

# 6. Agent engine: from instructions to results

## 6.1. How it works

The AI is not the operating system. The model proposes plans and typed calls; the `Tool Router` and the `Policy Engine` check and dispatch actions. System results are the source of truth, not an LLM saying "done".

**Pipeline:**

1. Receive the instruction and authorized context.
2. Classify the goal, sensitivity, likely tools and executors.
3. Create a persistent run with its budget, policy and goal.
4. Plan tasks and dependencies against real capabilities.
5. Run steps and tool calls with concurrency control, timeouts and cancellation.
6. Observe results; update events, logs, artifacts and memory.
7. Verify deliverables with deterministic checks where they exist.
8. Complete, report, ask for help or fail explicitly; never fake success.

## 6.2. State model

```text
RunState = QUEUED | PLANNING | RUNNING | WAITING_CAPABILITY |
           WAITING_USER | PAUSING | PAUSED | RETRYING | VERIFYING |
           COMPLETED | FAILED | CANCELLED | INTERRUPTED
StepState = PENDING | READY | RUNNING | SUCCEEDED | SKIPPED |
            BLOCKED | FAILED | CANCELLED | INTERRUPTED
```

Transitions go through a state machine. Events are append-only with incremental snapshots, protected against duplicates and out-of-order updates. Disconnected executors use correlated IDs and per-run sequences.

**DAG:** independent subtasks can run in parallel; explicit dependencies keep a step from starting before its inputs exist. Fan-out is bounded by process/model/network limits. Fan-in checks results before continuing.

## 6.3. First tools

| Namespace | Base operations | Notes |
|---|---|---|
| `shell` | `exec`, `spawn`, `attach`, `signal`, `list` | Separate backends and permissions |
| `fs` | `list`, `read`, `write`, `stat`, `search`, `export` | Workspace/SAF; no arbitrary paths outside scopes |
| `web` | `search`, `fetch`, `open` | Untrusted external data; provenance kept |
| `android` | `listApps`, `launch`, `inspectUi`, `screenshot`, `act`, `readNotifications` | Needs the matching capabilities |
| `connections` | `query`, `subscribe`, `execute` | Connector contracts and scopes |
| `task` | `spawn`, `status`, `pause`, `cancel`, `artifact` | Bounded subtasks |
| `memory` | `search`, `propose`, `save`, `forget` | Scopes, confidence, review |
| `remote` | `selectWorker`, `run`, `transfer` | Encrypted connections and consent |

Each tool has a `name`, `version`, JSON Schema for its arguments, required capabilities, risk, estimated cost and idempotency semantics. Raw `su` is never exposed as an all-powerful function by default.

## 6.4. Verifying results

Three separate things: `model_output`, `execution_result` and `verified_artifact`.

- File: exists, expected size, consistent MIME type, opens/renders where relevant; SHA-256.
- Script: exit code and its tests; if tests didn't run, that limitation is recorded.
- Research: sources, dates, quotes and a clear line between facts and inferences.
- Android operation: read the resulting state or observe it; never assume a tap worked.
- External service: check the API response and the final state where possible.

An agent never reports `COMPLETED` just for producing text or launching a command that is still running.

## 6.5. Recovery, checkpoints, idempotency

- The plan and its dependencies are persisted before destructive or long actions.
- `attempt`, `executorId`, `startedAt`, `heartbeatAt`, `lastCommittedStep` and `checkpointRef` are recorded.
- On reconnect, ask whether the remote action already ran before retrying.
- Non-idempotent operations (sending a message, deleting files, restarting a container) need specific deduplication or a fresh confirmation.
- Cancellation: mark `CANCELLING`, stop new tool calls and signal compatible processes. Rollback of effects that already happened is not promised.
- Retries with backoff and per-cause limits; errors are classified as permanent, transient or permission-related.
- When the agent lacks a capability or context, it moves to `WAITING_USER` with a concrete question.

## 6.6. Model planner

An extensible provider interface: several models, compatible endpoints, routes, quotas and costs. Tool calls, text, vision and streaming where they apply. No dependence on fixed model names or a single provider.

**Default policy:** rules/local index to open apps; a light model to classify and pick tools; a strong model to plan and code; a multimodal model for the screen; deterministic verifiers first. Success rate, tokens, latency, cost, timeouts and format errors are measured.

**Credentials:** per-provider API keys live in a vault; the agent gets a `providerProfileId`, never the token.

# 7. Memory and context

## 7.1. Kinds of memory

| Kind | Scope | Example |
|---|---|---|
| Preferences | User / workspace | Prefer not to be interrupted during recoverable tasks |
| Episodic | Run / project | A build failed; fix verified in a given run |
| Procedural | Tool / project | Proven steps to generate and validate a PDF |
| Projects | Workspace / device | Repository paths, build commands, services |
| Contextual | Session / goal | Current screen, quoted files and selection |
| External facts | Source / access | Data from an API or the web, with a timestamp |

## 7.2. Memory policy

- SQLite/Room as the structured base; FTS first; embeddings/vector search only if they measurably improve recall.
- Every entry records `source`, `scope`, `createdAt`, `expiresAt?`, `confidence`, `sensitivity`, `verificationState`.
- Observed facts, explicit preferences, hypotheses and LLM summaries are kept apart.
- Isolation between Android profiles, accounts, repositories and agents by scope.
- A UI to view, edit, pin, export and delete memories.
- Configurable retention for transcripts, captures, logs, snapshots and artifacts.
- The AI never saves passwords, tokens, OTPs or credentials it comes across.
- Old procedures are checked against current tool versions and invariants before reuse.

## 7.3. Context retrieval

Order: the request itself → the selected task's references → local project data → tool results → relevant memories → the model's general knowledge. Provenance goes into structured prompts. The full history isn't dragged into every call.

# 8. Autonomy, consent and action safety

## 8.1. Modes

| Mode | Behavior | Limit |
|---|---|---|
| `MANUAL` | Plan and operations are reviewed and approved | Nothing unapproved runs |
| `AUTO` | Runs pre-authorized capabilities; escalates when needed | Denies what exceeds its profile |
| `BYPASS` | Maximum configured autonomy, fewer repeated prompts | Not full access by default, never bypasses OS controls |

Configuration is multi-dimensional, not a single power switch.

## 8.2. Policy engine dimensions

- Subjects: user, agent, run, process, device, connector, account and worker.
- Resources: workspace, folder, repository, endpoint, device, UI, Android profile.
- Actions: read, write, delete, execute, network, publish, change settings, use root, control the screen.
- Conditions: time, network, Wi-Fi, battery level, free screen, prior confirmation, cost limit, frequency.
- Decisions: `ALLOW`, `DENY`, `REQUIRE_CONFIRMATION`, `WAIT_FOR_CONTEXT`, with a readable, audited reason.
- Separate root permissions (read, control, privileged shell, persistent changes, experimental).
- New actions can be revoked immediately; a process that already started and can't be reverted is reported as such.

**Example:** allow running scripts and writing inside a development container without asking; require specific permission to delete `/home`, push, restart Docker or touch the phone's partitions.

## 8.3. Threat model in short

Attacks considered: prompt injection from web pages, logs or READMEs; token exfiltration; shell injection through argument concatenation; extensions feeding false data; Docker socket abuse; SSRF into the local network; accessibility manipulation; spoofed remote services; malicious dependencies; spying through logs; sync and idempotency bugs.

Minimum mitigations: credentials kept out of prompts, per-connector restrictions, typed commands, output/path validation, configurable allowlists, real sandboxing where possible, mutual authentication, resource limits, protected local logs, secret scrubbing and per-operation permission review. A policy enforced inside the same UID as an arbitrary shell **is not a security sandbox**.

## 8.4. A busy screen and a user using it

A `Screen Arbitration Manager` reserves visual interaction only when a session is granted. Active human interaction pauses new agent gestures. In automatic mode the agent prefers shell/API and waits before conflicting visual actions; there is no hidden second virtual screen where everything just works.

**Kill switch:** always visible, it stops new actions and asks compatible processes to cancel. It works even when the LLM is stuck.

# 9. Connection hub and nine first integrations

## 9.1. Common connector model

A connector is more than an API client. It includes:

1. **Resources:** what it can query (repository, workflow, container, device, sensor, file, allowed message).
2. **Queries:** read views with pagination, filters, rate limiting and caching.
3. **Actions:** intentional typed operations (rerun, open a shell, trigger a scene…).
4. **Subscriptions:** real-time events over WebSocket/webhooks or throttled polling.
5. **Widgets:** declarative components and adaptive layouts.
6. **Tools:** actions offered to agents through the broker.
7. **Permissions:** OAuth scopes, tokens, credentials and per-operation capability.
8. **Health:** authentication, latency, API availability, stale data, rate limits and errors.

**Principles:** icons and panels render from shared data models without erasing provider specifics; provider logic isn't duplicated across UI, agent and worker. Tokens live in a secure store or on the authenticated worker, wherever the work runs.

## 9.2. Suggested Kotlin contract

```kotlin
interface QuorviConnector {
    val manifest: ConnectorManifest
    suspend fun connect(request: ConnectionRequest): ConnectionResult
    suspend fun healthCheck(accountId: String): HealthResult
    suspend fun query(request: ConnectorQuery): ConnectorResult
    suspend fun execute(request: ConnectorAction): ActionResult
    fun events(subscription: ConnectorSubscription): Flow<ConnectorEvent>
}

data class ConnectorManifest(
    val id: String,
    val version: Int,
    val capabilities: Set<String>,
    val authKinds: Set<String>,
    val actions: List<ToolDescriptor>,
    val widgetDescriptors: List<WidgetDescriptor>
)
```

The contract supports semantic versioning, structured errors, JSON Schema parameters and artifact references; it doesn't assume every provider streams.

## 9.3. Per-connector notes

### GitHub

**MVP:** connect the account securely; list selected repositories; PR/issue status; latest GitHub Actions runs; jobs, steps, logs and artifacts; open the original URL; live run tracking with adaptive polling. `workflow_dispatch` and reruns only with the right scopes and confirmations per policy.

**Panels:** `Repo Health`, `Recent Workflows`, `Failing Jobs`, `Pending PR Reviews`, `Release Progress`.

**Agent:** given a failed run, fetch what matters, analyze the error and work in a local/remote workspace without touching the repository by default. Branches, PRs and pushes only with explicit permission.

### GitLab

**MVP:** projects, pipelines, jobs, merge requests, logs and artifacts; filters by branch/state; retry/cancel/trigger when the token allows.

**Unification:** `CIExecution` as an entity shared with GitHub, with `providerSpecific` for each platform's own states.

### Docker

**MVP:** host profiles over SSH/TLS; containers, state, uptime, resources, logs and start/stop/restart on authorized hosts; optional Docker Compose.

**Safety:** daemon or socket access counts as administrative access to the host. Docker Engine is never exposed over TCP without TLS authentication, and a root socket never lives in the Android UI process.

### SSH / Linux

**MVP:** several hosts, secure authentication, a PTY terminal, command execution, file transfer, connectivity state, processes/metrics, systemd services where present. Host key verification is kept; fingerprint changes are never accepted silently.

**UI:** a small host health card, process details, folder shortcuts and sessions that can be pinned to the home screen. Telemetry sampled at a configurable rate.

### Home Assistant

**MVP:** instance URL; a secure token; devices, entities, states, sensors, lights and scenes; change events over WebSocket where it fits; actions on authorized entities.

**UI:** direct touch control of devices, rooms and scenes; a clear stale state when the link drops.

### Google Drive

**MVP:** OAuth/file picker, search, listing, opening, download/export and controlled upload to allowed places. Start with the smallest scope (for example `drive.file` where it's enough). Changes always depend on scopes and the agent mode.

**Agent:** the tool receives authorized objects/IDs; account permissions aren't used to walk all of Drive when the task needs one file.

### Discord

**MVP:** identity and allowed integrations through OAuth2/bots as appropriate; the servers/channels and events the integration can access; panels limited by permissions and intents.

**Constraint:** no self-bots and no automating a personal account with client tokens. Anything the official API doesn't allow is marked unsupported.

### Android devices

**MVP:** pairing with another Quorvi install; name, version, battery, connectivity, capabilities, presence and authorized runs. Each remote Android applies its own policy engine and can refuse actions.

**Does not mean:** ADB open to the network or an unauthenticated root service. The channel is authenticated and pairing is revocable.

### Custom HTTP services

**MVP:** a visual API editor: base URL, method, headers, authentication, body, JSONPath/JMESPath or an equivalent selector, refresh rate, output schema and actions. Request/response preview with secrets redacted.

**Widgets:** text, counter, traffic light, time series, button, toggle and list, with reactive data binding and error handling.

**Safety:** per-domain allowlists, control over loopback/LAN/metadata addresses, response limits, timeouts, redirects and TLS validation. The AI can propose a connector, but it's validated before it's enabled.

## 9.4. Order of depth

1. GitHub + SSH/Linux: the first real end-to-end CI and agent journey.
2. Docker + HTTP services: telemetry and generic automation.
3. Home Assistant + GitLab: interoperability and sync.
4. Google Drive + remote Android: harder authentication and transfers.
5. Discord: only the officially allowed capabilities.

The order drops none of the nine; it avoids building nine shallow clients without one useful integrated flow.
