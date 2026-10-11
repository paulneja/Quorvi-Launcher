# Known limitations

Anything not listed here as verified should be treated as unverified. Test devices: the `deck_test` emulator (API 36, 1080×2400, host GPU) and an Ulefone Armor 29 Ultra (arm64, Android 15).

## Design limits

- **The policy engine is not a sandbox.** It runs in the same UID as an arbitrary shell; it blocks `git push`, `gh` writes, `su` and `rm` outside the workspace, but a script could get around it (spec §8.3).
- **targetSdk 28.** Required to run the terminal's binaries from app data. No Play Store distribution.
- **The application id must be exactly 10 characters** because the runtime's binaries are rewritten byte for byte.
- **The SSH console is line based** (`TERM=dumb`): full-screen programs like `vim` or `htop` don't render. The terminal for the local runtime is a full emulator.
- **The remote worker runs `sh -c` as its user.** It is not a sandbox either (see its README).
- **Agent-proposed memories stay unused until approved**, on purpose, as a defense against injection.
- **Notifications live only in memory.** They're never written to disk or shown to agents.
- **DAG steps run in topological order, one at a time**; up to two runs in parallel.
- **The full backup only accepts backups from the same database version.**
- **Voice interruption depends on the phone's echo cancellation.** If it isn't good enough, Nexo can cut itself off with its own voice; the option can be turned off in Settings → AI → Voice assistant.

## Verified

- Quorvi takes the HOME role; the layout survives `am force-stop`; two crashes in a row open safe mode and "retry" returns to the normal home with the layout intact.
- Drawer (accent-insensitive search, A–Z index, most used, "don't suggest"), folders, drag from drawer to canvas, widget picker with real widgets and free resizing, edit mode, presets, glass spaces.
- Work profile tabs and pause, and Private Space lock/unlock, with users created by `pm create-user`.
- The runtime installs the official bootstrap (verified by sha256 and rewritten to the app's prefix) on x86_64 and aarch64; bash, `pkg install python git`, Python and `git clone` work.
- End-to-end agent run (spec §14.1) with a local OpenAI-compatible test server: a 3-step plan, real `fs.write`/`shell.exec`/`artifact.export`, the file in Downloads with a matching sha256.
- Kill switch with a hung model: CANCELLED in under 300 ms. Process death mid-run leaves the run INTERRUPTED and "Resume" continues from the checkpoint.
- SSH against a real OpenSSH server: fingerprint matches `ssh-keygen -lf`, trust pinning, host health, Docker container list and an interactive console.
- The Go worker: pairing, a long task finishing while the app was killed, and the run reattaching on resume.
- Approval cards: "allow once" runs the command exactly once and is audited.
- Capsules: a 3-step capsule ran without a model; an AI capsule ran from the charging trigger in its own chat.
- Accessibility screen control: observe → act → verify loop on Settings.
- Chat → Ultra offer, `@meta`, `/plan`, capsule proposals, PR draft card (grouping, per-file diff, leaving files out, editing text and the authorship check).
- The voice pipeline with a test server: assistant gesture, upload to `/audio/transcriptions`, spoken answer requested sentence by sentence.
- The orb in every state and the chat → Ultra transition; 60 fps in the bench screen on the emulator.
- Full encrypted backup: export, wrong-password rejection and import into a fresh install (chats, messages, providers, keys, home and capsules).
- English and Spanish UI.
- Armor 29 Ultra: install, 120 Hz, jank 1.0–1.6 % in drawer journeys.

## Not verified

**Launcher**
- Pin requests from other apps (Chrome "add to home"): the activity exists but wasn't triggered.
- Icon packs with a real pack installed (the parser is tested).
- Dragging to another page across the edge.
- Predictive back (`adb input` can't produce the gesture).
- Macrobenchmark on the phone (refused to run on low battery).
- The tinted-glass icon style with icon packs.

**AI and agents**
- Real model providers end to end on the phone (Copilot, the bridge and API-key providers); the engine is covered by deterministic tests and the test server.
- Spec §14.2 with a real failed GitHub run.
- Real `web.search`/`web.fetch` on the device.
- A restored API key used against a real provider; chat attachments and wallpaper inside a full backup.
- Editing a PR file with a real clone and creating a PR with excluded files on GitHub.

**Voice**
- Real OpenAI (`gpt-4o-mini-transcribe`, `gpt-4o-mini-tts`) and Gemini native TTS; the chat `input_audio` fallback; audible playback and the real microphone.
- Holding home/power on the phone, and other apps using Quorvi's `RecognitionService`.

**Connections**
- Docker start/stop/restart on real containers; the Docker card on the canvas.
- SSH from the phone to a LAN server; `ssh.exec` from an agent with a real model.
- Home Assistant and GitLab against real instances (clients and mapping are tested).
- The worker over TLS or Tailscale (tested over localhost with `adb reverse`).

**Device control**
- Real root, Shizuku and ADB-over-TCP execution (gating, levels and hooks are tested).
- Screen control on the phone (verified on the emulator).
- Screenshots are not yet fed to the model automatically; the agent reasons over the accessibility tree.
- Termux `RUN_COMMAND`: Termux isn't installed on the test emulator.

**Visuals**
- Orb performance on the phone and the tilt sensor; the Canvas orb fallback on a real Android < 13 (forced in the bench screen).
- The drawer background quality on the phone with a detailed wallpaper.

## Development environment

- On the development machine, Robolectric tests fail if the JVM extracts its native libraries into `/tmp`. Run the gate with `JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=<a build folder outside /tmp>"`.
