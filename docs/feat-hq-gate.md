# HQ gate: nothing in the world without a built HQ (fix/hq-gate-npcs)

The mod puts **nothing** in a world until that world's HQ stands. The one gate is
`dev.t3craft.client.hq.HqPresence.allows(Minecraft)`; every client-side spawner asks it every tick.

## The gate

`allows` is true only when all of these hold:

1. **Own world**: `mc.hasSingleplayerServer()` (singleplayer / the integrated server, also when
   opened to LAN). On any remote server (vanilla, Paper, another modded server, a Realm) it is
   always false.
2. **An HQ stands here**: `HqPresence.current()` is set, i.e. the demo HQ world finished building
   (`DemoClient.installAnchors` → `HqPresence.demo`) or the survival site HQ reported its plan
   (`HqRuntime` anchor sink → `HqPresence.site`).
3. **Ready**: the demo HQ (all modules) or a site HQ whose `base` module is built
   (`Standing.ready()`).
4. **Same dimension**: the player's dimension equals `Standing.dimension()` (the site's dimension
   as recorded by `HqRuntime`; the demo HQ is always `minecraft:overworld`).

It closes again when the world is left (`HqPresence.clear` on disconnect, `DemoClient` on level
change), when the HQ is reset (`/t3craft debug reset-hq`, HQ menu Reset), when the HQ is rebuilt from
scratch (anchors cleared until the build ends), when the player changes dimension, and when the
site's base is undone. A shut gate despawns everything below on the next tick.

## What appears when

| Thing | Class | Shown when |
|---|---|---|
| Thread NPCs + their panels | `client/npc/NpcRegistry` | gate open and `NpcGate` lets them in (full build: after it ends; diff: newcomers after their desk) |
| Behaviour overlays (bubbles, icons) | `client/npc/behavior/BehaviorDirector` | with their NPC (ticked only while the gate is open) |
| Helpers (subagents) | `client/npc/helpers/HelperDirector` | gate open and the parent NPC stands |
| Workstations (computers) | `client/workstation/WorkstationClient` | gate open, at the HQ's desks only (`HqStations`) |
| Hologram | `client/hologram/HologramClient` | gate open, `hud.hologram` on, over the HQ hub/board |
| Hub beacon glow | `client/blocks/HubBeacon` | gate open and no beacon lamp block in the cap |
| Schedule board | `client/schedule/ScheduleClient` | gate open and a schedule is subscribed |
| Builders | `dev.t3craft.build.client.BuildCrew` | only while a build job runs; jobs exist only on the integrated server, in the HQ world (demo) or for the site HQ |

Gone for good:

- the **ring/grid of NPCs around the player** when there was no HQ (`GridLayout` origin at the
  player): NPCs now spawn at their HQ desk (else the HQ's way in) and the grid, if anything still
  falls back to it, is laid out from the HQ's way in;
- the **neighbourhood signs** floating over that grid;
- **provisional computers** next to the ring desks (`DeskPlacement` is no longer used);
- the **hologram over the provisional board**.

## On a server without the mod

The mod is client-only (`"environment": "client"`). On a remote server it:

- spawns **no** entity (the gate is shut), builds nothing and places no block: building (demo
  world, `/t3craft site …`, `/t3craft hq …`, builders) runs on the integrated server only and
  refuses or does nothing otherwise;
- sends **no** custom payload and **no** chat or server command: there is no networking code
  (`ClientPlayNetworking`, `PayloadTypeRegistry`, `sendCommand`/`sendChat`) anywhere in the mod;
  `/t3craft …` are client commands (Fabric client command API), answered locally;
- has **no** mixins;
- keybinds only open screens (Y: T3 screen; J: settings; pair/reconnect/reload/toggles);
- right/left clicks on our entities or blocks are consumed client-side only when they hit one of
  ours (never the case there), so vanilla clicks pass through.

**Still works there:** the T3 screen (key **Y**, `T3Screen`): chat, threads, model picker, new
thread, actions. It talks to the paired T3 environments / local bridge on 127.0.0.1, never to the
Minecraft server. HUD toasts and the status bar also keep working (screen-only).

## Risks not provable by reading the code

- **Registries**: the T3 Terminal item, the creative tab and (blocks module on) the t3craft blocks,
  block items and block entity types are registered at startup. They are appended after vanilla's,
  so a vanilla server's block-state and item ids still match; with a Fabric API server that lacks
  the mod, Fabric registry sync should accept extra client-only entries. Verify by joining a vanilla
  and a Fabric server once.
- **Fake entities** use negative client ids; if a server ever sent an entity with a clashing id the
  client would replace ours. With the gate shut on remote servers this cannot happen.
- A **site HQ in another world** of the same save directory is per-world state on the integrated
  server; `HqPresence` is cleared on every disconnect, so a stale HQ from the previous world is
  never used.
- **Builders** (`dev.t3craft.build`, owned by fix/hq-builders-independent) do not ask the gate
  themselves; they exist only while a job of the integrated server runs.
