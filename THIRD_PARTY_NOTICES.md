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

Files adapted from AgentCraft (from `mod/src/client/java/dev/agentcraft/client/agents/`, MIT):

- `mod/src/client/java/dev/t3craft/client/npc/behavior/GridPathfinder.java` ← `GridPathfinder.java`
  (A* with 8 directions, 1-block steps, no corner cutting, string pulling; reworked over a
  Minecraft-free `Walkable`, request-bounded search, player avoidance).
- `mod/src/client/java/dev/t3craft/client/npc/behavior/LevelWalkable.java` ← the walkability test
  of `GridPathfinder.java` (solid floor below, passable or thin feet block, headroom).
- `mod/src/client/java/dev/t3craft/client/npc/behavior/Motion.java` ← `AgentMotion.java` (calm
  walking pace with ease in/out and smooth turning; Minecraft-free, holds for the player).
- `mod/src/client/java/dev/t3craft/client/hud/StatusBarModel.java` and `StatusBar.java` ←
  `mod/src/client/java/dev/agentcraft/client/hud/GoalBar.java` (design: top bar of counts with a
  "n waiting · KEY" badge under it, recounted only on change; rewritten over thread states, with a
  Minecraft-free model and top/bottom placement that keeps clear of the survival bars).

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
no AgentCraft file is copied. The portable key category used to follow the approach of AgentCraft's
`mod/src/client/java/dev/agentcraft/client/hud/Keys.java` (register the key category, reuse the
equal-by-id record if another feature registered it first); it now uses the shared
`T3CraftKeys.CATEGORY`. The 26.x API notes in AgentCraft's `mod/DEV.md` were used as a reference.
The held-item screen only aims to look coherent with AgentCraft's thread list in miniature (one
list, environment chip per row); no AgentCraft code or asset is used for it. MIT, Copyright (c) 2026 AgentCraft contributors.

The HQ generator (`mod/src/main/java/dev/t3craft/hq/`, branch `feat/hq-design`) takes its look
from AgentCraft's `StudioHqBuilder` ("Warm Studio": timber frame, waxed copper roofs and lights,
glass, warm lighting), its anchor idea (`desk_<id>`, `entrance`, `spawn`) and its one
workstation (desk + computer) per agent. It is a new
implementation: no AgentCraft source was copied or adapted (`Palette.java` says so in its header).

## Gradle wrapper (`mod/gradlew`, `mod/gradlew.bat`, `mod/gradle/wrapper/`)

Apache License 2.0, Copyright © 2015 the original authors (Gradle). Generated by `gradle wrapper`.

## Not used

Cheggin/minecraft-use (GPL-3.0) and minecraft-agent-ide (no license) were not read or used.
