# Piece browser (feat/piece-browser-ui)

A page of the UI kit (docs/features/ui-kit.md) to explore the catalog of pieces, pick one and ask the game to show it,
so that trying pieces and variants is clicking instead of typing commands. Package `dev.t3craft.client.pieces`.

> **Verification:** written without compiling (rule of the VPS). The coordinator compiles on the PC. Not played.

## Opening it

| way | what |
|---|---|
| key **K** (Options → Controls → T3Craft, "Open the piece browser"; rebindable) | the browser as a screen of its own, from the game |
| `/t3craft pieces` | same; `/t3craft pieces <id>` opens with that piece selected and its folder open (tab-completes ids) |
| Settings (J) → Connections → **Piece browser…** | same, Esc returns to Settings |
| `T3Screen` ⋯ menu → **Pieces** | the page in the thread's place (sidebar stays), like Settings and Environments |
| `/t3dev ui pieces`, `/t3dev ui inbox-pieces` | the gallery over fake pieces (`GalleryPieces`: folders, a sub-folder, accented names, a piece with no size; favorites kept in memory only) |
| `/t3dev ui pieces-catalog` | the gallery over the real code catalog |

## The screen

```
(o)[ search: words of name / folder / tags ......... ] [ Sort v ]
[ Folder v (narrow only) ] [ Tag v ] [ Size v ] [ Favorites only ]
 Folders        N pieces                 Timber cottage            <- name
 All pieces 68  Timber cottage           house_timber_cottage      <- id
 Annex       3  Stone tower   ...        House / Timber  |  medium
 Banner pole 2  ...                      Builds 9-15 x 9-15, h 10+
 House      21                           Faces       [South v]
   Japanese  3                           Plot size   [Middle of its range v]
   Stone     3                                       12 x 12 x 12 (W x D x H)
   ...                                   Seed        [ 1 ........ ][-][+][shuffle]
                                         [x] Stay here after asking
[eye Preview in game] [copy Copy command] [star Favorite] [Clear filters]
 status line: what the last action did
```

**Filters** (all combine; the folder counts follow the other filters):
- **Search**: every word must appear in the piece's id, name, folder or tags (`cherry lamp`). Case and accents are folded
  like the sidebar's search (`SidebarModel.fold`, called, not copied): `cafe` finds "Café".
- **Folder**: the column on the left (a select in the header when the window is narrower than ~430 GUI px). A folder includes its sub-folders.
- **Tag**: the most common tags of the catalog with their counts.
- **Size**: small (up to 6 blocks on the larger side), medium (7-12), large (13+).
- **Favorites only**, and **Sort**: folder + name (default), name, size small first, size large first.
- "Clear filters" appears while any filter is on.

**Keys**: Up / Down move through the list from anywhere (also while typing in the search box), Enter in the search box or the
seed box asks for the preview, Esc goes back / closes, the wheel scrolls the list under the pointer.

**Remembered** for the whole session (`PieceBrowserState`): filters, selected piece, facing, size choice, custom size, seed. Close and reopen
and you are where you were. **Favorites** are kept on disk in `config/t3craft/piece-favorites.json` (a JSON array of ids).

**Icons** (Lucide, `T3Icon`; issue #30): `folder` / `folder-open` (the chosen folder) in the folder column, a `star` at the
right end of every piece row (brass = favorite, muted = not, accent under the pointer; click it to flip the favorite), `search`
before the search box, `eye` / `copy` / `star` on the footer actions, `minus` / `plus` / `shuffle` next to the seed. Default tint
muted, accent on hover, the disabled colour when it cannot be used (an empty folder).

## Asking for the preview

Per piece the browser builds a `PieceRequest`: **facing** (north/east/south/west), **plot size** (smallest / middle / largest of the
piece's range, or custom W x D x H from 1 to 64) and **seed** (a whole number from 0; same seed, same blocks; `-`, `+` and shuffle
to walk through variants). The size goes out as the command's keyword: Smallest `min`, Middle `mid`, Largest `max`, Custom `WxDxH`;
a piece with no size range sends `mid`. Under the select the browser shows the footprint the game will use for a keyword (its
rule: from the smallest side to the largest, capped at 24); the height is the family's, picked by the game.

**Preview in game** calls `PiecePreviews.current().request(request)` and shows the answer in the status line:

| status | meaning | colour |
|---|---|---|
| `SENT` | the request left for the game | green; the browser closes unless "Stay here after asking" is on (the action bar says what was asked) |
| `UNAVAILABLE` | cannot be asked now (no world joined, no preview installed) | yellow, the browser stays |
| `FAILED` | it was tried and threw | red, the browser stays |

**Copy command** puts `/t3craft piece show <id> <size> <n|e|s|w> <seed>` on the clipboard (paste it in chat); the Preview button's
tooltip shows the exact command for the current choices.

### The hook (contract for the in-world preview)

Default hook (`PiecePreviews.CommandPiecePreview`): runs the command as the player. Since issue #30 (T12) it is exactly the
grammar the in-world preview registers (`client/piece/PieceClient`, docs/features/piece-catalog-preview.md):

```
/t3craft piece show <id> <min|mid|max|WxDxH> <n|e|s|w> <seed>
```

The browser always sends the three optional arguments (size, front, seed), in this order. `size` is what `PieceSpec.validSize`
accepts (sides 1 to 200; the browser's custom box stops at 64), `seed` is `>= 0` (`longArg(0)`). The stage only lives in the
T3Craft HQ world; elsewhere the command says so in chat. The server grammar was not changed.

To answer differently, from a client initializer, `PiecePreviews.install(request -> ...)` with a `PiecePreview` that calls its own API and returns
`PiecePreview.Result.sent("...")` / `unavailable(...)` / `failed(...)`. The message is what the user reads.

## The catalog (and where the real one plugs in)

The browser reads `PieceCatalog.all()`: the entries of every registered `PieceCatalogSource`, merged by id (a later source wins).
A `PieceEntry` is `id`, `name`, `folder` (`"House"` or a path `"House/Japanese"`), `tags`, an optional size range (`Dims`) and
`parametric` (true unless the game ignores size, facing and seed: the structures; the five-argument constructor keeps true).

- **Provisional source (`CodePieceSource`)**: reads `Catalog.all()` (every `Variant` in `CatalogEntries`, 68 at the time of writing).
  Folder = family (`House`, `Plaza center`...), houses get a style sub-folder (`House/Timber`, `Stone`, `Japanese`, `Terrace`, `Tower`);
  name = id without the family prefix; tags = family + the words of the name; size = the variant's own range.
- **Structures (`StructurePieceSource`, #49)**: the `struct_*` pieces of the in-world catalog (group `struct`, `dev.t3craft.piece.StructurePiece`)
  in the folder **Structures**: `struct_leader_office` (Leader's office, the control tower), `struct_lead_office`, `struct_hub`,
  `struct_barrio_gate`, `struct_barrio_hall`. Tags are the structure's own search words plus `structure`: the office is found by
  `office`, `oficina`, `leader`, `control`, `tower`, `torre`. Size = the real one (`minW == maxW`, `minD == maxD`): an estimate from the
  citadel's geometry until the structure has been built once in the session, then the size the stage measured. `PieceEntry.parametric`
  is false for them: the game ignores size, facing and seed. Details, sample data and limits: docs/features/piece-catalog-preview.md,
  "Structures".
- **The real catalog**: from its client initializer, `PieceCatalog.register(new MySource())`. Same id as a code piece = replaces it;
  new ids are added. Call `PieceCatalog.refresh()` when the source's content changes. The browser asks again every time it opens.

## Code map

| class | what |
|---|---|
| `PieceBrowserPanel` | the kit page (`T3Panel`): header filters, three columns, footer actions |
| `PieceBrowserScreen` | the page as a screen (`T3PanelScreen`, wider frame) |
| `PieceBrowserState` | session state + favorites file |
| `PieceEntry`, `PieceCatalogSource`, `PieceCatalog`, `CodePieceSource`, `StructurePieceSource` | the catalog side |
| `PieceRequest`, `PiecePreview`, `PiecePreviews` | the preview side (request, hook, default command) |
| `GalleryPieces` | fake catalog for `/t3dev ui pieces` (`PieceBrowserPanel.withCatalog`) |

Kit additions this needed: `T3List.ensureVisible` / `scrollToTop`, `T3Panel.pinField` (a text box in the pinned header/footer),
`T3PanelScreen.maxWidth` / `maxHeight` (a page that needs more than 560 x 400).

## To check when it is first played

- That `K` is free in the Controls screen and nothing else in the pack takes it.
- Typing in the search box: the list re-filters about one tick later (by design: the page is rebuilt from `tick()`, not from the key event).
- The three columns at GUI scale 2, 3 and 4; at 4 the folder column becomes the header select and the right column may scroll the page.
- Preview from the HQ world builds the piece on the stage; from another world the chat says the stage lives in the HQ world.
- Row text: only ASCII and Latin-1 glyphs; the favorite star is the Lucide sprite, tinted.

## Structures: UI changes still to do (#49)

The data side is in; the page itself was not touched (the right panel's navigation is being redone elsewhere). What the page should do with
a structure (`!entry.parametric()`):
- hide the facing, plot-size and seed controls (they are ignored), or show them disabled with a note "fixed size";
- show "Size 36 x 39 x 51" instead of "Builds 36 x 39, h 51+", and not the plot-size footprint line, which caps at 24;
- the Copy command can stay `/t3craft piece show <id>`: the three arguments are accepted and ignored;
- the folder "Structures" could be pinned first in the folder column.
