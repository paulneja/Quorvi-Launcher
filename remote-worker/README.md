# Quorvi remote worker

A daemon that runs Quorvi's long tasks on your own Linux machine. It keeps working while the phone is offline, and Quorvi reconciles when it comes back.

## Install

```sh
go build -o ~/.local/bin/quorvi-worker .
quorvi-worker -listen 127.0.0.1:8790
```

On start it prints a **pairing code** valid for 10 minutes. In Quorvi, open Settings → Servers → Services → Pair a remote worker and enter the URL and the code. The code works once, and the worker stores only the token's hash.

To reach it from the phone, listen on a Tailscale or LAN address (`-listen 100.x.y.z:8790`). Quorvi only allows `http://` to local or Tailscale networks; over the internet it needs TLS (`-tls-cert`/`-tls-key`).

As a user service:

```sh
cp quorvi-worker.service ~/.config/systemd/user/
systemctl --user enable --now quorvi-worker
journalctl --user -u quorvi-worker   # the pairing code shows up here
```

`quorvi-worker -revoke` revokes every paired phone.

## Protocol v1 (HTTP + JSON)

| Method | Path | What it does |
|---|---|---|
| POST | `/v1/pair` | `{code}` → `{token, workerId, protocol}` |
| GET | `/v1/hello` | identity, version and protocol (doubles as a heartbeat) |
| POST | `/v1/runs` | `{idempotencyKey, command, cwd?, timeoutSec?}` → `{run, existing}`; a repeated key returns the existing run instead of running it again |
| GET | `/v1/runs/{id}` | run status |
| GET | `/v1/runs/{id}/events?after=N&wait=S` | events with a sequence above N; long-polls for up to 30 s |
| POST | `/v1/runs/{id}/cancel` | SIGTERM to the process group, SIGKILL after 3 s |

Each run keeps `meta.json` and an append-only `events.jsonl` in `~/.local/share/quorvi-worker/runs/<id>/`. If the daemon restarts, anything that was RUNNING becomes INTERRUPTED: a run is never reported finished unless it was seen finishing.

The worker runs shell commands as the user running it. It is not a sandbox: run it as a user with only the permissions it needs.
