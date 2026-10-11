# Plan

The roadmap follows the spec's deliverables (P0–P7). Each phase ends with `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` passing, a walkthrough on the emulator and a release install on the test phone.

## Status

| Phase | Status |
|---|---|
| 0. Base | Done |
| 1. Canvas + glass design | Done (Macrobenchmark on the phone still pending) |
| 2. Command | Done |
| 3. GitHub + Operations | Done |
| 4. Agent + Copilot bridge + runtime | Done |
| 5. SSH / Docker / HTTP | Done (Docker actions on a throwaway container pending) |
| 6. Autonomy, memory, remote worker | Done (Home Assistant and GitLab without real instances) |
| 7. Android control + Control Studio | Done (phone-to-phone pairing pending) |
| 8. Advanced customization | Partial: capsules done; declarative scene editor and SystemUI experiment not started |
| Chat, Ultra, voice, orb, backup | Done |

## Phase 1. Canvas + glass design (spec P1 + P2)

**Goal:** a launcher worth using every day, with no network and no AI.

- **1A. Glass design system:** tokens, the liquid glass modifier with real refraction, fallbacks without a wallpaper bitmap, Material You / Material 3 surface styles.
- **1B. Canvas model:** Room schema with pages, containers, spans and locks; migrations tested with real old files.
- **1C. Canvas:** automatic grid from the screen size, pages, dock, clock.
- **1D. Drag and drop:** drawer → canvas, canvas → dock, onto an app to make a folder, across pages.
- **1E. Widgets:** picker with previews, binding, configuration, resizing.
- **1F. Menus, shortcuts and edit mode:** finger-anchored menus with real app shortcuts, pinned shortcuts, edit mode with selection and resize handles.
- **1G. Studio:** accent, glass, motion, icons, undo/redo, scene import/export.
- **1H. Performance:** backdrop recording only where needed, layers that don't invalidate on animation, jank measurements on the phone.

**Acceptance:** replaces the stock launcher for daily use offline; drag, folders, widgets and edit mode work; jank < 1 %; safe mode and migrations tested.

## Phase 2. Command (spec §2.2)

Instant search for apps, settings, contacts, conversions and the web, with the agent one tap away. Opens in under 100 ms and works offline; parser and ranking are unit tested.

## Phase 3. GitHub + Operations (spec §2.3, §9.3)

- **3A.** GitHub/Copilot bridge: device flow, token refresh, Copilot and Orbit Bridge.
- **3B.** Execution model: runs, steps, events, artifacts.
- **3C.** GitHub connector: watched repositories, runs, jobs, steps, logs, rerun, rate limits.
- **3D.** Operations: list, run detail with DAG, logs, artifacts.

**Acceptance:** a canvas card shows a real workflow and expands into the logs of the failing step; connector tests against a fake server (401/403/429/500, pagination, duplicate events).

## Phase 4. Agent + runtime (spec P3, §5, §6, §14.1–14.2)

- **4A. Unix runtime:** bootstrap pinned by sha256, prefix rewrite, apt hook for every `.deb`, `IShellService` with typed exec, kill and export.
- **4B. Agent engine:** classification, JSON plan with a DAG, tool loop, deterministic verification, checkpoints, kill switch, foreground service while running.

**Acceptance:** the photosynthesis document scenario finishes with a verified file in Downloads; a hung model is cancelled instantly; a killed process resumes from its checkpoint.

## Phase 5. Priority connections (spec P4)

SSH with pinned host keys and a console, Docker over SSH, HTTP/HTTPS/TCP monitors with JSONPath bindings and conditions.

**Acceptance:** a real SSH session from Operations; a Docker card with real data; an HTTP widget with binding and error handling.

## Phase 6. Autonomy, memory, remote worker (spec P5)

Policy engine with categories, modes, grants and conditions; in-run approvals; reviewed memory with FTS; the Go worker with pairing, idempotency and event sequences; Home Assistant; GitLab.

**Acceptance:** a long task finishes on the worker while the phone is offline and reconciles on return; policies hold under a negative test suite.

## Phase 7. Android control (spec P6)

Control Studio, in-memory notifications, media, Drive copies, Discord webhooks, accessibility screen control with arbitration, root levels and hooks, Shizuku, ADB over TCP, safe fallback when privileges are lost.

**Acceptance:** a controlled visual session the user can interrupt; revoking a permission degrades without breaking the home.

## Phase 8. Advanced customization (spec P7)

Action capsules (AI instructions with triggers, plus fixed-step JSON), a declarative scene editor and a SystemUI experiment.

**Acceptance:** extreme configurations import and restore without losing launcher recovery.

## Beyond the spec

- **Chat and Ultra:** streaming chat turns with every tool, orchestra with model chains, memory, attachments, image generation, Ultra repository work with user-approved PRs, `@meta`, `@investigar`, `/plan`.
- **Voice:** a system assistant using the user's own AI provider for speech in both directions, voice mode inside the chat.
- **The orb:** AGSL Pearl and Singularity with state reactions and the switch transition.
- **Backup:** password-sealed full backups.
