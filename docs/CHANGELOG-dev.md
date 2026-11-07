# CHANGELOG-dev

Read by Taller to decide how to refresh Facundo's game after pulling `main`.
One line per structural commit on `main`, newest first:

    - <date> <branch> RESTART|RELOAD — <what changed>

- `RESTART`: close and relaunch the client (new items, blocks, registries, entrypoints, keybinds, commands, HUD elements, mixins, dependencies).
- `RELOAD`: no structural change; code/behaviour, resources/lang or config only (hot-reload / rebuild is enough).

## Entries

- 2026-11-07 feat/search RESTART — new command /t3craft search <text> (clickable results across all environments, read-only searchThreads via T3Connection); search box in PortableScreen; lang keys.
- 2026-11-07 feat/site RESTART — new client entrypoint SiteClient; commands /t3craft site here|pick|wand|confirm|cancel|undo|move|resume|status; terrain evaluation with growth reserve, leveling, per-expansion snapshots stored in the world folder (undo last expansion, move rebuilds modules elsewhere); wand click handler; particle preview.
- 2026-11-07 feat/hud-bar RESTART — new HUD element t3craft:status_bar registered at client init (working / needs you / error counters + 'n waiting · key' badge, GoalBar-style, AgentCraft attribution); additive config (enabled, position, hide when empty) in Settings.
- 2026-11-07 feat/npc-attention RELOAD — NPCs walk over to the player when their thread needs attention (approach state in the NPC brain), configurable (enabled, max distance, max concurrent, cooldown). No new entrypoints, registries, keybinds or commands.
- 2026-11-07 feat/panel-chips RELOAD — model/provider chip on NPC nameplate and panel, branch/worktree in the panel and workstation screen; tolerant decoding; additive config keys to show/hide chips. No new entrypoints, registries, keybinds or commands.
- 2026-11-07 feat/hq-design RESTART — modular HQ plan (dev.t3craft.hq: 6-desk modules per project, archive basement, env colours, deterministic/idempotent layout, HqPlans → BuildPlan, HqAnchorSource for NPCs); command /t3craft debug hq-preview registered at client init. No blocks are placed outside that debug command yet.
- 2026-11-07 feat/workstation RESTART — new client entrypoint WorkstationClient (client UseBlockCallback: clicking a desk computer opens that thread's chat); in-world workstation screens via client display entities (no registered blocks); command /t3craft workstation [on|off]; config block workstations; new lang keys.
- 2026-11-07 feat/survival RESTART — new client entrypoint SurvivalClient and main-init hook (T3Craft.java); crafting recipe t3craft:t3_terminal + recipe-book unlock advancement + 'First Contact' advancement (data pack: /reload is enough for data alone, but the entrypoint needs a restart); GameModePolicy/SurvivalRules config defaults; new lang keys.
- 2026-11-07 feat/portable RESTART — live screen on the held T3 Terminal (first person, both hands) via a vanilla special model renderer t3craft:held_terminal_screen registered at client init + new item model JSON; portable key moved to the shared T3Craft key category (t3craft:general); new config key heldScreen (default on) in Settings.
- 2026-11-07 feat/demo-world RESTART — new client entrypoint DemoClient; "T3Craft HQ" button on title/world-select screens; commands /t3craft demo [confirm|rebuild|status]; demo superflat world (creative) builds the HQ (shed plan for now) at a fixed origin; new lang keys.
- 2026-11-07 feat/builders+feat/npc-behavior+feat/config+feat/devx RESTART — new client entrypoints (BuildersClient, ConfigClientInit, DevReloadClient) and modmenu entrypoint; keybinds (J open settings + 5 unbound T3Craft keys); commands /t3craft debug build-*, /t3craft config, /t3dev; t3craft.json config file; Mod Menu compile-only dependency.
