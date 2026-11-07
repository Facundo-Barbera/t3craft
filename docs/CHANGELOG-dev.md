# CHANGELOG-dev

Read by Taller to decide how to refresh Facundo's game after pulling `main`.
One line per structural commit on `main`, newest first:

    - <date> <branch> RESTART|RELOAD — <what changed>

- `RESTART`: close and relaunch the client (new items, registries, entrypoints, keybinds, mixins, dependencies).
- `RELOAD`: in-game reload is enough (resources/lang, config, commands re-registered on rejoin).

## Entries

- 2026-11-07 feat/survival RESTART — new client entrypoint SurvivalClient and main-init hook (T3Craft.java); crafting recipe t3craft:t3_terminal + recipe-book unlock advancement + 'First Contact' advancement (data pack: /reload is enough for data alone, but the entrypoint needs a restart); GameModePolicy/SurvivalRules config defaults; new lang keys.
- 2026-11-07 feat/portable RESTART — live screen on the held T3 Terminal (first person, both hands) via a vanilla special model renderer t3craft:held_terminal_screen registered at client init + new item model JSON; portable key moved to the shared T3Craft key category (t3craft:general); new config key heldScreen (default on) in Settings.
- 2026-11-07 feat/demo-world RESTART — new client entrypoint DemoClient; "T3Craft HQ" button on title/world-select screens; commands /t3craft demo [confirm|rebuild|status]; demo superflat world (creative) builds the HQ (shed plan for now) at a fixed origin; new lang keys.
- 2026-11-07 feat/builders+feat/npc-behavior+feat/config+feat/devx RESTART — new client entrypoints (BuildersClient, ConfigClientInit, DevReloadClient) and modmenu entrypoint; keybinds (J open settings + 5 unbound T3Craft keys); commands /t3craft debug build-*, /t3craft config, /t3dev; t3craft.json config file; Mod Menu compile-only dependency.
