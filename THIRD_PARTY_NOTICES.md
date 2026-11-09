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

The kit blocks `t3craft:glow_strip` and `t3craft:glow_panel` (emissive strip and panel lighting) take only the idea from
AgentCraft's blocks of the same names: their models, textures (drawn by `tools/art/glow_art.py`) and code are our own, nothing is copied.

Idea only, nothing copied: `t3craft:decision_podium` (`DecisionPodiumBlock`, its models and textures, `tools/art/decision_podium_art.py`)
takes the idea of AgentCraft's `decision_podium` (a podium with a bell and a `!` where the agent that needs a decision waits, and a click that
opens it). The code, the models and every pixel are t3craft's own, so those files carry no AgentCraft header.

Files adapted from AgentCraft (MIT; the `npc/behavior` files come from AgentCraft `mod/src/client/java/dev/agentcraft/client/agents/`):

- `mod/src/client/java/dev/t3craft/client/npc/behavior/GridPathfinder.java` ← `GridPathfinder.java`
  (A* with 8 directions, 1-block steps, no corner cutting, string pulling; reworked over a
  Minecraft-free `Walkable`, request-bounded search, player avoidance, body-sweep passages,
  start/goal snapping and a resumable search for a per-tick node budget).
- `mod/src/client/java/dev/t3craft/client/npc/behavior/Collision.java` ← the walkability test
  of `GridPathfinder.java` (solid floor below, passable or thin feet block, headroom; formerly
  `LevelWalkable.java`), rewritten over real collision boxes with an AABB body sweep and
  collision-resolved movement.
- `mod/src/client/java/dev/t3craft/client/npc/behavior/Motion.java` ← `AgentMotion.java` (calm
  walking pace with ease in/out and smooth turning; Minecraft-free, holds for the player,
  collision-resolved sub-moves and stuck detection).
- `mod/src/client/java/dev/t3craft/client/hud/StatusBarModel.java` and `StatusBar.java` ←
  `mod/src/client/java/dev/agentcraft/client/hud/GoalBar.java` (design: top bar of counts with a
  "n waiting · KEY" badge under it, recounted only on change; rewritten over thread states, with a
  Minecraft-free model and top/bottom placement that keeps clear of the survival bars).
- `mod/src/main/java/dev/t3craft/site/mc/LevelBlockWorld.java` — the block write flag set
  (`FLAGS`: clients updated, no neighbour/shape updates, no drops, no block-entity side effects,
  no `onPlace`) is taken from AgentCraft `mod/src/main/java/dev/agentcraft/hq/Plan.java`
  (commit `8fe3ad23489b06f9142c6382bc23d31090c6b406`).
- `mod/src/client/java/dev/t3craft/client/hologram/HologramModel.java`, `HologramLayout.java` and
  `HologramRenderer.java` ← `mod/src/client/java/dev/agentcraft/client/hq/StatusLampRenderer.java`
  (`extractHologram`, `beam`, `card`, `HOLO_Y`) at commit `8fe3ad23489b06f9142c6382bc23d31090c6b406`
  (design: a floating card about 3.5 blocks over the floor with a progress ring captioned
  "done/total" that follows the done count, "n working · n need you" counts, an "offline · last
  known state" line and a soft beam from the floor to the card, rebuilt only on change; rewritten
  over T3 thread states of every environment with one ring per environment or project and the most
  urgent thread, as client-only display entities instead of a block entity renderer, no sprites or
  textures copied).

Design ideas only (no code copied), from AgentCraft `hq/Plan.java` and `hq/PlanStore.java`:
apply a build as a diff of a whole box, persist the applied plan in the world folder (palette +
index per cell, temp file + atomic move), and treat a cell that matches neither the stored plan nor
the original as a player change that is never overwritten silently. t3craft's site code
(`dev.t3craft.site`) reimplements this for arbitrary terrain with full snapshots and undo.

Files adapted from AgentCraft's blocks (`mod/src/main/java/dev/agentcraft/block/`,
`mod/src/client/java/dev/agentcraft/client/world/`, MIT), branch `feat/blocks-core`:

- `mod/src/main/java/dev/t3craft/blocks/BoundBlockEntity.java` ← `block/entity/StationBlockEntity.java`
  (binding string on a block entity: save/load, update packet and update tag for client sync).
- `mod/src/main/java/dev/t3craft/blocks/StatusLampBlock.java` ← `block/StatusLampBlock.java`
  (`status` enum block state with light per state + binding block entity).
- `mod/src/main/java/dev/t3craft/blocks/LampStatus.java` ← `block/LampStatus.java` (status enum as a
  `StringRepresentable`; t3craft's own states, colours and light levels).
- `mod/src/main/java/dev/t3craft/blocks/MonitorBlock.java` ← `block/FacingEntityBlock.java`
  (horizontal facing, placement, rotate and mirror).
- `mod/src/client/java/dev/t3craft/client/blocks/MonitorRenderer.java` ← `client/world/StationRenderer.java`
  (`toFace`: draw in screen pixel space on a block's front face).

Files adapted from AgentCraft (commit `8fe3ad2`, MIT; task wall: `mod/src/client/java/dev/agentcraft/client/taskwall/`,
`mod/src/main/java/dev/agentcraft/block/TaskBoardBlock.java`), branch `feat/thread-wall`:

- `mod/src/main/java/dev/t3craft/blocks/ThreadBoardBlock.java` ← `block/TaskBoardBlock.java` and
  `block/FacingEntityBlock.java` (the panel origin draws the whole board; facing, rotate, mirror).
- `mod/src/client/java/dev/t3craft/client/blocks/WallColumns.java` ← `taskwall/TaskBoard.java` (`Col`:
  fixed kanban columns with a status family each; t3craft's own columns and project/environment variants).
- `mod/src/client/java/dev/t3craft/client/blocks/WallLayout.java` ← `taskwall/TaskBoard.java` (lanes,
  headers with counts, "+N more" overflow chip whose click opens the first hidden card; fixed-size cards).
- `mod/src/client/java/dev/t3craft/client/blocks/WallBoard.java` ← `taskwall/TaskBoard.java` (`step`:
  exponential glide, `Card.arrivedFrom`: cards that change column fly and glow where they land; layout
  cached until the input changes).
- `mod/src/client/java/dev/t3craft/client/blocks/WallGeometry.java` ← `taskwall/TaskWallFeature.java`
  (`toBoard`: world hit → board pixels for a panel drawn from its bottom-left origin).
- `mod/src/client/java/dev/t3craft/client/blocks/ThreadBoardRenderer.java` ←
  `taskwall/TaskBoardRenderer.java` (draw from the origin over the whole board, off-screen rendering,
  lifted flying cards with shadows, hover from the crosshair, pulsing ring on cards that wait on the user).

Monitor live log (branch `feat/monitor-live-log`, AgentCraft commit `8fe3ad2`, sources under
`mod/src/client/java/dev/agentcraft/client/`):

- `mod/src/client/java/dev/t3craft/client/blocks/MonitorRenderer.java` (rewritten) ←
  `monitor/MonitorRenderer.java` (header with status dot, tail of tool rows with kit icons, blinking
  caret while working, pulsing "waiting for you", opaque full-bright layers with explicit z steps,
  off-screen rendering) on top of `world/StationRenderer.java`.
- `mod/src/client/java/dev/t3craft/client/blocks/ScreenDraw.java` ← `monitor/DisplayDraw.java` and
  `ui/WorldUi.java` (opaque copy of the world text pipeline drawn in the solid pass, flat rectangles
  on a white texture, GUI-atlas sprites as opaque quads).
- `mod/src/client/java/dev/t3craft/client/blocks/MonitorLog.java` ← `monitor/LogRows.java` and
  `monitor/MonitorScreen.java` (one-line tool rows that never wrap, prose limited to a few lines
  without Markdown markup, +/- counts in green/red, newest row at the bottom, caret / waiting line).
- `mod/src/client/java/dev/t3craft/client/blocks/MonitorLod.java` ← `MonitorRenderer.density`
  (128 px/block for one-block screens, 96 for bigger ones) and `DisplayDraw.Z_STEP`.
- `mod/src/client/java/dev/t3craft/client/blocks/MonitorVisibility.java` ← the idea of
  `monitor/MonitorFeature.java`'s sweep of screens unused for 30 s (new implementation: distance and
  view with hysteresis, one subscription per thread).

Multi-block monitor (branch `feat/monitor-multiblock`, AgentCraft commit `8fe3ad2`, sources under
`mod/src/main/java/dev/agentcraft/` and `mod/src/main/resources/assets/agentcraft/`):

- `mod/src/main/java/dev/t3craft/blocks/MonitorBlock.java` ← `block/PanelBlock.java` (connection
  properties `up/down/left/right` as seen by the viewer, `left = facing.getClockWise()`,
  `right = facing.getCounterClockWise()`, the walk to the panel's corner and along its edges to get
  origin and extent).
- `mod/src/main/java/dev/t3craft/blocks/MonitorWall.java` ← `block/PanelBlock.java`
  (`connect/origin/extent`; reworked: the group is validated as a whole, rectangles only, with
  agreeing bindings and a size limit, over a Minecraft-free lookup).
- `mod/src/main/java/dev/t3craft/blocks/ScreenRect.java` ← `block/PanelBlock.java` (origin + width ×
  height of a connected panel, as a value).
- `mod/src/main/resources/assets/t3craft/blockstates/monitor.json` and
  `models/block/monitor_edge_{up,down,left,right}.json` ← `blockstates/monitor.json` and
  `models/block/monitor_edge_*.json` (multipart: one bezel model per side, applied while that side is
  not connected; adapted: full-block monitor, our own textures, no corner models).

Files adapted from AgentCraft (commit `8fe3ad2`, MIT), branch `feat/lamps-breathe`:

- `mod/src/client/java/dev/t3craft/client/blocks/LampRenderer.java` ← `client/hq/StatusLampRenderer.java`
  (`breathe`, `beacon` and `beam`: additive lightning-type quads over the lens with alpha gradients,
  crossed upward-fading beam planes).
- `mod/src/client/java/dev/t3craft/client/blocks/LampGlow.java` ← `client/hq/StatusLampRenderer.java` and
  `client/hq/HqClientFeature.java` (sine-breathing glow, motes only on lamps that wait on the player;
  t3craft's own per-state curves).
- `mod/src/client/java/dev/t3craft/client/blocks/HubBeacon.java` and `LampSignals.beacon` ←
  `client/hq/HqWorldDriver.java` (`BEACON_BINDING`, `beaconLamp`: the aggregate state, most urgent
  first); the client-only display fallback is t3craft's own.
- Motes in `client/blocks/BlocksClient.java` ← `client/hq/HqClientFeature.java` (`motes`: dust rising in
  front of an exposed face).

The lamp driver (`dev.t3craft.blocks.LampApplier` + `client.blocks.BlocksClient`: the client computes
lamp states and the integrated server applies them) follows the idea of AgentCraft's
`HqWorldDriver`; it is a new implementation, no code copied. Models and textures are t3craft's own.

Thread chat (feat/chat). The browser copies the look and behaviour of AgentCraft's console as
closely as T3's data allows (AgentCraft commit `8fe3ad2`). Sources under
`mod/src/client/java/dev/agentcraft/client/` unless noted:

- `mod/src/client/java/dev/t3craft/client/chat/ChatTheme.java` ← the "Warm Studio" tokens of
  `ui/UiStyle.java`, `mod/src/main/resources/assets/agentcraft/gui/ui-style.json`, `palette.json`
  and `assets-src/ui-style.md` (paper GUI colours, status palette, metrics: margin 8, gap 4,
  panel padding 8, row 10, tool row 13, field 18, pulse 1200 ms, caret 500 ms).
- `mod/src/client/java/dev/t3craft/client/chat/ChatDraw.java` ← `ui/Panels.java` and
  `hud/UiBits.java` (panel, inset, pill, keycap hints, 6 px scrollbar with grip, status dots and
  the waiting pulse, minute separator).
- `mod/src/client/java/dev/t3craft/client/chat/ConversationPanel.java` ←
  `console/ConsoleScreen.java` (panel layout, header with rule, inset feed with 2 px stripe, avatar
  and hanging text, separators, "↓ newer below" pill, key-cap footer, command bar with brass `>`),
  `console/TextFieldView.java` (field metrics, placeholder colour), `monitor/LogRows.java` (tool
  lines, tinted diff rows) and `decisions/DecisionScreen.java` (‹ n/N › header navigation).
- `mod/src/client/java/dev/t3craft/client/chat/ThreadListView.java` ← the kit drawing above and the
  console popup's selection (clay wash + 2 px clay bar).
- `mod/src/client/java/dev/t3craft/client/chat/ThreadBrowser.java` and `ThreadBrowserScreen.java`
  ← the console's placement and veil and the decision screen's keys (Tab / Shift+Tab, Esc).
- `mod/src/client/java/dev/t3craft/client/chat/ToolLine.java` ← `monitor/LogRows.java`
  (`tool()`, `iconFor()`, `shortName()`, test-command pattern).
- `mod/src/client/java/dev/t3craft/client/chat/FeedScroll.java` ← `ui/TextUtil.java`
  (`Scroll`, in pixels).
- `mod/src/client/java/dev/t3craft/client/chat/FeedRules.java`, `ChatTurns.java` ← the console's
  minute separators and speaker rows.
- Sprites `mod/src/main/resources/assets/t3craft/textures/gui/sprites/chat/*.png` (+ `.mcmeta`) ←
  `mod/src/main/resources/assets/agentcraft/textures/gui/sprites/kit/` (panel_paper, panel_inset,
  frame_brass, header, divider, tooltip, bubble, bubble_tail, pill, keycap, text_field,
  text_field_focused, scroll_track, scroll_thumb, scroll_thumb_hover, scroll_grip, dot_* and
  dot_*_halo, icon_bash/decision/edit/git/memory/merge/message/read/test), copied unchanged. AgentCraft's
  README states that all its art is generated by `assets-src/` and covered by the same MIT license.

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

The leader office tower (`citadel/CitadelOffice.java`, branch `struct/leader-office`) takes its control-room *flow* from AgentCraft's studio (threads on screens, a wall of attention lamps, an archive, a console desk); it copies no code, model or texture. The blocks AgentCraft has and t3craft lacks are only proposed, as new code, in `docs/plans/leader-office-blocks.md`.

`t3craft:console_terminal` (branch `kit/console-terminal`, `ConsoleTerminalBlock`, its model, textures and `tools/art/console_terminal_art.py`) takes only the idea of AgentCraft's `console_terminal` (a desk that opens the command console). No code, model or texture was copied; the pixel art is generated by the script from scratch.
