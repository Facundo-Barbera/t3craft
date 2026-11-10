# Icon pipeline (#24)

UI icons are **Lucide SVGs** (ISC, plus MIT for the ones Lucide derives from Feather; see
`THIRD_PARTY_NOTICES.md`). The repo keeps **only the SVGs**; the PNGs are generated at build time.

```
tools/art/icons/lucide/<name>.svg
        |  ./gradlew generateIcons   (Apache Batik, build-only `icons` source set)
        v
mod/build/generated/icons/assets/t3craft/textures/gui/sprites/icons/<name>.png   (32 px, white on transparent)
        |  added to the main resources, before processResources
        v
GUI atlas sprite  t3craft:icons/<name>   ->   T3Icon.<NAME>   ->   tinted at draw time
```

## Build side

* **Source set `icons`** (`mod/src/icons/java`, like `hqrender` / `uirender`): `dev.t3craft.tools.icons.IconGen`.
  Apache Batik (`batik_version` in `mod/gradle.properties`, pinned) is a dependency of this source set only,
  so it is **not in the mod jar nor in any run classpath**.
* **`generateIcons`** (`JavaExec`, group `t3craft`): `processResources` depends on it and its output dir
  (`build/generated/icons`) is a main resources dir. Incremental: the inputs are the SVG folder, the size and
  the generator's classpath; unchanged inputs mean up-to-date, no work.
* `currentColor` is replaced by white before rasterising, and pixels without ink are transparent *white* (no
  dark fringe when the GPU filters). The output folder is emptied of `.png` first, so a deleted SVG leaves no sprite.
* **It fails closed**: if Batik does not resolve, no SVG is found, one does not rasterise or a result is not a
  32x32 PNG with ink in it, `generateIcons` fails and so does `build` / `runClient`. The game is never packaged
  without its icons.
* No atlas file is needed: the GUI atlas takes every sprite under `textures/gui/sprites/` of a namespace (the
  `chat/*` kit already works that way).
* Run it on the PC (agents do not run Gradle elsewhere): `tools/heavy.sh --timeout 15m -- bash -c 'cd mod && ./gradlew generateIcons'`.
  A normal build runs it by itself.

## Runtime side (`client/t3ui/kit`)

| | |
|---|---|
| `T3Icon` | One constant per SVG (`CHEVRON_DOWN` is `chevron-down.svg`), `id()`, `sprite()`, `byId()`, `SIZE = 12` |
| `T3Icon.draw(g, t, icon, x, y, size, argb)` | Tinted `blitSprite` (same call as `ChatDraw`). A tint with alpha 0 means the theme's muted colour |
| `T3Icon.draw(g, t, icon, x, y, size, hover, enabled)` / `T3Icon.tint(t, hover, enabled)` | muted at rest, accent on hover, disabled when disabled |
| `new T3Button(icon, label, [style,] action)` | Icon + optional label (null / empty = icon only; give it a tooltip). Same tint as the label in every style |
| `T3IconButton(icon, action)` | Ghost icon-only button for the sidebar and toolbars: wash + accent on hover, `setActive` for a toggled-on page |

## Adding an icon

1. Copy `icons/<name>.svg` from Lucide (note the tag in `THIRD_PARTY_NOTICES.md`; use the same one or update it) into `tools/art/icons/lucide/`.
   Names are lowercase kebab-case. Only SVG goes in the repo, never the PNG.
2. Add the constant to `T3Icon` (name in upper case with `_`).
3. Check it in the gallery (below). An SVG without a constant is generated but unreachable; a constant without an
   SVG shows as the missing-texture square in the gallery.

## Looking at them

`/t3dev ui icons` (after `generateIcons`, i.e. any build): every icon in a grid on both themes (Warm Studio and
terminal), with its name, at 12 px in the three states (muted, accent, disabled) and at 32 px; the wheel scrolls,
hovering a cell shows the sprite id. F3+T reloads the sprite atlas and `ui-style.json`, so a changed SVG (after
`generateIcons` and `processResources`, which a build does) or colour shows without restarting.

Threads and agents never open PNGs (`AGENTS.md` rule 9): this check is done by a person in the game.
