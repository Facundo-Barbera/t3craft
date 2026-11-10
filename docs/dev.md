# Iterating on the mod

Everything below runs from `mod/`. Java 25 is required; Gradle downloads a toolchain if the JDK
that runs it is not 25 (foojay resolver in `settings.gradle`).

## Fast loop

The mod runs in **direct mode** by default (it pairs with each T3 environment itself, `/t3craft pair`); nothing else
needs to run next to it. The bridge (`bridge/`) is legacy and frozen (`bridge/README.md`): you do not need it to
develop or test the mod.

**Este repo no usa tests unitarios; prohibido agregarlos o correrlos. Verificación = compilar.**
Gradle blocks them outright (`mod/build.gradle`): no test source set, every `Test` task disabled,
and `check`/`build` only compile.

| What | Command | Boots Minecraft? |
|---|---|---|
| Compile (verification) | `./gradlew compileJava compileClientJava` | no |
| Compile + jar | `./gradlew build` | no |
| Play / debug locally | `./gradlew runClient` | **yes** |

Where these run (see `AGENTS.md`, "Where things run"): Gradle runs **only on the PC**, one at a
time, through `tools/heavy.sh`, launched by the coordinator. The VPS runs no Gradle, java or Minecraft at
all; child threads there only write code and push. On your own machine the daemon is fine.

The PC has **two sides**, and they do different jobs (see "UI cycle" below):

| Side of the PC | Runs |
|---|---|
| **Windows, native** | The **interactive dev client** (`runClient`) and the IDE, both on the same side. Launched by the owner of the machine, not by the coordinator |
| **WSL** | **Renders only** (the render daemon and its relay), plus the Gradle the coordinator runs through `tools/heavy.sh` |

Do not run the dev client inside WSL: with WSLg, OpenGL either falls back to software or pays a
frame copy out of video memory on every frame (a reported ceiling around 72 fps), and there are open
reports of instability. Keep it on Windows.

`tools/heavy.sh` is a Linux script whose lock lives on the WSL side, so a Gradle started from
Windows **does not take that lock**. Avoid building on both sides of the PC at the same time.

## Builds

There is no hosted build service: GitHub Actions is off for this repo and must stay off (it bills
minutes and storage). A jar comes from a build on the PC (`mod/build/libs/`) or from your own
`./gradlew build`.

## Code conventions (Facundo, 2026-11-09; cleanup plan `docs/plans/code-cleanup.md`)

- **Source sets:**
  - `main`: common code; it never imports `net.minecraft.client`.
  - `client`: client-only code.
  - `hqrender` and `uirender`: tools, never in the jar.
  - A package never spans two source sets.
- **Packages by feature.** No flat package over ~40 files: split it into subpackages by responsibility.
  `hq.citadel.catalog.*` keeps its paths (the design rounds live there).
- **Suffixes:**
  - `*Init` is a `ModInitializer`; `*Client` is a `ClientModInitializer` only.
  - `*Screen`, `*Renderer` and `*Service` mean what they say.
  - `*Kit` is a block-state palette or helper set.
  - No `Manager`, `Helper` or `Util`.
- **Names:**
  - The primary HQ layout is the **Citadel** in code. Config and layout ids (`hybrid-ringcampus`) are never renamed;
    a new id gets an alias.
  - Inside `hq.*`, new classes do not repeat the `Hq` prefix.
- **Language:** code, identifiers and comments in English; Spanish only in `docs/` and `lang/`.
- **Shapes:**
  - Immutable data is a `record`.
  - Static helpers are `final class X { private X() {} }`.
  - No commented-out code and no `System.out` outside the tools source sets.
- **Class names in strings:** prefer class literals (`X.class.getName()`) so the compiler catches renames. Where a
  string is unavoidable (the render daemon's class loader), it is one constant with a comment that says who reads it.
- **Moves:** every package move or class rename adds `old.FQN<TAB>new.FQN` lines to `tools/refactor/moves.tsv`, in
  the same commit. `tools/refactor/apply-moves.sh` replays them on an older branch (plain sed, no java).
- **Doc references** in code use `docs/features/<name>.md`.
- **Changelog:** every branch adds its line to `docs/CHANGELOG-dev.md`. Touching `fabric.mod.json`, registries or
  entrypoints is `RESTART`.

## UI cycle

The recommended loop for screens, the HUD and anything else visual. Plan:
`docs/plans/ui-browser-and-integrations.md`, "Frente A".

### 1. Iterate on the PC, on Windows

- The dev client and the IDE both live on **Windows** (see the table under "Fast loop"). Same side of
  the machine, or the debugger and the paths cross a boundary for nothing.
- Start the client **from the IDE in debug mode**, never from a bare terminal: that debug connection
  is what pushes recompiled classes into the running game. See "Hot-swap".
- In IntelliJ set **Build and run using: IntelliJ** (not Gradle). With Gradle the usual symptom is
  "0 classes reloaded".

### 2. What reloads without restarting

| Change | How to apply it | Needs |
|---|---|---|
| Method bodies (layout maths, draw calls, conditions) | IDE: *Build Project* / *Reload changed classes* | Client started in debug mode |
| Textures, GUI sprites, their `.mcmeta` nine-slice, models, fonts, `lang` | **F3+T** in game | Nothing. Works today |
| UI colours and metrics (`ChatTheme` tokens in `assets/t3craft/gui/ui-style.json`) | **F3+T** in game | Nothing. See "UI tokens" below |
| Datapack data | `/reload` | Server-side only, not used by the UI |
| Fields, records, new methods, nested classes | Restart, or JBR (see "Shape changes") | — |

Fabric offers nothing beyond this for code: there is no official hot reload past the JVM's own
HotSwap. `-XX:+AllowEnhancedClassRedefinition` is **not** a Fabric feature; it belongs to JetBrains
Runtime, a separate JDK.

Keep this in mind when a visual tweak seems not to apply: colours and metrics that live in Java
`static final` fields or in static initialisers are **not re-run** by a hot-swap. Values read from a
resource through the resource manager are the ones F3+T can refresh.

### 3. Look at the result on the Mac (default)

The build happens on the PC and the game runs natively on the Mac:

1. On the PC: `./gradlew build` → `mod/build/libs/`.
2. Copy that jar to the Mac over the private network and drop it in the `mods/` folder of a normal
   Minecraft instance (same Minecraft version, Fabric loader, Fabric API).
3. Play there.

Why this and not screen streaming: the image is **pixel-exact** (no video compression) and the game
runs natively on the Mac. The trade-off is that a plain instance is **not** the Loom dev client, so
there is no HotSwap there — F3+T still works for resources. Fast iteration stays on the PC; the Mac
is where you judge the result.

Transport note: the PC's SSH server uses `wsl.exe` as its shell, so `scp` and `rsync` against it do
not work. The pattern that does is the one the render relay already uses — feed a script on stdin
and read the payload back, e.g. `ssh -T <pc> < script.sh > mod.jar.b64`. From the WSL side the
Windows checkout is reachable under `/mnt/<drive>/…`.

### 4. Judging pixels

Minecraft's font is an 8 px bitmap, so small text and 1 px rules are exactly what video compression
destroys. Judge kerning, 1 px alignment and edges from the Mac instance above or from a screenshot
(`F2` writes a PNG), never from a streamed picture. Screen streaming is a fallback for interactivity,
not the default, and it needs a direct connection on the private network to be worth it.

### 5. Seeing a screen without walking to it

`uiRender` (`docs/tools/ui-render.md`) paints `T3Screen` and its dialogs to PNG with mock data and no
game at all. Treat it as a **layout check**, not a design tool: it allocates `Minecraft` without
running its constructor, paints with Java2D, and has no Unifont, shaders, IME or clipboard.

### 6. UI tokens: restyle with F3+T (#12)

The colours and metrics of the chat kit (`ChatTheme`: panel, ink, accent, status colours, row heights,
margins…) live in `mod/src/main/resources/assets/t3craft/gui/ui-style.json`, not in Java. A Fabric reload
listener (`UiStyleReloader`) re-reads it on every resource reload, so **F3+T restyles the open screens**
(`T3Screen`, the archive, the wall boards) with no HotSwap, no debugger and no restart.

- Two themes: `warm_studio` (the paper console, the default) and `terminal` (dark teal, flat fills).
  Keys are the `ChatTheme` component names; colours are `"#RRGGBB"` or `"#AARRGGBB"`, metrics are numbers.
- A missing key keeps its built-in value (`ChatTheme.builtinWarmStudio()` / `builtinTerminal()`), so a
  resource pack can override a single colour. **When you add a token to `ChatTheme`, add its key to the file
  and its built-in value in Java** (the file is the one you edit; the Java value is the fallback).
- Bad values are skipped with a WARN in `latest.log` (`t3craft/ui-style`); a file that is not valid JSON is
  ignored whole and the previous style stays, so a typo while editing never blanks the UI.
- Edit the file in the source tree, rebuild resources (`./gradlew processResources`, or the IDE's build),
  press F3+T. Never hard-code a colour in a screen: add a token.
- Screens read `ChatTheme.warmStudio()` / `terminal()` (same instance until a reload). A screen that keeps
  the theme in a field compares `theme != ChatTheme.warmStudio()` in `tick()` and re-lays itself (see
  `T3Screen.syncTheme`).

### 7. The UI gallery: `/t3dev ui <scenario>` (#13)

`/t3dev ui` lists the scenarios and `/t3dev ui inbox` opens one, with **fake data and no T3 or network**
(`client/debug/UiGallery`). After a restart you are one command away from the exact screen you were
tuning; combined with F3+T it is the fast loop for visual work.

| Scenario | Opens |
|---|---|
| `inbox`, `inbox-readonly`, `inbox-empty` | The main T3 screen: every thread state and a running conversation / read-only / no threads |
| `inbox-settings`, `inbox-environments` | The main T3 screen with the settings / environment-list page in the thread's place (what the sidebar's gear and cloud open) |
| `settings`, `settings-notifications`, `settings-connections`, `environments` | The same pages as screens of their own (a tab each; fake environments) |
| `launch` | The main T3 screen with a new-thread draft open in the composer (what "New thread" does; needs a paired environment with projects, otherwise it shows the plain inbox) |
| `confirm`, `snooze` | Kit dialogs: the destructive-action confirmation and the snooze presets |
| `pair`, `pair-readonly`, `operate-pair` | Kit dialogs: pairing (control by default / read-only) and enable-control |
| `inbox-icons` | The main T3 screen with every sidebar and header icon in use (#27): pinned stars, folder and dock chevrons, Working / Snoozed / Settled, a worktree row, the agents chip with active and finished children |
| `icons` | The icon gallery (#24): every `T3Icon` in a grid on both themes, 12 px in its three states and 32 px; the wheel scrolls (`docs/tools/icons.md`) |
| `model-picker`, `composer-icons` | The composer's model picker open over a fake catalog; the composer with its icons (paperclip, thumbnails, error line, model chip, send) over a sender that sends nothing (#29) |
| `queue` | The queued-runs screen (reads the live queue) |

The fake environment is `t3dev-gallery`; its transcripts never reach the disk cache. Real things stay
real: `settings` saves the actual config, and an action aimed at the fake environment fails with the usual
error toast. To add a scenario, add a `Scenario` to `UiGallery.SCENARIOS`. The scenarios are written in
code because the `uiRender` fixtures live in a tool source set that is not in the mod jar; `uiRender`
remains the headless layout check for agents (it has no fixtures for the kit's pages and dialogs yet).

### 8. The UI kit (#14)

Settings, Environments, Pair, OperatePair, Confirm and Snooze are built on a small kit
(`client/t3ui/kit`: button, toggle, field, select, list, tabs, dialog, panel) drawn with the `ChatTheme`
tokens, so a colour or metric change in `ui-style.json` restyles them with F3+T. Settings and Environments
are pages that open inside `T3Screen` (and as screens of their own); the others are modal dialogs. How it is
built, how to add a control or a page, and what is left: `docs/features/ui-kit.md`.

Icons (#24) are Lucide SVGs rasterised by `./gradlew generateIcons` into tinted GUI sprites (`T3Icon`,
`T3Button(icon, …)`, `T3IconButton`); see `docs/tools/icons.md`.

## Hot-swap

### Why a hot-swap can do nothing at all

Before blaming the mod, check these. They are the reasons this loop is easy to get wrong:

1. **Nothing is pushing the bytecode.** `DevReloadClient` only *detects* that a class changed shape
   and resets state; it never redefines a class. Launched with a bare `./gradlew runClient` there is
   no debugger attached and **no class is ever reloaded**. Start from the IDE in debug mode.
2. **IntelliJ is building with Gradle.** Switch *Build and run using* to IntelliJ, or expect
   "0 classes reloaded".
3. **`-Pt3craft.hotswap` on an ordinary JDK.** That flag only exists on JetBrains Runtime, so the JVM
   **refuses to start** and the game never opens. Leave the flag off until JBR is actually in place.
4. **IDE and game on different sides of the machine** (for example IDE on Windows, game in WSL).
   Keep both on Windows.
5. **The change is in a static initialiser or a `static final`.** Those do not re-run; see "What
   reloads without restarting".

### Method bodies (any JDK)

Run `runClient` from the IDE in debug mode (IntelliJ: Gradle run configuration `Minecraft Client`
generated by Loom, debug ▶) and use "Reload changed classes" / build. Plain JVM HotSwap only
accepts method-body changes, which do not invalidate in-memory state. Fabric's own docs point at this
for "screen element alignment", which is most of the UI work.

### Shape changes (JetBrains Runtime)

Adding/removing fields, records, nested classes requires enhanced redefinition:

1. Install a JetBrains Runtime 25 (JBR) and point Gradle at it, e.g. in `~/.gradle/gradle.properties`:
   `org.gradle.java.installations.paths=/path/to/jbr-25`.
2. Make Loom run the client on JBR (IDE run configuration → JRE = JBR) and start with
   `./gradlew runClient -Pt3craft.hotswap`, which adds `-XX:+AllowEnhancedClassRedefinition` and
   `-Dt3craft.devreload=true`. Without JBR the JVM refuses to start with that flag, which is why it
   is opt-in.

This path is optional and it is the fragile one: enhanced redefinition is a JetBrains extension with
open crash reports, and one of them triggers when it is combined with `-XX:+UseCompactObjectHeaders`
(adding an instance field can leave objects on the old class, giving a `ClassCastException` or a hard
crash). That option is **off by default on JDK 25**, so simply do not turn it on. Reach for JBR only
if you change class shape often; otherwise method bodies plus F3+T cover the UI loop.

This is where stale state used to crash the game (a `ClassCastException` in `NpcRegistry` after a
reload: objects of the old class layout still sitting in its maps). The safety net in
`dev.t3craft.debug` handles it:

- **`DevReloadClient`** (its own client entrypoint) polls every 40 ticks, in a dev environment or
  with `-Dt3craft.devreload=true` (`=false` turns it off), whether any watched class changed shape
  (`ClassFingerprint`: fields, methods, constructors, nested classes). If so it calls
  `DevReload.resetAll(REDEFINITION)`.
- **`Resettable`**: each module that keeps state registers a reset hook; on reset it drops its
  maps/entities and rebuilds on the next tick.
- **`ModuleGuard`**: guarded tick work never propagates an exception into Minecraft. It logs, runs
  one `RECOVERY` reset if the error looks like stale state (`ClassCastException`, `LinkageError`),
  and auto-disables the module after 3 consecutive failures.

In game: `/t3dev` (status of state holders and modules), `/t3dev reset`, `/t3dev check`,
`/t3dev enable <module>`, `/t3dev disable <module>`.

### How a module subscribes

```java
// 1. Reset hook: id is the key (re-registering replaces), then the classes the state depends on.
DevReload.get().register("npc-registry", reason -> npcs.clear(Minecraft.getInstance()),
        NpcRegistry.class, ThreadAvatar.class, NpcInfo.class);

// 2. Guard its ticks (module name shows up in /t3dev and in the logs).
ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("npcs", npcs::tick));
// anything else:
ModuleGuard.get().run("hud.peek", () -> PeekHud.render(...));
int n = ModuleGuard.get().call("hud.count", () -> compute(), 0);
```

Rules of thumb for hot-swap-friendly code:

- No static field whose *type* may change between versions without a reset hook clearing it.
  Static holders of JDK types (`String`, `int`, `Map<String, String>`) are fine; static holders of
  your own records/classes need a `Resettable`.
- A reset must be idempotent, non-blocking, and must not throw (it is isolated anyway).
- Keep state rebuildable from the source of truth (config, T3 snapshot; the bridge is legacy) so a reset is
  cheap: clear, mark dirty, let the next tick rebuild.
- Do not change `Resettable`'s shape: it is the one type shared across hot-swaps.
- New fields on entrypoints (`T3CraftClient`, `*Client`) must be lazy or null-guarded:
  `onInitializeClient` (and field initializers) do not run again after a hot-swap, so a field
  added then is `null` on the live instance. Read it through a getter, e.g.
  `return helpers = HotSwapFields.orInit(helpers, HelperDirector::new);`, or check for null
  in every tick/render/callback that uses it.

## Performance

### Debug tooling switches (decision D3: off in a normal game)

`dev.t3craft.debug` and `dev.t3craft.client.debug` stay in the jar. The `/t3dev` commands are always
registered; only the background work is opt-in. `ModuleGuard` (try/catch around module ticks) is a safety
net, not debug tooling, and is always on.

| Switch | Controls | Default |
|---|---|---|
| `-Dt3craft.perf=true\|false` | `PerfStats` counters and step timers (`/t3dev perf`, `perf steps`), the once-a-second roll, the `[perf] slow step` WARN | on in a development environment, off otherwise |
| `-Dt3craft.hitches=true\|false` or `config/t3craft.json` `"debug": {"hitches": …}` | `HitchWatch` frame/tick watchdog, its sampler thread and its tick/render callbacks (the property wins) | on in a development environment, off otherwise |
| `-Dt3craft.devreload=true\|false` | hot-swap polling every 40 ticks and the watched class list (needs JetBrains Runtime for shape changes) | on in a development environment, off otherwise |

With a switch off, `/t3dev perf` and `/t3dev perf hitches` say so and show nothing; `/t3dev status`, `reset`,
`check`, `enable` and `disable` keep working.

### In game: `/t3dev perf`

`/t3dev perf` (needs `-Dt3craft.perf=true` outside a development environment) prints the FPS and every counter
that moved during the last second (`dev.t3craft.debug.PerfStats`, rolled once a second from the client tick). `/t3dev perf reset`
zeroes them. Timers end in `.ns` and print as **ms per second** (red when above 20 ms/s, i.e. more
than 1 ms per client tick on average):

| Counter | What | Healthy steady HQ |
|---|---|---|
| `tick.<module>` | client tick time of each guarded module (`GuardedEvents`), plus `tick.npcs`, `tick.helpers` | a few ms/s each |
| `nav.searches`, `nav.nodes`, `nav.failed` | A* searches started, nodes expanded, searches without a route | ~0 once NPCs sit at their desks |
| `nav.miss_hits`, `nav.deferred` | asks answered from remembered misses (backoff); asks deferred by the node/time budget | `deferred` ~0 |
| `nav` (timer) | time spent searching | ≤ 40 ms/s (2 ms per tick cap) |
| `monitor.glyph_rebuilds` | monitor text re-laid out | 0/s unless a thread changes |
| `monitor.drawn`, `monitor.culled` | monitors in range drawn / skipped (seen from behind, behind the camera) per second | culled > drawn when you look away |
| `lamp.drawn`, `lamp.culled` | lamp BER per frame, drawn vs skipped | — |
| `display.set_text`, `display.spawned` | `setText` on our display entities, display/interaction entities created | ~0/s steady |
| `lamp.sweeps`, `lamp.writes`, `binding.writes` | server: `LampApplier` sweeps, lamp states written, HQ bindings written | sweeps ½/s, writes 0 steady |

To bisect a module: `/t3dev disable <module>` and watch the FPS (and `/t3dev enable <module>`).

### Hitches that no step explains: `/t3dev perf hitches` (fix/new-thread-hitch)

`dev.t3craft.debug.HitchWatch` times every frame (end of one level render to the next, client ticks
included), every client tick and every integrated-server tick, independent of the `PerfStats` steps.
A daemon thread takes `Thread.getStackTrace()` of the render and server threads every 5 ms, **only
while** the current frame / server tick already ran over 30 ms (normal frames cost two `nanoTime`).
A frame or tick over 50 ms logs one WARN in latest.log:

```
[hitch] frame 184.2 ms on Render thread (client tick inside: 171.0 ms); 31 sample(s) every 5 ms after 30 ms; GC 12 ms; fps 58
  ours (dev.t3craft, inclusive):
    28/31 client.npc.NpcRegistry.reconcile:212      <- our method on the stack in 28 of 31 samples
    ...
  deepest game frame (net.minecraft / dev.t3craft / blaze3d):
    19/31 net.minecraft.world.level.lighting.LevelLightEngine.runLightUpdates:...   <- what was really running
  leaf:
    ...                                              <- raw top of the stack (JDK / LWJGL / park = waiting)
  most common stack: <deepest ours> → <deepest game frame> → <leaf>
  server thread meanwhile: ...
  during: thread+ env:abc [Render thread, -40 ms]; step build.job_new 6.1 ms [Server thread, +12 ms]
```

How to read it: `ours` = which of our methods the time went through (inclusive; the first line is
usually the culprit's caller); `deepest game frame` = the engine or our code at the very top of the
stack (light engine, chunk section rebuild, texture upload, JSON, file I/O…); `leaf` showing
`Unsafe.park` / `Object.wait` = the thread was *waiting* (look at `server thread meanwhile`); no
samples + GC = a garbage collection pause. `during` lists the marks (`HitchWatch.mark`: thread
appeared, NPC / workstation spawned, crew spawned…) and every timed step of 2 ms or more from 250 ms
before the hitch to its end. Frames stretched by the game's frame limiter (unfocused / AFK) are
dropped. At most 4 detailed reports per 5 s; the rest are counted in the next one.

`/t3dev perf hitches` lists the last 20 (time, kind, ms, top method). Config (additive, not written):
`config/t3craft.json` → `"debug": {"hitches": true, "hitchMs": 50}`; default on in a dev
environment, `-Dt3craft.hitches=false` turns it off.

### fix/hq-fps: what made the FPS drop after the HQ was built

Static review of every per-tick / per-frame callback plus work-count tests and microbenchmarks
(`NavigationBudgetTest`, `MonitorWorkCountTest`, `RenderLodTest`, `PerfStatsTest`):

1. **Pathfinding (main cause).** Once the HQ stands, every NPC gets a desk inside it. When there
   is no route (closed door, unfinished wing, `fix/npc-enter-hq`), each NPC re-ran a full A*
   (up to 16,000 nodes) every 40 ticks, forever, and a node over real collision shapes cost
   **~33 µs** (6 body sweeps × ~8 block lookups with `Long` boxing per neighbour, 16 neighbours).
   The shared 2,500-node tick budget was then always spent: up to **~80 ms per client tick** on
   the render thread. Fixed:
   - misses back off exponentially per route (40 → 80 → … → 1,200 ticks; `Navigation.missTtl`),
     kept across `blocked()` (which only makes them retry now);
   - a **2 ms per tick** wall-clock cap on top of the node budget (`Navigation.forLevel`,
     checked every 64 nodes);
   - `Collision.passage` fast path (one bounding-box query for the whole sweep on open floor)
     and fastutil primitive maps for the block/floor/score caches: **33 → 12 µs per node**.
   - Before/after (10 NPCs, desk unreachable, 2 min): 600 → 60 searches, ~1.2 M → 125 k nodes;
     worst tick ~80 ms → ~2–3 ms.
2. **Lamps.** `LampRenderer.shouldRenderOffScreen()` is true (the beacon's beam), so vanilla never
   frustum-culled *any* lamp, and every lamp within 160 blocks was drawn every frame. Now
   `shouldRender` decides per lamp (`RenderLod.lamp`): plain lamps within 48 blocks and not behind
   the camera, halos within 20; only the beacon keeps 160 blocks.
3. **Monitors.** The renderer from `feat/monitor-live-log` draws "off screen" (panels draw from their
   origin block) with its own NEAR/FAR LOD, so vanilla never frustum-culled any monitor: every
   monitor in range was laid out and drawn every frame, also from behind the screen and behind the
   camera. `shouldRender` now skips those (`RenderLod.monitor`: face test on the block, behind-camera
   test on the screen centre with the panel's radius). Unbound/unknown screens are cached instead of
   re-allocated every frame, and a version epoch stops a returning thread from reusing stale glyphs.
4. Small per-tick allocations: the NPC bubble placement re-flattened every panel's text
   (`getString()`) every tick (now only when it changes); `sawLamp` allocated a record per lamp per
   frame (now mutated in place).

Checked and fine (no change): workstation, archive, schedule board and NPC panels push `setText`
only when the content changes; HQ reconciliation runs every 20 server ticks with cached targets and
deduplicated anchor publication; `LampApplier` writes only differing states (sweep every 40 ticks);
`BindingWriter` skips equal bindings; the orphan scan (`entitiesForRendering`) only runs on
recovery, never per tick.

### Rules for future threads

- **Nothing O(n) per frame that can be per change.** BER `extractRenderState` reads cached data;
  rebuild glyphs/models only on a version change (`MonitorScreens.Shown.version`) and count it.
- **Cull in `shouldRender`.** Override it per block (distance + facing); never return true from
  `shouldRenderOffScreen()` without a per-instance `shouldRender` that culls what is behind the
  camera. Add a far LOD (background, core quad) for anything with text or many quads.
- **Display entities:** create once, re-create only when removed or moved, `setText` only when the
  `Component` differs (compare a cached value, not `getText()` of a fresh component per tick).
- **Searches and other open-ended work on the client tick** need a node budget *and* a time cap,
  and failures must back off; never retry a failed expensive operation at a fixed short interval.
- Never scan `entitiesForRendering()` or a chunk per tick; keep references.
- Reconcile on events or with a debounce (≥ 20 ticks), not every tick; write block states/entities
  only when they differ.
- Hot paths: no `Long`/`Integer` boxing in maps (fastutil is on the classpath), no string building
  (`PerfStats` keys are constants), no lambdas capturing per-call state where avoidable.
- Add a `PerfStats` counter to any new per-tick/per-frame path and a work-count test (steady state
  ⇒ 0 rebuilds / 0 `setText`).

## World presence: the HQ gate

Anything the mod adds to a world (client-only entities, displays, NPCs, helpers, workstations,
hologram, beacon, schedule board) must ask `HqPresence.allows(mc)` first and despawn when it turns
false. It is open only on the integrated server, with this world's HQ built, in the HQ's
dimension; on a server without the mod it is always shut, and only the T3 screen (key Y), HUD and
client commands run there. Rules, the per-thing table and the open risks: `docs/features/hq-gate.md`.
