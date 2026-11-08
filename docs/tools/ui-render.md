# ui-render: the game's screens as PNGs, without the game

Renders `T3Screen` (the Y screen) and its dialogs with **mock data**, headless: no Minecraft window, no GL,
no network, no T3. Meant for design agents (and humans) to *see* a UI change. Looks like the game, minus the
limits listed at the end.

```
./gradlew uiRender -PuiArgs="--all"
./gradlew uiRender -PuiArgs="--scenario markdown,running --size 2560x1380 --gui-scale 3"
./gradlew uiRender -PuiArgs="--scenario many-threads --mouse 400,300"
./gradlew uiRender -PuiArgs="--scenario many-threads --compare /home/user/projects/t3craft-ref/ui/t3craft-ingame.png"
```

Agents never run gradle or java themselves: ask the shared render service (`docs/tools/render-service.md`).
**Only the coordinator compiles**: `./gradlew compileUirenderJava` (not part of `build`/`check`, on purpose: it
can never break the mod jar).

## Options

| option | meaning |
| --- | --- |
| `--list` | prints the scenario names |
| `--scenario a,b` / `--all` | what to render (default: all) |
| `--size WxH[,WxH]` | window size in pixels (default `2560x1380`, the size of the reference shots; also `1920x1080`, `1280x720`) |
| `--gui-scale N[,N]` | GUI scale 2, 3, 4… (default 3; GUI size is `ceil(size / scale)`, like the game) |
| `--mouse X,Y` | pointer in **window pixels** (hover states, tooltips); `--mouse-gui X,Y` in GUI pixels |
| `--compare IMG` | also writes `<name>-compare.png`: ours left, the reference scaled to the same height right, magenta rule between |
| `--out DIR` | default `build/ui-render` |

Per render, in the output dir: `<scenario>-<W>x<H>-gui<S>.png`, and `….texts.txt` (every text drawn, as
`x,y<TAB>text` in GUI pixels: grep it instead of reading pixels). Problems that did not stop the render
(a step that found no text, a composer that changed shape) are printed as `note:` lines.

From Java (the daemon of `tools/render-service`, `kind: ui`):

```java
UiRender.Result r = UiRender.render(new UiRender.Request("markdown", 2560, 1380, 3, -1, -1, null, outDir));
// r.png(), r.comparison(), r.texts(), r.notes();   UiRender.scenarios() lists the names
```
Request fields: `scenario, width, height, guiScale, mouseX, mouseY (window px, <0 = none), compare (Path|null), outDir`.
`render` is `synchronized` (it installs process-wide statics in `Minecraft`/`OperateClient`), so one at a time.
The daemon must put the worktree's compiled `uirender`, client and main classes on one classloader whose parent
holds the Minecraft jars; the fixtures are classpath resources (`fixtures/*.json`).

## How it works

1. **Real screens.** `Mock` builds `new T3Screen(directory, source, actions, slots, selected)` with injected
   `ThreadDirectory`, `ChatSource` (answers `subscribeThread` synchronously with the fixture's snapshot, in the
   real T3 stream format), `ConversationPanel.Actions`. The dialogs (`NewThreadDialog`) are built over it.
   Nothing in the UI code was changed for this except one additive hook: `ModConfig.useDataDir(Path)`.
2. **No game.** `Headless` allocates `Minecraft` without running its constructor (`Unsafe.allocateInstance`) and
   sets only what screens read: `font` (a real `Font` whose glyph advances come from the jar's bitmap fonts,
   so wrapping and truncation are the game's), `getWindow()` GUI size, the input type, the language (vanilla +
   the mod's `en_us.json`), and `OperateClient`/`ModConfig.dataDir()` pointing at a scratch dir where the
   scenario's environments and operate sessions are written (`control: false` = read-only).
3. **Recording.** `RecordingGraphics` subclasses `GuiGraphicsExtractor` (also allocated without a constructor) and
   records fills, gradients, text runs, sprites, textures, scissor, tooltips in GUI coordinates (pose applied).
   Screens and vanilla widgets only see the extractor API, so whatever they call lands there. Not overridden =
   not used by our screens (items, entities, maps…): it would fail loudly with a NullPointerException.
4. **Painting.** `Raster` paints with Java2D, nearest-neighbour: glyphs from `assets/minecraft/textures/font/*.png`
   (`JarFont`, same advance rule as the game, shadow = colour/4 at +1 px, bold, underline, strikethrough),
   sprites from `textures/gui/sprites/` nine-sliced/tiled per their `.mcmeta`, the mod's own sprites from its
   resources, textures by id. A missing sprite/texture is drawn as a magenta frame.

## Scenarios

`empty`, `many-threads` (80 threads, 3 machines, 6 projects, pinned, Settled, all states), `running` (Stop, a
steering message, a draft), `markdown` (code, lists, quote, table, tool calls, reasoning, to-dos, plan, error,
folded steps), `attachments` (3 thumbnails), `context-menu`, `model-picker`, `new-thread-draft`, `no-control`,
`offline` (cached rows, unreachable machine).

### Adding a scenario

1. Create `mod/src/uirender/resources/fixtures/<name>.json` and add the name to `fixtures/index.json`.
2. Schema (all keys optional except where noted):

```json
{
  "description": "one line",
  "screen": "t3 | new-thread",
  "newThread": {"env": "studio", "project": "p-t3craft"},
  "cached": false,
  "environments": [{"id": "studio", "label": "Studio Mac", "status": "ok|offline|connecting", "control": true, "color": "#e0af68"}],
  "projects": [{"env": "studio", "id": "p-t3craft", "title": "t3craft"}],
  "threads": [{"env": "studio", "id": "t1", "project": "p-t3craft", "title": "…", "state": "working|done|idle|needs_attention|error",
               "pending": false, "activity": "running tests", "settled": false, "pinned": false, "agoMin": 3.5, "transcript": "name"}],
  "generate": {"threads": 80, "seed": 7, "pinned": 3, "settledPct": 25},
  "selected": "studio:t1",
  "transcripts": {"name": {"title": "…", "runs": [{"id": "r1", "ordinal": 1, "status": "running"}], "items": [ /* T3 turn items */ ]}},
  "serverConfig": { /* server.getConfig; default fixtures/_server-config.json (Claude + Codex) */ },
  "composer": {"draft": "text", "images": 3},
  "steps": [{"op": "rightClickText", "text": "Pinned thread", "keepMouse": true}],
  "ticks": 2
}
```

* **Transcript items** are T3 `OrchestrationV2TurnItem`s (`user_message`, `assistant_message`, `reasoning`,
  `command_execution`, `file_change`, `file_search`, `dynamic_tool`, `todo_list`, `proposed_plan`, `error`,
  `notification`…; see `TurnItems`). `ordinal`, `status`, `id` and `messageId` are filled in if missing.
  A run with status `running` makes the thread "running" (Stop, steering). Times are relative (`agoMin`).
* **Steps** replay input: `clickText` / `rightClickText` click the first drawn text that *contains* `text`
  (so scenarios survive layout changes), `hoverText`, `scroll` (`amount`), `tick` (`n`). `keepMouse` leaves the
  pointer there for the final frame (hover states).
* `composer` goes through the composer's public API (`ComposerView.state().editor()`, `addImage`) and fills the
  thumbnail cache (`ImageThumbs.ready`) because stb_image/GL do not exist here. If the composer is rewritten and
  these change, the scenario still renders and a `note:` says what was skipped.

## Limits against the real game

* No shaders, no blurred/darkened background, no world behind: the screen is painted over `Raster.BACKDROP`
  (T3Screen paints its own opaque backdrop anyway). Dialogs get a flat 63 % dim instead of the blur.
* Fonts: only the bitmap sheets of the jar. Characters that exist only in Unifont (e.g. `⚙`, many symbols) draw as
  the missing-glyph box; the game would show a Unifont glyph. No resource-pack/shader-pack overrides.
* Text positions are pixel-exact at integer GUI scales; italics are an approximation (shear).
* Input is simulated only by the steps above; clicks that open other screens or use the clipboard/player/IME
  (`Minecraft.gui`, `textInputManager`) are not supported (null in the headless `Minecraft`). Focusing the
  composer by clicking it is therefore not a step; the draft is set directly.
* The live bits (animations, pulsing dots, blink of the caret, hover prewarm) show one frame at tick `ticks`.
* Tooltips use the vanilla look but not its exact wrapping.
* Items, entities, maps, skins and the profiler chart are not drawn (our screens do not use them).
* Thread status chips use the fallback pill: there is no shell (`T3CraftClient`) behind the screen, so the
  thread's model in the picker shows the first provider's default.
* Fixture data is a stand-in: real threads have more variety. Add a scenario when a bug needs one.
