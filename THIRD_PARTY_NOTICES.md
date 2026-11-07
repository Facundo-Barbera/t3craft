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

## Gradle wrapper (`mod/gradlew`, `mod/gradlew.bat`, `mod/gradle/wrapper/`)

Apache License 2.0, Copyright © 2015 the original authors (Gradle). Generated by `gradle wrapper`.

## Not used

Cheggin/minecraft-use (GPL-3.0) and minecraft-agent-ide (no license) were not read or used.
