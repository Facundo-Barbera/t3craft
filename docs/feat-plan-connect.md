# fix/plan-connect — fences, walls, panes and stairs joined in every HQ plan

## Problem

Builders place an HQ plan without neighbour updates, so a fence, wall, pane, iron bars or stair keeps
whatever shape the plan gives it. The plans gave the default one: loose posts, 2- and 3-sided corners
not joined, stairs without corner shapes. `PlanShapes.resolve` tried to get the shapes from vanilla's
`updateShape` through a dynamic-proxy `LevelAccessor`; any call the proxy does not answer throws, the
exception is swallowed and the cell keeps its default state, so in practice it did not connect.
(`origin/citadel/1-connect` tried the same with a `BlockGetter` proxy, with the same result.)

## Fix: `dev.t3craft.hq.PlanConnect`

An explicit, deterministic post-pass over the final cell map (position → `BlockState`). No world, no
proxy, no `updateShape`, nothing caught. The rules are vanilla 26.3's (`FenceBlock.connectsTo`,
`IronBarsBlock.attachsTo`, `WallBlock.updateShape/shouldRaisePost`, `StairBlock.getStairsShape`), written out:

| Block | Connects towards a neighbour that is… |
| --- | --- |
| fence (`FenceBlock`) | a fence of the same family (wooden ↔ wooden; `nether_brick_fence` only with itself), a fence gate whose axis is perpendicular to the direction, or a sturdy face |
| pane / bars (`IronBarsBlock`, stained panes) | panes/bars, walls, or a sturdy face |
| wall (`WallBlock`) | walls, panes/bars, perpendicular fence gates, or a sturdy face |

* **Sturdy face**: `state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face)` — exactly what
  vanilla asks, and a pure function of the state's shape — minus vanilla's connection exceptions
  (leaves, barrier, pumpkins, jack o'lantern, melon, shulker boxes). So stone, planks, bricks, glass,
  upside-down stair backs… connect; slabs, lanterns, plants, doors, trapdoors, carpets do not.
* **Walls**: a connected side is `TALL` when the collision shape of the block above covers that side's
  arm (a full cube, or a wall connected over it), else `LOW`. `UP` follows `shouldRaisePost`: a post
  above → up; isolated, end, corner, T or cross → up; a straight run → down, unless both sides are not
  tall and something on top needs a post (its collision covers the post column — a lantern, a block — or
  it is a torch, standing sign, banner or pressure plate, vanilla's `#wall_post_override`).
* **Stairs**: `STRAIGHT` / `INNER_LEFT|RIGHT` / `OUTER_LEFT|RIGHT` from the stairs behind and in front
  with the same `HALF`, as `getStairsShape`.
* **Waterlogged** is never touched. Cells outside the map read as air.
* **No block tags** (they may be unbound when a plan is generated headless): families by class.
* **Order**: stairs first (their shape changes which faces are sturdy), then fences and panes (their
  connections never change a neighbour's input), then walls top-down (a wall reads the wall above).

## Where it runs

* `CitadelFinish.build`: `PlanConnect.apply(c.cells)` on the citadel's cell map right after
  `PlanShapes.resolve` (kept: it is harmless and other generators share it).
* `HqGenerator.generate`: `PlanConnect.apply(plan)` on every generated plan, after the layout pack's
  rooms are added — so every layout and style (classic, ring, terraces, campus, citadel…) gets it.

`BuildPlan` is kernel (loaded once by the render daemon) and was **not** touched: the plan-level entry
point uses the existing public `BuildPlan.replacing(...)`, which keeps positions, order, anchors,
modules, workstations, group index and pack hash. Only the placement that holds a cell's final state
(the last one at that position) is reshaped.

Why the old check said nothing: headless (hqRender, the render daemon) block tags are unbound, so
vanilla's `state.is(BlockTags.FENCES / WALLS)` is false and `updateShape` wants fences and walls
*disconnected*; `PlanProbe.shapeMismatches` now compares against `PlanConnect` instead (no proxy, nothing caught).

## Check: hq-render report

`report.txt` has a **CONNECT** section (`report.json` → `connect`): fences/walls/panes connected on no
side although a neighbour they connect to (`PlanConnect.connectsTo`) is beside them. It must be 0. The
count catches nothing per cell; an exception fails the section visibly ("could not be computed").

The renderer (`BlockModels`) already draws multipart blockstates (`when` with `OR`/`AND`/`|`/`!`), so
the connected sides of fences, walls and panes show in hqRender with no renderer change.
