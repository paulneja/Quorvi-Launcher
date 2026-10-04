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
