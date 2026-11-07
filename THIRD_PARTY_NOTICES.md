# Third-party notices

## T3 Code contracts (`bridge/vendor/t3-contracts/`)

Copied unchanged from https://github.com/pingdotgg/t3code, `packages/contracts/src`, at commit
`25bf84ab07f53f141c20239c63e383823a9d5ea0` (see `bridge/vendor/t3-contracts/VENDORED.md`).

```
MIT License

Copyright (c) 2026 T3 Tools Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## AgentCraft (design reference)

https://github.com/blendi-remade/agentcraft — MIT, Copyright (c) 2026 AgentCraft contributors.

The mod⇄bridge protocol shape (`{v,type,id?}` envelope, hello → snapshot → upserts, `ack`,
Origin/Host refusal, reconnect with backoff) follows AgentCraft's design. Any file copied or
adapted from AgentCraft must keep this header and be listed here:

```
// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
```

Files adapted from AgentCraft (MIT; the `npc/behavior` files come from AgentCraft `mod/src/client/java/dev/agentcraft/client/agents/`):

- `mod/src/client/java/dev/t3craft/client/npc/behavior/GridPathfinder.java` ← `GridPathfinder.java`
  (A* with 8 directions, 1-block steps, no corner cutting, string pulling; reworked over a
  Minecraft-free `Walkable`, request-bounded search, player avoidance).
- `mod/src/client/java/dev/t3craft/client/npc/behavior/LevelWalkable.java` ← the walkability test
  of `GridPathfinder.java` (solid floor below, passable or thin feet block, headroom).
- `mod/src/client/java/dev/t3craft/client/npc/behavior/Motion.java` ← `AgentMotion.java` (calm
  walking pace with ease in/out and smooth turning; Minecraft-free, holds for the player).
- `mod/src/main/java/dev/t3craft/site/mc/LevelBlockWorld.java` — the block write flag set
  (`FLAGS`: clients updated, no neighbour/shape updates, no drops, no block-entity side effects,
  no `onPlace`) is taken from AgentCraft `mod/src/main/java/dev/agentcraft/hq/Plan.java`
  (commit `8fe3ad23489b06f9142c6382bc23d31090c6b406`).

Design ideas only (no code copied), from AgentCraft `hq/Plan.java` and `hq/PlanStore.java`:
apply a build as a diff of a whole box, persist the applied plan in the world folder (palette +
index per cell, temp file + atomic move), and treat a cell that matches neither the stored plan nor
the original as a player change that is never overwritten silently. t3craft's site code
(`dev.t3craft.site`) reimplements this for arbitrary terrain with full snapshots and undo.

AgentCraft license:

```
MIT License

Copyright (c) 2026 AgentCraft contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Portable terminal (`mod/src/client/java/dev/t3craft/client/portable/`, `mod/src/main/java/dev/t3craft/portable/`):
no AgentCraft file is copied. `PortableClient.category()` follows the approach of AgentCraft's
`mod/src/client/java/dev/agentcraft/client/hud/Keys.java` (register the key category, reuse the
equal-by-id record if another feature registered it first), and the 26.x API notes in AgentCraft's
`mod/DEV.md` were used as a reference. MIT, Copyright (c) 2026 AgentCraft contributors.

## Gradle wrapper (`mod/gradlew`, `mod/gradlew.bat`, `mod/gradle/wrapper/`)

Apache License 2.0, Copyright © 2015 the original authors (Gradle). Generated by `gradle wrapper`.

## Not used

Cheggin/minecraft-use (GPL-3.0) and minecraft-agent-ide (no license) were not read or used.
