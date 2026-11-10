# t3craft

Each T3 Code thread becomes a villager in Minecraft, coloured by its state (working, needs
attention, done, error, idle) and tagged with the machine it runs on.

> **The bridge is legacy and frozen** (`bridge/`, `protocol/`; decision D2, issue #7). The mod pairs **directly**
> with each T3 environment (direct mode, the default; see "Direct mode" below). The bridge only gets critical fixes,
> no new features. Whether it is retired is decided when #1 (T3 Connect) closes. Status and rules:
> `bridge/README.md`. The diagram and the `mod/`/`bridge/` bullets below still describe the original bridge setup;
> the README rewrite is phase 6 (#8).

```
[PC: Minecraft 26.3 + T3Craft mod]  --ws over Tailscale-->  [<homelab>: bridge (systemd)]
                                                              ├─ vps-env T3        (own token)
                                                              └─ <laptop> T3   (own token)
```

- `mod/`: Fabric client-only mod (Minecraft 26.3, Java 25, Loom 1.19.4 no-remap, Mojang names).
  A dumb view: it draws what the bridge sends. It stores no T3 credentials.
- `bridge/` (**legacy, frozen**, see `bridge/README.md`): Node 22+ / TypeScript / effect 4.1.2. Talks to every configured T3 environment over
  authenticated WebSocket RPC and serves the mod on the homelab's Tailscale IP only.
- `protocol/protocol.md`: the mod⇄bridge protocol v1 (legacy, frozen). `docs/plans/plan.md`: technical plan and the
  architecture decision.
- `tools/`: dev tooling. `heavy.sh` runs one heavy process at a time (render PC only), `render/` holds the
  render relay and the PC daemon scripts, `studio/` is Citadel Studio, and `art/` has the texture and model
  generators that write into `mod/src/main/resources`.
- `docs/`: see `docs/README.md` for what lives where.

Status: scaffolding plus phase 0/1 (list threads with colour). Chat, permissions and thread
creation are stubs (see TODOs and `docs/plans/plan.md` §6).

## Direct mode (default): pair the mod with each T3

Since v2.1 the mod talks to each T3 Code environment itself, like the desktop app with a remote
server. The bridge below is legacy and frozen (`"mode": "bridge"`, see `bridge/README.md`).

1. On each T3 host, issue a one-time pairing credential (valid 5 minutes by default). By default
   the mod pairs with **read + control** (`orchestration:operate`: send messages, approve, stop…)
   in a single pairing:
   ```bash
   t3 auth pairing create --scope orchestration:read --scope orchestration:operate --base-url https://<vps>.<tailnet>.ts.net
   ```
   For a **read-only** pairing use only `--scope orchestration:read` and `/t3craft pair --read-only`
   (or the "Access" toggle on the pairing screen). Always pass explicit `--scope`s: without them
   the credential carries every scope. The mod refuses a session that grants anything beyond
   `orchestration:read` + `orchestration:operate`. Destructive actions ask for an in-game
   confirmation and every action lands in `config/t3craft/audit.log`. Details: `docs/features/operate.md`.
2. In Minecraft run `/t3craft pair`, enter a name (shown on the villagers, e.g. `vps-env`) and
   paste the link (`https://host/pair#token=…`), or the bare code plus the T3 URL
   (e.g. `http://<laptop>.<tailnet>.ts.net:3773`). Repeat for each environment. An
   environment paired read-only can get control later with "Enable control" in the environments
   screen (or `/t3craft operate enable <env>`); `/t3craft operate revoke <env>` takes it back to
   read-only.
3. The session (Bearer, 30 days, no refresh: pair again when it expires) is stored in the
   instance profile under `config/t3craft/credentials/<env>.json` (plus `credentials/operate/<env>.json`
   when it has control; owner-only where the OS allows).
   It is never logged and never belongs in a repo. Revoke it on the host with
   `t3 auth session list` / `t3 auth session revoke <id>` (or Sharing & access), and forget it
   locally with `/t3craft unpair <env>`.

Commands: `/t3craft status | reconnect | reload | pair [--read-only] | unpair <env> | envs | operate [enable|revoke <env>]`. `reload` re-reads
`config/t3craft.json`, the paired environments and their credentials, and reconnects without
restarting the game. Visibility in direct mode is set in `config/t3craft.json`
(`visibility.recentWindowHours` 24, `visibility.maxPerEnvironment` 40, `includeSubagents`).

Threads show up as player-like NPCs (default skins chosen per environment and state), grouped into
neighbourhoods per environment + project with a floating sign. Each has its label in the state
colour, a panel with environment · project and the current activity, and particles for working,
needs-you and error.

## T3 token for the legacy bridge (one per environment)

Legacy installs only: the bridge is frozen (`bridge/README.md`).

On **each** T3 host, Facundo issues a minimal read-only session (needs nothing else for phase 0/1):

```bash
npx t3 auth session issue --scope orchestration:read --token-only
```

Put each token in its own file **outside the repo** (for example `/etc/t3craft/tokens/vps.token`,
`chmod 600`, owned by the service user) and reference it from `environments[].tokenFile`. Each
session can be revoked on its own host (`t3 auth` / Sharing & access). Phases 2+ (chat, approvals)
will need `--scope orchestration:operate`, which is close to admin: issue it separately when needed.

## Bridge (legacy, frozen)

For existing bridge installs only. New installs use direct mode; the bridge gets critical fixes only
(`bridge/README.md`).

```bash
cd bridge
pnpm install
pnpm typecheck
cp config.example.json ~/.config/t3craft/bridge.json   # edit: listen.host, environments, token paths
T3CRAFT_CONFIG=~/.config/t3craft/bridge.json pnpm probe          # threads the mod would show (filtered), then exits
T3CRAFT_CONFIG=~/.config/t3craft/bridge.json pnpm probe --all    # no window/cap (archived/settled still hidden)
T3CRAFT_CONFIG=~/.config/t3craft/bridge.json pnpm probe --env vps
T3CRAFT_CONFIG=~/.config/t3craft/bridge.json pnpm start   # runs the bridge
```

Config (`config.example.json`):
- `listen.host` must be the host's **Tailscale IP** (100.64.0.0/10 or fd7a:115c:a1e0::/48) or
  loopback. `0.0.0.0`, `::` and public IPs are refused. `listen.allowedHostnames` adds accepted
  `Host` names (for example the MagicDNS name). Connections with an `Origin` header are refused.
- `listen.clientKeyFile` (optional, recommended before phase 3): a shared key the mod must send.
  It grants bridge access only, never T3 access.
- `environments[]`: `id` (goes into NPC ids `<id>:<threadId>`), `label` (shown on the villager),
  `url` (no credentials), `tokenFile` (absolute path outside the repo).
- `visibility` (optional; installed configs without it get the defaults): which threads get a
  villager, decided per environment.
  - Always hidden: archived, deleted, settled, and subagents/side chats (unless `includeSubagents`).
  - Shown: threads that need you (approval, user input, `waiting`, plan to review), threads
    working or with a turn in progress, and threads with activity (messages/runs) in the last
    `recentWindowHours` (default `24`).
  - At most `maxPerEnvironment` (default `40`) per environment: needs-you first, then working,
    then most recent.
  - Re-evaluated on every T3 update and every 60 s, so expired threads disappear on their own
    and come back as soon as they qualify again (the bridge keeps the full state).
- A missing or unreadable token file marks that environment `auth_failed`; restart the bridge
  after fixing it.

On the homelab it runs as a systemd service: see the example unit
`bridge/deploy/t3craft-bridge.service` (config at `/etc/t3craft/bridge.json`). Deployment itself
is not scripted in this repo.

## Mod

```bash
cd mod
./gradlew build        # jar -> mod/build/libs/t3craft-0.1.0.jar (needs JDK 25; Gradle toolchains can fetch it)
./gradlew runClient    # dev client (on the PC)
```

On the Windows PC: `gradlew.bat runClient`, or put the jar plus Fabric API 0.165.2+26.3 in a
Fabric Loader 0.20.1 / Minecraft 26.3 instance. In direct mode (the default) pair with `/t3craft pair` (see
"Direct mode"). Only for the legacy bridge mode, configure the bridge address in
`config/t3craft.json` of the instance (created on first start):

```json
{ "bridgeUrl": "ws://<homelab>.<tailnet>.ts.net:7878/", "bridgeKey": null }
```

The default is `ws://127.0.0.1:7878/`. `T3CRAFT_BRIDGE_URL` or `-Dt3craft.bridgeUrl=` override it.
Tailscale must be up on the PC. In a world, the villagers appear in a ring next to where you stand.
Right-click one to open its (placeholder) chat screen.

## Security rules for this repo

- No tokens, tickets or credentialed URLs in git (`.gitignore` covers `.env`, `*.token`, `run/`, `build/`).
- No servers on `0.0.0.0` or public IPs.
- Este repo no usa tests unitarios; prohibido agregarlos o correrlos. Verificación = compilar.
- No GPL or unlicensed code. AgentCraft (MIT) only with attribution (`THIRD_PARTY_NOTICES.md`).

## License

MIT, see `LICENSE`. Third-party notices: `THIRD_PARTY_NOTICES.md`.
