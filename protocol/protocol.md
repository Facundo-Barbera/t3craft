# t3craft protocol v1 (mod ⇄ bridge)

Source of truth for types: `bridge/src/protocol.ts`. This document must match it.

## Topology

```
[PC: Minecraft + T3Craft mod]  --ws://<homelab-tailscale-ip-or-name>:7878-->  [my-homelab: bridge (systemd)]
                                                                                 ├─ wss → my-vps T3 (own token)
                                                                                 └─ http(s) → my-acmesite T3 :3773 (own token)
```

- The bridge listens **only** on the homelab's Tailscale IP (or loopback for tests). Wildcard
  (`0.0.0.0`, `::`) and public addresses are refused at config load and again at bind.
- The mod connects to a configurable URL (default `ws://127.0.0.1:7878/`). It holds **no T3
  credentials**. It may hold an optional bridge access key (`hello.key`), which only grants access
  to the bridge, never to T3.
- **The mod is a dumb view.** State mapping, colours, glyphs, label text, ordering and notification
  wording are all decided by the bridge and sent ready to draw. Changing them needs a bridge
  restart, never a Minecraft restart.

## Transport and envelope

- WebSocket, UTF-8 JSON text frames, one object per frame, max 256 KiB.
- Every frame: `{ "v": 1, "type": "<type>", "id"?: "<string>", ...payload }`. Payload fields sit at
  the top level (`id`, `v`, `type` are reserved).
- Every mod→bridge message that carries `id` is answered with
  `ack { re: <id>, ok: boolean, error?: string }`.
- Unknown `type`s must be ignored by the mod (forward compatibility). The bridge acks unknown or
  malformed mod frames with `ok:false`.

## Connection policy (bridge side)

- Upgrade refused (HTTP 403) if the request has an `Origin` header (browsers), or if the `Host`
  header's hostname is not loopback, the listen IP, or one of `listen.allowedHostnames`
  (DNS-rebinding guard).
- The first frame must be `hello` within 5 s, otherwise close `4001`. Any other first frame: close
  `4002`. Wrong/missing key when `listen.clientKeyFile` is configured: close `4003`.
- On a good hello: `ack`, then bridge `hello`, then `snapshot`, then `bridge.status`.

## Mod reconnect

Exponential backoff with jitter (0.5 s → 15 s cap), reset after a successful open. After every
reconnect the mod sends `hello` again and replaces its whole NPC set with the new `snapshot`.

## NpcView (the unit of display)

```jsonc
{
  "id": "vps:thr_123",          // "<env>:<threadId>", unique across environments
  "env": "vps",
  "envLabel": "my-vps",      // which machine the thread lives on
  "threadId": "thr_123",
  "title": "Fix login",         // already clipped
  "project": "telar",           // or null
  "state": "needs_attention",   // working | needs_attention | done | error | idle (informational)
  "color": "#FFAA00",           // draw label in this colour
  "glyph": "!",
  "label": "! Fix login",       // main nameplate line, drawn in `color`
  "sublabel": "my-vps · telar", // second line, grey
  "activity": "Approval needed: command", // or null; short status / last message
  "priority": 0,                // lower first; the mod lays NPCs out in this order
  "settled": false              // the user settled the thread in T3 (mod may dim it)
}
```

The mod must not derive anything from `state`; it renders `label`/`sublabel`/`color`/`activity`.

Phase 1 nameplate (vanilla custom name, one line): `label` in `color`, then `  [envLabel]` in grey.
The chat screen shows `label`, `sublabel` and `activity`. NPCs are laid out by ascending
`priority`, then `id`.

## Mod configuration

`config/t3craft.json` in the Minecraft instance (created with defaults on first start):

```json
{ "bridgeUrl": "ws://my-homelab.<tailnet>.ts.net:7878/", "bridgeKey": null }
```

`bridgeUrl` defaults to `ws://127.0.0.1:7878/`; env `T3CRAFT_BRIDGE_URL` or
`-Dt3craft.bridgeUrl=` override it. `bridgeKey` is only needed when the bridge sets
`listen.clientKeyFile`. The mod never stores T3 tokens.

### Added in v1.1 (additive, older mods ignore them)

`NpcView` also carries `groupId` (`<env>:<projectId>`), `groupLabel` (project), `groupSublabel`
(environment), `skin` (seed for a deterministic player skin) and `particle`
(`working|attention|error|done|null`).

| type | payload | notes |
|---|---|---|
| `npc.transition` | `npcId`, `from`, `to`, `level`, `title`, `text`, `color`, `sound: attention\|done\|error` | visible NPC changed state; never for initial state. A `notify` with the same text is still sent for older mods |
| `hud.peek` | `text \| null`, `color`, `npcId \| null` | one-line HUD summary; sent on change and on connect |

Declared for the chat history (`chat.history` page, `chat.message` upsert, `chat.status`) and
`bridge.reload`; not served yet. The bridge is now the **legacy** path: see `docs/plan-v2.md`
(direct mod ⇄ T3 pairing).

## Bridge → mod

| type | payload | notes |
|---|---|---|
| `hello` | `bridge`, `protocol: 1` | reply to the mod's hello |
| `snapshot` | `npcs: NpcView[]` | full replacement of the mod's NPC set, sorted |
| `npc.upsert` | `npc: NpcView` | add or replace by `npc.id` |
| `npc.remove` | `npcId` | |
| `notify` | `level: info\|warn\|error`, `title`, `text`, `color`, `npcId?` | toast; wording decided by bridge |
| `bridge.status` | `environments: [{id,label,status,detail?}]` | `status`: `connecting\|ok\|auth_failed\|offline\|incompatible`; sent on every change |
| `ack` | `re`, `ok`, `error?` | |
| `chat.history` *(phase 2)* | `npcId`, `messages: [{role,text}]` | after `chat.open` |
| `chat.append` *(phase 2)* | `npcId`, `message: {role,text}` | live while chat open |
| `permission.open` *(phase 3)* | `npcId`, `requestId`, `kind`, `summary`, `options: [{decision,label,warning?}]` | |
| `permission.closed` *(phase 3)* | `npcId`, `requestId` | resolved elsewhere / expired |

## Mod → bridge

| type | payload | bridge action |
|---|---|---|
| `hello` | `client`, `version`, `protocol: 1`, `key?` | handshake |
| `chat.open` *(phase 2)* | `npcId` | `orchestration.subscribeThread` for that thread |
| `chat.close` *(phase 2)* | `npcId` | drop that subscription |
| `chat.send` *(phase 2)* | `npcId`, `text` | `dispatchCommand` `message.dispatch` |
| `run.interrupt` *(phase 2)* | `npcId` | `dispatchCommand` `run.interrupt` (runId = shell.activeRunId) |
| `permission.respond` *(phase 3)* | `npcId`, `requestId`, `decision: accept\|acceptForSession\|acceptAlways\|decline\|cancel`, `feedback?` | `dispatchCommand` `runtime-request.respond` |
| `thread.create` *(phase 4)* | `env`, `projectId`, `title`, `text` | `thread.create` / `launchThread` |

Until a phase is implemented the bridge acks its messages with `ok:false, error:"not implemented"`.
Phase 2+ write commands need an `orchestration:operate` token for that environment.

## Example session

```
mod → {"v":1,"type":"hello","id":"m1","client":"t3craft-mod","version":"0.1.0","protocol":1}
br  → {"v":1,"type":"ack","re":"m1","ok":true}
br  → {"v":1,"type":"hello","bridge":"0.1.0","protocol":1}
br  → {"v":1,"type":"snapshot","npcs":[{...NpcView...}]}
br  → {"v":1,"type":"bridge.status","environments":[{"id":"vps","label":"my-vps","status":"ok"}]}
br  → {"v":1,"type":"npc.upsert","npc":{...}}
br  → {"v":1,"type":"notify","level":"warn","title":"Approval needed: command","text":"Fix login (my-vps)","color":"#FFAA00","npcId":"vps:thr_123"}
```
