# hqRender: piece views and plan report

`./gradlew hqRender -PhqArgs="..."` draws the HQ headless (no Minecraft window). Besides the full galleries
(`--style`, `--layout`, see the javadoc of `HqRender`), it can look at **one piece** of the plan and write a
**report** about it. Both exist so a designer of a door or a stretch of wall sees only that, in seconds, and
gets the problems the eye misses. The code is in `mod/src/hqrender/java/dev/t3craft/tools/hqrender/`
(`PieceViews`, `PlanReport`, `PlanSource`, `ImageCompare`); `PlanProbe` (in `dev.t3craft.hq`) is the read-only
bridge to the package-private helpers (`PlanNav`, `PlanShapes`, the group index).

> If `docs/tools/render-service.md` exists (the shared render service), the same flags are documented there
> too, as the service calls this code.

## Flags

A target flag switches to **piece mode**. The plan is the one of `--layout` (default `hybrid-ringcampus`, the
citadel) grown through `--projects` (default `10`; give the whole chain, e.g. `4,10,20`, to grow it the way
the game does, each roster allocated against the previous layout).

| Flag | Meaning |
| --- | --- |
| `--region x1,y1,z1:x2,y2,z2` | That box (plan-relative, inclusive, corners in any order). |
| `--module ID` | A module of `BuildPlan.modules()` by id (`id@...` pieces match too), padded by `--pad N` (default 1). |
| `--group NAME` | The blocks a group owns (`hub`, `module_3`, …; `BuildPlan.groups()`), same padding. |
| `--anchor NAME [--radius N]` | A cube of radius N (default 10) around an anchor; a unique part of the name is enough. |
| `--name NAME` | File-name prefix of the PNGs (default derived from the target). |
| `--cut-y Y` | Height of the plan view cut (default: 5 above the base of the piece). |
| `--section-at N` | Coordinate of the vertical section plane (default: the middle). |
| `--only a,b` | Only the views whose suffix contains a word: `iso_se`, `iso_ne`, `iso_nw`, `iso_sw`, `plan`, `section`. |
| `--scale F` | In piece mode, multiplies the automatic fit (about 1100 px across the piece): `1` = full, `0.5` = small preview. (In the full galleries it keeps its old meaning, default `1.6`.) |
| `--ss N` | Supersampling per axis; `--ss 1` is fastest. |
| `--no-npcs` | No NPC stand-ins. |
| `--compare DIR` | After the run, put each PNG written next to the one of the same name in `DIR` (before / after): `compare_NAME.png`, plus `compare.txt` (pixels that differ, and whether the two `report.json` have the same plan signature). Works in every mode. |
| `--report` | Piece mode on the whole plan. |
| `--report-only` | Report only, no images (whole plan unless a target is given). |
| `--no-report` | Images only. |
| `--no-ticks`, `--ticks-limit N`, `--crew N`, `--bpt N` | Build simulation: skip it; skip it above N entries (default 600000); builders (4); blocks per tick (48). |
| `--low-light N` | Light threshold for walkable cells (default 10). |

Written, in `--out` (default `build/hq-render`): `NAME_iso_se.png`, `NAME_iso_ne.png`, `NAME_iso_nw.png`,
`NAME_iso_sw.png` (four corners), `NAME_plan.png`, `NAME_section.png`, `report.txt`, `report.json`.

```
./gradlew hqRender -PhqArgs="--region -10,0,-10:10,12,10 --scale 0.5 --ss 1"
./gradlew hqRender -PhqArgs="--module module_3 --out build/hq-after --compare build/hq-before"
./gradlew hqRender -PhqArgs="--anchor walk_hub_0 --radius 12 --only plan,iso_se"
./gradlew hqRender -PhqArgs="--report-only --projects 4,10"
```

## Look: lighting, shadows, AO, filter

`Renderer` is a ray caster (CPU, or the RTX through CUDA, see "GPU" below): one ray per sub-pixel walks the block grid and hits the textured quads
of each cell. `BlockModels` builds those quads from the **real vanilla models and textures** in the client jar
(blockstate variants and multipart, parents, element from/to, per-face uv, uv rotation, element rotations with
rescale, variant x/y rotations, `uvlock`, tint indices). Textures are sampled **nearest** (texel-exact, the
Minecraft look); smoothness comes from supersampling. Grass and foliage use the plains colours (no biome map,
the render has no world).

All of the following are flags of every mode (galleries, `--layout`, piece mode, `--catalog`). **They are on by
default (`--final`) and off in `--preview`.** Without either flag, `--ss 1` (what the render service adds to its
previews) counts as a preview. The service passes `--preview` to `hq` previews itself.

| Flag | On (final) | Off (preview) |
| --- | --- | --- |
| `--sky` / `--no-sky` | Linear-light shading: a warm sun from the south-west at about 45°, a cool sky hemisphere plus a warm ground bounce, a soft shoulder tonemap (linear up to 0.6, then eases into white; no toe, so shadows stay readable), and a light sky-gradient background. Faces the model marks `"shade": false` (plants, torches) are lit evenly. | The old flat look: Minecraft-like face factors (top 1, south/north 0.82, east/west 0.68, bottom 0.55) times a sun term, on a flat light-grey background. |
| `--shadows` / `--hard-shadows` / `--no-shadows` | Soft sun shadows: `--shadow-rays N` (default 2) rays per sub-sample toward a sun disc of 3° radius, stratified and jittered by sample, so a 3×3 supersampled pixel averages 18 rays and the penumbra widens with the distance to the occluder. Leaves cast dappled shadows (cutout texels are tested); glass and water do not. | One hard shadow ray (as before). `--no-shadows`: none. |
| `--ao` / `--no-ao` | Ambient occlusion in the spirit of smooth lighting, per pixel: the cells in front of the face are read at **half-block** resolution (contact shadows in joints, under slabs and stairs) and at **block** resolution (the broad gradient at wall bases and inner corners). Each corner of the cell holding the pixel gets Minecraft's vertex rule (two sides plus the diagonal); the four corners are interpolated bilinearly. A cell counts as solid from its opaque model elements (`BlockModels.occupancy`: which of its 8 half-block sub-cells are covered); glass, plants and fences do not occlude. In sky mode AO darkens the sky light fully and the sun only a little. | None. |
| `--bloom` / `--no-bloom` | A faint halo around emissive blocks (lanterns, froglights, glowstone…): their colour, gaussian-blurred (sigma about half a block), screen-blended at 15 %. | None. |
| `--filter mitchell\|tent\|box` | `mitchell`: Mitchell–Netravali (B = C = 1/3) over a 1.25 px radius, resolved in linear light. Edges are cleaner than the box average and stay crisp. | `box`: the plain average of the sub-samples. |

Cost (render PC, 11 threads, `--ss 3`): the hub of `--layout hybrid-ringcampus --projects 4 --only hub`
(6 images) took 13.0 s before and 15.1 s after; the catalog gallery
`--catalog HOUSE:house_timber_cottage,LAMP:lamp_cherry_post` took 2.2 s before and 4.7 s after. The extra cost
is the shadow rays. `--shadow-rays 1` or `--hard-shadows` halves it.

`Renderer.look` is a static that `HqRender` sets at the start of every run. A JVM therefore renders with one
look at a time, which suits the render service (1 worker on the PC).

## GPU

On the render PC the ray tracing runs on the **RTX 3050** (tools/render-gpu). Final looks use it by default; if the GPU is not
there, or anything goes wrong, that image (or the rest of the job) is rendered on the CPU exactly as before. Output is always a
full-colour PNG (no palette).

| Flag | Meaning |
| --- | --- |
| `--gpu` / `--no-gpu` | Force the GPU (still falls back to the CPU if it is missing) / force the CPU. Default: GPU in final looks, CPU in `--preview` (and `--ss 1` without `--final`). |
| `--gpu-compare` | Every image is rendered on both: the GPU one is kept, and the log prints `gpu-compare #N`: CPU and GPU seconds, speed-up, image difference (mean per channel, max, share of pixels over 4 and over 16 of 255) and the share of identical sub-samples. `gpu-compare/NN_cpu.png`, `_gpu.png`, `_diff.png` (difference x8, black = equal) are written next to the other images. |
| `--gpu-no-resolve` | Only the ray tracing on the GPU; the filter, bloom and sRGB stay on the CPU. |
| `--gpu-probe` | Writes `gpu-probe.txt` (and the log) with what the machine offers, see "What was probed". Renders nothing. |
| `--gpu-nvrtc` | Dev: compile `mod/src/hqrender/cuda/trace.cu` with NVRTC instead of using the committed PTX and write `trace.ptx` into the output folder. |
| `--gpu-fail-test` | Dev: the GPU child fails at start, to check the CPU fallback. |

**How it works.** `Renderer.render` hands the scene to `GpuTrace`, which flattens it (the grid of model ids, the quads of every
model, the textures, the look) into a temp file. A **child JVM** (`gpu/GpuChild serve`, one per job, started on the first
image and closed when the job ends) maps the file, uploads it and runs the kernel; a driver crash or a hang costs that child,
never the shared daemon. The child talks to the CUDA **driver API** through Java's foreign function API (`gpu/Cu`, libcuda from
`/usr/lib/wsl/lib`): no CUDA toolkit, no JNI, no LWJGL, no window. Nothing is installed.

The kernel (`mod/src/hqrender/cuda/trace.cu`) is a port of `Renderer.trace`: the same voxel walk, quad intersection, alpha test,
translucent blending, smooth-lighting AO, soft sun shadows (same hash, so the same jitter), flat and sky looks; then
`Renderer.resolve` and the bloom (separable filter taps, gaussian blur, screen blend, sRGB). One thread per sub-sample, in
float, in clip-local coordinates (the ray origin is computed in double and moved to where the ray enters the clip box).
Launches are bands of rows that adapt to take about 100 ms each, so the desktop on the same GPU keeps drawing and the
Windows watchdog never fires. If an image needs more device memory than is free, that image goes to the CPU and the GPU keeps
serving the next ones.

**The kernel is committed as PTX** (`gpu/TracePtx.java`, generated): the driver JIT-compiles it (about 2 s the first time, 16 ms
when the driver's cache has it), so at run time only the driver is needed. To change the kernel:

```
# 1. edit mod/src/hqrender/cuda/trace.cu; a render job on the PC compiles it with NVRTC and keeps the PTX
tools/render/submit.sh --layout hybrid-ringcampus --projects 4 --only hub --gpu-compare --gpu-nvrtc
# 2. embed the PTX it wrote (needs the PC to have a libnvrtc.so.12: T3CRAFT_NVRTC or the pip package of the PC's Python venvs)
python3 tools/render/gen-trace-ptx.py ~/.cache/t3craft-render/outbox/<job>/trace.ptx
```

**Same picture?** Compared with the CPU in the same job (`--gpu-compare`, final look, `--ss 3`):

| Scene | Images | Identical sub-samples | Mean difference | Pixels > 16 of 255 |
| --- | --- | --- | --- | --- |
| hub, `--layout hybrid-ringcampus --projects 4 --only hub` (isometric) | 3 | 99.6 % | 0.015 / 255 | 0.003 % |
| catalog gallery, `HOUSE:house_timber_cottage,LAMP:lamp_cherry_post --scale 2` | 3 | 99.6 to 99.8 % | 0.010 to 0.012 / 255 | 0.000 to 0.005 % |
| aerial views, `--projects 4,10,20 --only aerial` | 3 | 98.1 to 98.3 % | 0.055 to 0.062 / 255 | 0.005 to 0.008 % |
| hub sections (axis-aligned rays) | 3 | 98.8 % | 3.6 / 255 | 4.8 % |

The differences are float against double (an edge texel here and there) and the soft-shadow jitter (the hash is the same, its
output is truncated to 24 bits). The one scene with a visible difference, the **sections**, is the CPU's fault: rays exactly
parallel to a grid axis leave one-pixel light cracks along cell borders in the CPU image (`02_cpu.png`); the GPU gives faces a
tolerance of 1.5e-4 blocks at their edges and has no cracks.

**Speed** (render PC: 11 CPU threads, RTX 3050; image seconds including the filter, bloom and sRGB; the first image of a job also
pays about 2 s for the child JVM and the PTX JIT when the driver's cache is cold, 0.3 s when it is warm):

| Scene | CPU | GPU | Speed-up |
| --- | --- | --- | --- |
| hub, 4 projects, 1995x1878 sub-samples (3.7 M) | 0.41 to 1.1 s | 0.09 to 0.15 s | 3 to 6x |
| hub section, 2031x909 sub-samples (1.8 M) | 0.28 to 0.64 s | 0.05 to 0.07 s | 5.5 to 9x |
| catalog gallery at `--scale 2`, 8055x5526 sub-samples (44.5 M) | 3.9 to 4.8 s | 0.83 s | 4.7x |
| catalog gallery at `--scale 2`, 8535x2961 sub-samples (25.3 M) | 5.5 s | 0.56 s | 9.8x |
| aerial 4, 10 and 20 projects, about 4820x3315 sub-samples (16 M) | 7.5 to 9.1 s | 1.16 to 1.27 s | 6.2 to 7.5x |

(The kernel itself takes 20 to 900 ms per image; the rest of the GPU time is the export of the scene, the child's round trip and
the download of 4 bytes per pixel.) After the GPU, what is left of a render is the PNG encoding.

**What was probed** (`--gpu-probe`, job `render-gpu-1794228843154`, WSL Ubuntu 24.04, driver 621.14 / CUDA 13.5, Mesa 25.2.8):

| Route | Result |
| --- | --- |
| CUDA driver API (`/usr/lib/wsl/lib/libcuda.so.1`) through FFM | Works. RTX 3050, cc 8.6, 18 SMs, 6 GB (about 5 GB free next to the desktop). The driver JIT-compiles hand-written PTX (`.target sm_52`, `sm_75` and `sm_86` all load; the JIT takes 7 to 215 ms for a toy kernel) and runs it. **This is the route in use.** No `nvcc`; NVRTC is only used as a dev tool for the kernel (the PC's Python venvs ship `libnvrtc.so.12`). |
| EGL surfaceless (`EGL_MESA_platform_surfaceless`) + OpenGL compute | A 4.3 core context and compute shaders work, but Mesa picks **llvmpipe (CPU)**. |
| same with `GALLIUM_DRIVER=d3d12` | Compute shaders work on `D3D12 (AMD Radeon 740M Graphics)`: Mesa's d3d12 driver picks the **iGPU**, not the RTX (`MESA_D3D12_DEFAULT_ADAPTER_NAME` was not tried). |
| EGL device platform | One device, llvmpipe again. |
| EGL default display (`eglGetDisplay`) | Hangs (timed out after 90 s: it tries the desktop's display server). Never used; no window can appear because nothing but a surfaceless context is ever created. |

## The report

Information for the designer, not a test: nothing fails. With a target, counts and lists cover that box;
reachability and the shape/floating checks look at the whole plan, so a piece is judged among its neighbours.

* **Signature**: SHA-256 of every cell (position and block state, air included, sorted by position, so the
  order the generator wrote them in does not matter) for the whole plan, for the scope, and per group; also
  `BuildPlan.fingerprint()` (order, anchors and pack too). Same `cells` before and after a refactor = the plan
  did not change, block by block (phase 0 of the citadel swarm must keep it). Per-group hashes say which
  piece moved when it did.
* **Blocks**: count per block type; light sources: how many, how many have an exposed face ("seen"), how many
  are lamps in the floor under a walkable cell, and per 100 walkable cells (the "full of lamps" measure).
* **Light**: walkable cells (reachable on foot from the `spawn` anchor, `PlanNav` rules) with block light
  (`HqLightMap`) under `--low-light`: histogram, clusters (cells, box, first cell) and coordinates.
* **Anchors**: every standing anchor and every desk reachable from the spawn (`direct`, or `adjacent` for
  seats and benches); the unreachable ones are listed.
* **Shapes**: fences, walls, panes, bars and stairs whose state differs from what `PlanConnect` (fix/plan-connect,
  see `docs/feat-plan-connect.md`) gives them among their real neighbours. `cornersMissing` counts those
  that should connect on 2 or 3 sides and do not: the round fences without corners.
* **Connect**: fences, walls and panes connected on no side although a neighbour they connect to is beside
  them (`connect:` line on stdout too). Must be 0; nothing is caught per cell.
* **Floating**: groups of blocks connected to nothing that reaches the ground (y <= 0); **unsupported**:
  blocks vanilla's `canSurvive` refuses where they stand (grouped by type and by group, with a coordinate; `byBlock`, `byGroup` and
  the first 2000 in `list` in report.json). Headless the block/fluid tags are unbound, so plants, lily pads, sugar cane and bamboo
  are judged by `PlanProbe.tagBasedOk` with the tag read from the vanilla data (`supports_vegetation`, …) instead of vanilla's `is(TAG)`.
* **Settle by origin** (inside SPLIT PIECES, `split.settleOrigins` / `split.settleGroups` in report.json): the groups `CitadelSettle` handled, grouped by the catalog piece (`FAMILY:variant`) or by canvas group / zone for the rest, with the mean fall, and for each group its plot-relative box and, if small, its cells. Use it to find the piece that is born floating: a piece with many groups here has a part that touches its support only on an edge or not at all. `minecraft:light` is ignored.
* **Split pieces** (`split` in report.json): groups of hanging cells near a terrace step that the column lift would have torn and the relief lifted with their support (`CitadelRelief.lastSplit`), what `CitadelSettle` did with floating groups (fell / got a post / removed / left), and horizontal log beams with nothing solid under them or at their ends (`touch nothing` ones are also in FLOATING; `hang from a wall` ones are held from the side or above).
* **Build**: ticks the crew (`CrewSchedule` + `CrewCursor`, no world) needs for the scope, with `--crew`
  builders at `--bpt` blocks per tick, and the blocks it walks.

## From Java

```java
HqRender.bootstrap(false);                                   // once per JVM
var planned = PlanSource.plan(null, List.of(4, 10));         // null = the citadel
var session = PieceViews.open();                             // keeps the block models
var rq = new PieceViews.Request();
rq.target = PieceViews.Target.module("module_3");
rq.out = Path.of("build/piece");
rq.scale = 0.5;
PieceViews.Result r = session.render(planned.plan(), rq);    // images + report files
PlanReport report = PlanReport.analyze(planned.plan(), null, new PlanReport.Options());   // report alone
ImageCompare.run(Path.of("build/before"), Path.of("build/piece"), 0);                      // before / after
```
