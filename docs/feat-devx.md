# feat/devx: CI, hot-swap safety net, GameTests, dev docs

Branch `feat/devx`, based on `main` at `fc38fd2`. Scope: build/tooling and `dev.t3craft.debug`
(plus its client glue `dev.t3craft.client.debug`). No other module's code changed.

## What was done

1. **CI** (`.github/workflows/mod.yml`): on push/PR touching `mod/**` (and manual dispatch):
   Temurin 25 + Gradle cache, `./gradlew build` (compile, JUnit, compile GameTests, jar), uploads
   `mod/build/libs/*.jar` as `t3craft-mod-<sha>`, test reports on failure. Second job runs the client
   GameTests under xvfb, `continue-on-error`, uploading logs/screenshots/crash reports.
2. **Hot-swap safety net** (`mod/src/main/java/dev/t3craft/debug`, pure Java, JUnit-tested):
   - `Resettable` + `ResetReason` (`REDEFINITION`, `RECOVERY`, `MANUAL`): the generic reset hook.
   - `DevReload`: registry keyed by id (re-registering replaces), `resetAll` isolates each failing
     reset and ignores re-entrant calls, `checkForRedefinition` compares `ClassFingerprint`s of
     watched classes and resets on change.
   - `ClassFingerprint`: hash of declared fields/methods/constructors/nested classes.
   - `ModuleGuard`: try/catch around a module's tick, logs (full stack first, then rate-limited),
     one `RECOVERY` reset per failure streak for `ClassCastException`/`LinkageError`, auto-disable
     after 3 consecutive failures; manual/redefinition resets re-enable, recovery resets do not.
     `OutOfMemoryError` is rethrown.
   - Client glue (`mod/src/client/java/dev/t3craft/client/debug`): `DevReloadClient` is a **second
     client entrypoint** (one line added to `fabric.mod.json`) that polls every 40 ticks in dev or
     with `-Dt3craft.devreload=true`, watches the known state holders by class name (detection only),
     and registers `/t3dev status|reset|check|enable <m>|disable <m>`. `GuardedEvents` wraps
     Fabric tick callbacks in `ModuleGuard`.
3. **Client GameTests**: viable in 26.3 (`fabric-client-gametest-api-v1` 6.0.12 ships in Fabric API
   0.165.2, experimental). Loom `fabricApi.configureTests` with only client GameTests enabled;
   `src/gametest` is a separate mod (`t3craft-gametest`, not in the jar). `check` compiles it;
   `./gradlew runClientGameTest` runs it. The smoke test checks: entrypoint ran, a throwing tick
   module is contained and disabled after 3 failures while the game keeps ticking, a manual reset
   with a world loaded retries it once, no false redefinition.
4. **`docs/dev.md`**: how to iterate (commands, CI, GameTests, hot-swap with JBR, how a module
   subscribes, rules for hot-swap-friendly state).
5. Tooling: `runClient -Pt3craft.hotswap` adds `-XX:+AllowEnhancedClassRedefinition` and
   `-Dt3craft.devreload=true` (JBR only, hence opt-in). `.gitignore`: `mod/logs/`.

Verified: `./gradlew build` in `mod/` green (JUnit: 16 new tests, plus the existing ones). The client
GameTests compile but were **not run** (they boot Minecraft; not allowed on the VPS). First real run
will be the CI job.

## Integration points (for the owners of each module; not done here)

The ClassCastException in `NpcRegistry` is only fully fixed once its owner subscribes. Suggested
changes, all in `T3CraftClient.onInitializeClient()` / the module itself:

```java
// T3CraftClient: reset NPC state and ask the source for a fresh snapshot.
DevReload.get().register("npc-registry", reason -> {
	npcs.resetForReload(Minecraft.getInstance());
	if (started) reconnect();   // or refreshDirect(...) in direct mode
}, NpcRegistry.class, ThreadAvatar.class, NpcInfo.class);

// T3CraftClient: guard the tick body instead of calling it bare.
ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("t3craft.tick", mc -> { ...existing body... }));
```

```java
// NpcRegistry: like clear(), but the maps are emptied even if removing a stale entity throws.
public void resetForReload(Minecraft mc) {
	try { clear(mc); } finally { spawned.clear(); signs.clear(); npcByEntityId.clear(); wanted.clear(); level = null; anchor = null; dirty = true; }
}
```

Other holders that should subscribe the same way: `PeekHud` (static `text`/`banner`: JDK types, low
risk), `DirectController`/`ShellState` (snapshot maps), any new registry. HUD render callbacks can
use `ModuleGuard.get().run("hud.peek", ...)`. `UseEntityCallback` handlers can use
`ModuleGuard.get().call("npc.use", () -> ..., InteractionResult.PASS)`.

If another branch renames the classes listed in `DevReloadClient.WATCHED`, they are skipped (looked
up by name), not a compile error; update the list when convenient.

## Pending

- First CI run (needs the branch pushed and Actions enabled on the repo); then decide whether the
  GameTest job becomes blocking.
- Owners to subscribe `NpcRegistry` and guard `T3CraftClient`'s tick (snippet above).
- Bridge (`bridge/`, vitest) is not in CI yet; a small Node 22 + pnpm job would cover it.
- Possibly a GameTest that spawns fake NPCs through `NpcRegistry.replaceAll` and asserts a reset
  leaves no client-only entities in the level, once the registry subscribes.
