# Code cleanup plan for mod/, bridge/ and protocol/

**Status:** approved by Facundo on 2026-11-09, decisions in section 7. One GitHub issue per phase (`#N` in section 5). Running.

**Basis:** a read-only survey of `main` at `44d7e167`.
- `mod/` has 811 Java files, about 192.7k LOC:
  - `main` 120.7k, of which `hq` is 95.5k and `hq/citadel` 68k;
  - `client` 57.5k;
  - `hqrender` 12.1k;
  - `uirender` 2.3k.
- `bridge/` has 1.7k LOC of its own code plus 27.6k LOC of vendored code.
- `protocol/` is a single file.

## 1. Findings

**Structure**
- `dev.t3craft.hq` has 74 files flat in the package root: plan core, style families, 32 legacy layouts and the primary layout all live together.
- Cycles:
  - `blocks ↔ hq`;
  - `survival → portable → T3Craft → survival`;
  - `client.t3ui ↔ client.chat`;
  - `client.config ↔ client.demo`;
  - `client.operate ↔ client.screen`.
- `T3CraftClient` acts as a service locator.
- Packages split across source sets:
  - `hqrender` puts `RenderBlocks` in `dev.t3craft.blocks`;
  - `client` puts `HqAnchorSource` and `HqPreviewCommand` in `dev.t3craft.hq`.
- Repeated type names: `Box` (in `hq`, `site` and `npc.behavior`), `Placement` and `BuildPlan` (in `hq` and `build`), `Anchor`, `Rect`.
- The primary layout has two names: `Citadel*` in the code, while the layout id is `hybrid-ringcampus` and the entry class is `RingCampus*`.
- `client.demo` contains the HQ menu.

**Dead code (certain)**
- `CherryKit` (461 LOC) and `ThreadActionsBar` (155).
- `MixedTreeKit` (39) has a single user.
- 4 lang keys are never used.
- `kit_lantern_end.png` and `kit_lantern_side.png` are never used.
- `-Dt3craft.render.palette` is obsolete.
- `build.gradle` declares the `check` dependsOn twice.

**Legacy generations**
- 7 alternative layouts: campus, ring, vertical, ensanche, village, hybrid-towncenter and hybrid-terraces. Together they are about 14.8k LOC in `main`, about 1.8k in `hqrender`, 18 lang keys ×3 and their params. They cut cleanly from the citadel.
- `ClassicStyle` (418) is also legacy.
- **Note:** `HqLayouts.DEFAULT_ID` is still `classic`, and new configs write `hqLayout: classic`. The citadel (`PRIMARY_ID`) only sorts first in the menu.

**Duplicates**
- Block-state helpers in the citadel kits: `barrel()` ×14, `wall` ×10, `lantern` ×9, `stair` ×9, and so on. That is about 600–900 LOC.
- Client text helpers: `clip` ×7 and `wrap` ×7.
- Identical yaw wrap ×4.
- `stableHash` ×2.
- `hq.Box` and `site.Box`.
- `hash`/`mix` helpers in the citadel.

**Bridge**
- Legacy and opt-in: the mod defaults to `Mode.DIRECT`.
- The mod only sends `hello` to it.
- The mod keeps `client.link/` and `Mode.BRIDGE` branches in about 12 files.
- `protocol.md` has drifted from `protocol.ts`, which also declares types that nothing uses.
- Java ports of TS logic are kept in sync by hand: `ThreadState`, `Presentation`, `Visibility`, `Transitions`, `ShellState`.

**Fragile coupling** (a rename the compiler does not catch). Every move must keep these green:
- `fabric.mod.json`: 16 entrypoint FQNs.
- `build.gradle` `mainClass` (`HqRender`, `UiRender`).
- `tools/render/start-daemon.sh` (`dev.t3craft.tools.render.RenderDaemon`).
- `JobRunner`: FQN strings and the paths `mod/src`, `mod/build.gradle`.
- `RenderDaemon.PACKS_CLASS`.
- `Pinned`: the kernel prefixes `dev.t3craft.blocks.` and `dev.t3craft.hq.`.
- `DevReloadClient.WATCHED`, `PortableLinks.CHAT_CANDIDATES`, and the `HitchWatch` prefixes.
- `gen-trace-ptx.py` (path of `TracePtx.java`).
- The paths `remote-submit.sh` sends with tar.
- `THIRD_PARTY_NOTICES.md` (paths of adapted files) and `docs/api.md`.
- The PC daemon's **kernel**: moving or renaming classes in `blocks.*` or the `hq` kernel classes forces a daemon restart on the PC.

## 2. Target structure (mod/)

Keep the four source sets. Remove the packages split across source sets. Keep `hq.citadel.catalog.*` paths stable, because the paused rounds live there.

```
main  dev.t3craft
  T3Craft, T3CraftTab
  t3/            T3 client (unchanged: renaming it costs ~200 imports and gains nothing)
  blocks/        status/ (monitor, lamps, board, merge, archive, console, podium) · kit/ (Kit*) · registration at the root
  build/         unchanged; its BuildPlan/Placement get distinct names
  site/          unchanged; Box merged with hq.plan.Box
  survival/      + PortableItems (breaks the cycle)
  debug/         unchanged (pending decision: in the jar or not)
  hq/
    plan/        BuildPlan, Anchor*, Placement, Box, Palette, Plan*   (kernel)
    style/       HqStyle(s), CherryStyle, …
    layout/      HqLayouts, HqLayout, HqGenerator, HqGeometry, PlanBuilder
    citadel/     + RingCampus* → Citadel* (the layout id "hybrid-ringcampus" stays as is)
      catalog/ hub/ nature/ nodes/ barrio/   (unchanged)
    construct/ lead/ pack/   (unchanged)
client  dev.t3craft.client
  hq/            + hq/menu/ (HqMenu*, HqReset, HqStartGate from demo) + HqAnchorSource/HqPreviewCommand
  text/          TextFit/TextWrap (one clip and one wrap)
  demo/          demo world only
  link/          removed if the bridge is retired
  (the rest stays as is)
hqrender  dev.t3craft.tools.hqrender  + RenderBlocks (out of dev.t3craft.blocks)
```

## 3. Conventions (go into `docs/dev.md` in phase 0)

- **Packages by feature.**
  - No flat package with more than ~40 files.
  - No package split across source sets.
  - `main` never imports `net.minecraft.client`.
- **Suffixes:**
  - `*Init` = ModInitializer.
  - `*Client` = ClientModInitializer only.
  - `*Screen`, `*Renderer`, `*Service`.
  - `*Kit` = block-state palettes and helpers.
  - No `Manager`, `Helper` or `Util`.
- **Product name:** `Citadel` for the primary layout. Config ids are not renamed; if one ever changes, it gets an alias.
- **Language:** code, identifiers and comments in English; Spanish only in `docs/` and `lang/`.
- **Static classes and records:** static utilities are `final class X { private X() {} }`; immutable data is a `record`.
- **Class references:** use class literals (`X.class.getName()`) instead of FQN strings when they share a classloader. When a string is unavoidable (the daemon's classloader), it is a single constant next to a comment saying who uses it.
- **Doc references** take the form `docs/features/<name>.md`.
- **Changelog:** every branch adds its line to `docs/CHANGELOG-dev.md`. Moving an entrypoint or touching `fabric.mod.json` is a `RESTART`.

## 4. How the children work (VPS)

**Phase 0 (coordinator):**
- Create the T3 project `t3craft` in the `vps-env` environment pointing at `/home/user/projects/t3craft`. The DB reset left it with no projects.
- Write the conventions.
- Create `tools/refactor/moves.tsv` (old FQN → new FQN) and `tools/refactor/apply-moves.sh`. The script is plain sed/grep, with no java.

**Each child:**
- `delegate_task` against `vps-env`, Sonnet, effort medium or high, `isolation: "worktree"`, `startFromOrigin: true` on `origin/main`.
- T3 creates its worktree in `~/.t3/worktrees/t3craft/` on a `cleanup/<phase>-<slug>` branch.
- One task per child, with an explicit list of files.
- No gradle, java or PNGs.
- Commit and `git push -u origin <branch>`, nothing more.
- Its last line is: branch, SHA, files touched, risky spots.

**Sync:**
- Children always start from up-to-date `origin/main`.
- If `main` moves while a child works, the coordinator merges `main` into the branch. If there is a conflict, a new child (from the same branch) resolves it with `git merge origin/main`, never a rebase.

**Verification loop (coordinator):**
1. On the Mac, create `verify/<slug>` = `main` + the branch (merge `--no-ff`) and push it.
2. On the PC, fetch it into `~/projects/t3craft` and run `tools/heavy.sh -- bash -c 'cd mod && ./gradlew build hqrenderClasses uirenderClasses'`.
3. If it compiles, fast-forward `main` to `verify/<slug>` and push.
4. If not, the coordinator fixes small errors himself, or a fix child does it on the same branch.
5. Afterwards, delete the branch (local and remote) and the child's worktree on the VPS (clean and merged), and run `git pull --ff-only` on the VPS checkout.

**Concurrency:**
- Up to 4 children at once only in phases 1 and 4, which touch disjoint areas.
- Phases 2, 3 and 5 go **one branch at a time**, because they rewrite imports and registries across the whole repo. During phase 3 no other branch is open (a "move window").

## 5. Phases, tasks and merge order

| phase | task (1 child each) | depends on | compilation on the PC |
|---|---|---|---|
| **0** (#2) | Phase 0 above (coordinator) | Facundo's decisions (section 7) | — |
| **1** zero risk (parallel) (#3) | 1a: remove `CherryKit` and `ThreadActionsBar`, inline `MixedTreeKit`, remove 4 lang keys ×3 | — | 1 per branch |
| | 1b: tool leftovers (`-Dt3craft.render.palette`, double `check`, `JobRunner` skip of test/gametest, 2 `kit_lantern_*` textures, `VENDORED.md`) | — | 1 |
| | 1c: yaw wrap ×4 → `Motion.wrapDegrees`, a single `stableHash` | — | 1 |
| | 1d: FQN strings → class literals (`DevReloadClient`, `PortableLinks`); make `RenderDaemon` and `JobRunner` strings constants; check that `debug`/`client.debug` are off by default (D3) | — | 1 |
| **2** legacy (serial) (#4) | 2a: default layout = citadel; a config or world with a legacy or unknown layout id falls back to the citadel with a log warning, no crash | D1 | 1 + plan report |
| | 2b: remove ensanche + hybrid-towncenter (main, hqrender views, params, lang) | 2a | 1 + plan report |
| | 2c: remove village + hybrid-terraces | 2b | 1 + plan report |
| | 2d: remove campus, ring (+`Growth`) and vertical | 2c | 1 + plan report |
| | 2e: `ClassicStyle` (move `inward()` into `CherryStyle` first) | 2d | 1 |
| **3** packages (serial, move window) (#5) | 3a: end the split packages (`RenderBlocks` → `tools.hqrender`, the 2 client `hq` classes → `client.hq`) | 1d | 1 |
| | 3b: `portable` → `survival` | 3a | 1 |
| | 3c: `hq` plan core → `hq.plan` (**kernel**) | 3b | 1 |
| | 3d: `hq` styles → `hq.style` | 3c | 1 |
| | 3e: `hq.layout` + `RingCampus*` → `hq.citadel.Citadel*` | 3d (and phase 2 if approved) | 1 |
| | 3f: `blocks` → `blocks.status` / `blocks.kit` (**kernel**, together with 3c in a single daemon restart) | 3e | 1 |
| | 3g: `client.demo` HQ menu → `client.hq.menu` | 3f | 1 |
| | 3h: single `Box`; rename `build.BuildPlan` and `build.Placement` | 3g | 1 |
| **4** duplicates (parallel by area) (#6) | 4a: citadel block-state kit consolidation | 3 | 1 + identical plan report |
| | 4b: client `clip`/`wrap` → `client.text` | 3 | 1 |
| | 4c: citadel `hash`/`mix` → `Plot.hash`/`Flora.hash` | 3 | 1 + identical plan report |
| | 4d (optional, one per child): split `CitadelPlacement` (2402), `CitadelOffice` (1865), `RadialCursor` (1756), `PlanReport` (1655), `ConversationPanel` (1641), `T3Screen` (1551), `RequestCard`, `SiteService` | 4a–4c | 1 + identical plan report for citadel |
| **5** bridge (D2: frozen) (#7) | 5a: mark `bridge/` and `protocol/` as legacy and frozen (README and a note at the top of `bridge/` and `protocol.md`), with no code changes. Retiring it is decided when the investigation in #1 (T3 Connect) closes | #1 | — |
| **6** docs (#8) | README (stale: it still describes the bridge as primary), `docs/api.md`, `THIRD_PARTY_NOTICES.md` paths | 5 | — |

There are about 22–26 child tasks in total.
- **Compilation:** each one gets a single gradle `build hqrenderClasses uirenderClasses` on the PC, around 20 s warm, one at a time.
- **Plan report:** phases 2a–2d, 4a, 4c and the citadel splits in 4d also need a before/after plan report (`hqRender` report from `PlanReport`, without images) with an identical signature. Facundo approved it with the D4 scope (section 7).
- **Kernel:** phases 3c and 3f change the daemon kernel. The daemon is stopped today, and its next start rebuilds the baseline.
- **Final pass:** a full build plus a smoke test in the game by Facundo, because the RESTART lines are cumulative.

## 6. The paused rounds (`cat/*`, `struct/signposts`, `feat/kit-blocks-art`)

- **Little impact:**
  - phase 1, except 1a, which touches `catalog/tree` (azalea round 2 lives in its own file);
  - phase 2, if the legacy code is not imported.
- **Strong impact:** phase 3, through the imports of `hq.*` and `blocks.*` in the catalog pieces.
- **Mitigation:**
  - Resume or close the paused rounds **before** phase 3, or else apply `tools/refactor/apply-moves.sh` when resuming them (it is mechanical, done by a child with no compiling).
  - `hq.citadel.catalog.*` keeps its paths.

## 7. Decisions (Facundo, 2026-11-09)

- **D1:** the 7 legacy layouts and `ClassicStyle` are retired and **deleted, with no archive tag** (git history keeps them). The citadel becomes the default layout. A config or world with an old id falls back to the citadel with a log warning, no crash.
- **D2:** the bridge is **frozen**: no changes, marked as legacy. Whether it is retired is decided when the T3 Connect investigation closes (#1: A, a native Java client; B, a T3 Code CLI as a backend sidecar; C, the current `direct` mode).
- **D3:** `debug` and `client.debug` stay in the jar, **off by default** (phase 1d checks this).
- **D4:** `hqRender` on the PC is approved **only for plan reports**:
  - only the coordinator runs it;
  - the output is text only (plan signature before and after);
  - one at a time, through `heavy.sh`;
  - no PNGs are made or fetched;
  - it holds while the cleanup lasts.
- **D5:** the paused rounds do not hold up phase 3. They are adapted with `tools/refactor/apply-moves.sh` when they resume.
