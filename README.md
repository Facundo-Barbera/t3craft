# t3craft

Each T3 Code thread becomes a villager in Minecraft, coloured by its state (working, needs
attention, done, error, idle) and tagged with the machine it runs on.

```
[PC: Minecraft 26.3 + T3Craft mod]  --ws over Tailscale-->  [my-homelab: bridge (systemd)]
                                                              ├─ my-vps T3        (own token)
                                                              └─ my-acmesite T3   (own token)
```

- `mod/`: Fabric client-only mod (Minecraft 26.3, Java 25, Loom 1.19.4 no-remap, Mojang names).
  A dumb view: it draws what the bridge sends. It stores no T3 credentials.
- `bridge/`: Node 22+ / TypeScript / effect 4.1.2. Talks to every configured T3 environment over
  authenticated WebSocket RPC and serves the mod on the homelab's Tailscale IP only.
- `protocol/protocol.md`: the mod⇄bridge protocol v1. `docs/plan.md`: technical plan and the
  architecture decision.

Status: scaffolding plus phase 0/1 (list threads with colour). Chat, permissions and thread
creation are stubs (see TODOs and `docs/plan.md` §6).

## T3 token (one per environment)

On **each** T3 host, Facundo issues a minimal read-only session (needs nothing else for phase 0/1):

```bash
npx t3 auth session issue --scope orchestration:read --token-only
```

Put each token in its own file **outside the repo** (for example `/etc/t3craft/tokens/vps.token`,
`chmod 600`, owned by the service user) and reference it from `environments[].tokenFile`. Each
session can be revoked on its own host (`t3 auth` / Sharing & access). Phases 2+ (chat, approvals)
will need `--scope orchestration:operate`, which is close to admin: issue it separately when needed.

## Bridge

```bash
cd bridge
pnpm install
pnpm typecheck && pnpm test
cp config.example.json ~/.config/t3craft/bridge.json   # edit: listen.host, environments, token paths
T3CRAFT_CONFIG=~/.config/t3craft/bridge.json pnpm probe   # prints each environment's threads + state, then exits
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
Fabric Loader 0.20.1 / Minecraft 26.3 instance. Configure the bridge address in
`config/t3craft.json` of the instance (created on first start):

```json
{ "bridgeUrl": "ws://my-homelab.<tailnet>.ts.net:7878/", "bridgeKey": null }
```

The default is `ws://127.0.0.1:7878/`. `T3CRAFT_BRIDGE_URL` or `-Dt3craft.bridgeUrl=` override it.
Tailscale must be up on the PC. In a world, the villagers appear in a ring next to where you stand.
Right-click one to open its (placeholder) chat screen.

## Security rules for this repo

- No tokens, tickets or credentialed URLs in git (`.gitignore` covers `.env`, `*.token`, `run/`, `build/`).
- No servers on `0.0.0.0` or public IPs. Tests bind 127.0.0.1 on ephemeral ports.
- No GPL or unlicensed code. AgentCraft (MIT) only with attribution (`THIRD_PARTY_NOTICES.md`).

## License

MIT, see `LICENSE`. Third-party notices: `THIRD_PARTY_NOTICES.md`.
