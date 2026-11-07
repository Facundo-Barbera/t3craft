# CHANGELOG-dev

Read by Taller to decide how to refresh Facundo's game after pulling `main`.
One line per structural commit on `main`, newest first:

    - <date> <branch> RESTART|RELOAD — <what changed>

- `RESTART`: close and relaunch the client (new items, registries, entrypoints, keybinds, mixins, dependencies).
- `RELOAD`: in-game reload is enough (resources/lang, config, commands re-registered on rejoin).

## Entries

- 2026-11-07 feat/demo-world RESTART — new client entrypoint DemoClient; "T3Craft HQ" button on title/world-select screens; commands /t3craft demo [confirm|rebuild|status]; demo superflat world (creative) builds the HQ (shed plan for now) at a fixed origin; new lang keys.
- 2026-11-07 feat/builders+feat/npc-behavior+feat/config+feat/devx RESTART — new client entrypoints (BuildersClient, ConfigClientInit, DevReloadClient) and modmenu entrypoint; keybinds (J open settings + 5 unbound T3Craft keys); commands /t3craft debug build-*, /t3craft config, /t3dev; t3craft.json config file; Mod Menu compile-only dependency.
