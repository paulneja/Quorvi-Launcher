# Quorvi Launcher

An Android launcher that doubles as an operations console and a home for autonomous agents. Quorvi keeps the everyday launcher fast and offline-first, and puts **Nexo**, its built-in AI assistant, a real Unix terminal and your connections one gesture away.

## Features

- **Home:** liquid glass on your wallpaper, an automatic grid, pages, dock, folders, real Android widgets with free resizing, glass spaces, start presets and deep customization (glass, Material You or Material 3 surfaces, motion profiles, icons, clock).
- **Drawer and Command:** instant, accent-insensitive search for apps, settings, contacts, conversions and the web, with suggestions you can tune.
- **Nexo (chat):** streaming answers with tools, memory, attachments and image generation; an optional orchestra of specialists with their own model chains; `@meta`, `@investigar` and mentions.
- **Ultra:** repository and heavy work in a private workspace. Clone, edit, build and test; pull requests only when you ask, as an editable draft you approve.
- **Background jobs:** downloads, builds and installs run with no time limit; when one ends you get a notification and Nexo picks up the chat with the result. Other time limits are adjustable in Settings → Agents.
- **Terminal:** a bundled Unix runtime (Termux packages, apt) running inside the app.
- **Operations:** runs, steps, DAGs, logs and artifacts for every agent task, with approvals and a kill switch.
- **Connections:** GitHub (Actions, PRs, issues, pending items), SSH with pinned host keys, Docker, HTTP/HTTPS/TCP monitors, Home Assistant, GitLab, Discord webhooks and a remote Linux worker.
- **Capsules:** saved AI instructions or fixed-step automations with schedules and event triggers.
- **Device control:** opening apps, installing APKs agents build (through Shizuku) and optional root, Shizuku and ADB backends with granular levels. Agents never touch the screen.
- **Voice:** an optional system assistant that hears and speaks through your own AI provider.
- **Backup:** scene export plus password-sealed full backups (AES-256-GCM).
- English and Spanish.

## Building

Requirements: JDK 21, Android SDK with platform 37, and a `local.properties` file at the project root:

```properties
sdk.dir=/path/to/Android/Sdk
github.clientId=<your GitHub OAuth app client id>
```

The GitHub client ID is public (device flow, no secret). Without it, GitHub sign-in is disabled.

To sign release builds with your own key, add a `keystore.properties` (never committed):

```properties
storeFile=/path/to/keystore
storePassword=…
keyAlias=…
keyPassword=…
```

Without it, release builds use the debug key.

```bash
./gradlew assembleDebug          # app-debug.apk (quorvi.dbg)
./gradlew assembleRelease        # app-release.apk (quorvi.app)
./gradlew testDebugUnitTest lintDebug
```

Install and make it the home app:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd role add-role-holder android.app.role.HOME quorvi.dbg 0
```

The application id must stay exactly 10 characters long (see `gradle.properties`): the terminal's binaries carry the app's data path rewritten byte for byte.

## Project layout

```text
app/            the launcher, Nexo, connections and UI (Kotlin, Compose, Hilt, Room)
runtime/        the Unix runtime and terminal (runs in its own process)
benchmark/      Macrobenchmark journeys
remote-worker/  the Go worker for long tasks on a Linux machine
protocol/       the scene JSON schema
docs/           spec, decisions, known limitations and plan
licenses/       third-party licenses and attributions
```

## Docs

- [`docs/SPEC.md`](docs/SPEC.md): product and architecture specification
- [`docs/DECISIONS.md`](docs/DECISIONS.md): decisions and the reasons behind them
- [`docs/KNOWN_LIMITATIONS.md`](docs/KNOWN_LIMITATIONS.md): what's verified and what isn't
- [`docs/PLAN.md`](docs/PLAN.md): roadmap and status

## Third-party code

Liquid glass builds on `miuix-blur` and adapts the lens from Kyant0/AndroidLiquidGlass (both Apache-2.0). The runtime uses the Termux bootstrap and packages. Fonts: Geist and Geist Mono (SIL OFL 1.1). See `licenses/` or Settings → About → Licenses in the app.
