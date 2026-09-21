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
