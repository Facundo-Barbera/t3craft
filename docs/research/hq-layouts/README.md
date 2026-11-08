# HQ layouts (feat/hq-layouts)

The HQ can be built with six ground plans. Each one has its own README and renders in this folder
(`campus/`, `ring/`, `vertical/`, `blocks/` for ensanche, `village/`).

| id         | name (es)            | origin branch       | large |
|------------|----------------------|---------------------|-------|
| `classic`  | Clásico              | main (hub + spine + wings) | no |
| `campus`   | Campus               | `layout/campus`     | yes   |
| `ring`     | Anillo               | `layout/ring`       | no    |
| `vertical` | Vertical             | `layout/vertical`   | no    |
| `ensanche` | Ensanche (manzanas)  | `layout/blocks`     | no    |
| `village`  | Aldea                | `layout/village`    | no    |

## API (for the menu thread, feat/hq-menu)

- `dev.t3craft.hq.layout.HqLayoutInfo(id, nameKey, descKey, large)`
- `dev.t3craft.hq.layout.HqLayouts`: `DEFAULT_ID = "classic"`, `all()` (menu order), `find(id)`, `resolve(id)`
  (unknown or null → classic), `CONFIG_KEY = "hqLayout"`, `active()` (what plans are generated with).
- Client side, package `dev.t3craft.client.hq.layout` (the HQ world and its marker are client-side):
  - `HqLayoutState.builtLayoutId()` – layout the current HQ world is built with, or `null` (not in the
    HQ world / nothing built). Also `builtLayoutId(Path worldRoot)`. Stored in the world's
    `t3craft-demo.json` marker as `state.layoutId`; an HQ built before layouts counts as `classic`.
  - `HqLayoutConfig.selected()` / `selectedId()` / `select(id)` – the `hqLayout` config key.
  - `HqRebuild.refusal()` and `HqRebuild.resetAndRebuild(id)` – saves `hqLayout`, then runs the
    existing `/t3craft debug reset-hq` reset (HQ world only, zip backup unless `demo.resetBackup`
    is false); the new world is built with that layout. Ask the user before calling it.

## Rules

- A built HQ keeps its layout. Choosing another one in the config does not touch it (the demo says
  so in chat once); it is applied only by a reset + rebuild.
- `/t3craft demo rebuild` rebuilds with the layout the world already has.
- One visual style: every layout paints with the cherry palette (`HqStyles.forLayout`); campus,
  ensanche, vertical and village keep their own per-barrio / per-tower / per-hamlet materials.
  `classic` still honours `hq.style` (cherry by default) and is byte-for-byte the HQ of main
  (same allocation, plan, build key and demo hash).
- Mechanism: one hook in `HqStyle` – `allocate`, `ownLayout`, `layoutPlan`, `envelope`, `bottom`,
  `top`. `HqStyles.forLayout(id)` / `current()` pick the style; `HqPlans`, `HqGenerator`, bindings,
  NPC anchors and the demo builders follow `HqLayouts.active()`. The village keeps its own record
  and is stored in `HqLayout` by `VillageStyle` (hamlet on plot p = modules `p*4 + part`).
- The survival site (`hq/construct`) asks the active layout but keeps the classic spine for the
  own-layout ones: they do not fit its 64-block envelope and one-slot-per-wing frame.

## Render

`./gradlew hqRender -PhqArgs="--layout all --projects 4"` (or `--layout ring --projects 4,10,20`)
writes `layout_<id>_<n>p_aerial.png` and `_plan.png`.
