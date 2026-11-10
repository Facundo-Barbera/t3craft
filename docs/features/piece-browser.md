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
| `/t3dev ui pieces`, `/t3dev ui inbox-pieces` | the gallery (docs/features/ui-kit.md) |

## The screen

```
[ search: words of name / folder / tags ............ ] [ Sort v ]
[ Folder v (narrow only) ] [ Tag v ] [ Size v ] [ Favorites only ]
 Folders        N pieces                 Timber cottage            <- name
 All pieces 68  Timber cottage           house_timber_cottage      <- id
 Annex       3  Stone tower   ...        House / Timber  |  medium
 Banner pole 2  ...                      Builds 9-15 x 9-15, h 10+
 House      21                           Faces       [South v]
   Japanese  3                           Plot size   [Middle of its range v]
   Stone     3                                       12 x 12 x 12 (W x D x H)
   ...                                   Seed        [ 1 ........ ][-][+][Random]
                                         [x] Stay here after asking
[Preview in game] [Copy command] [Favorite] [Clear filters]
 status line: what the last action did
```

**Filters** (all combine; the folder counts follow the other filters):
- **Search**: every word must appear in the piece's id, name, folder or tags (`cherry lamp`).
- **Folder**: the column on the left (a select in the header when the window is narrower than ~430 GUI px). A folder includes its sub-folders.
- **Tag**: the most common tags of the catalog with their counts.
- **Size**: small (up to 6 blocks on the larger side), medium (7-12), large (13+).
- **Favorites only**, and **Sort**: folder + name (default), name, size small first, size large first.
- "Clear filters" appears while any filter is on.

**Keys**: Up / Down move through the list from anywhere (also while typing in the search box), Enter in the search box or the
seed box asks for the preview, Esc goes back / closes, the wheel scrolls the list under the pointer.

**Remembered** for the whole session (`PieceBrowserState`): filters, selected piece, facing, size choice, custom size, seed. Close and reopen
and you are where you were. **Favorites** are kept on disk in `config/t3craft/piece-favorites.json` (a JSON array of ids).

## Asking for the preview

Per piece the browser builds a `PieceRequest`: **facing** (north/east/south/west), **plot size** (smallest / middle / largest of the
piece's range, or custom W x D x H from 1 to 64) and **seed** (same seed, same blocks; `-`, `+` and Random to walk through variants).
A piece with no size range sends `0 0 0` ("default size").

**Preview in game** calls `PiecePreviews.current().request(request)` and shows the answer in the status line:

| status | meaning | colour |
|---|---|---|
| `SENT` | the request left for the game | green; the browser closes unless "Stay here after asking" is on (the action bar says what was asked) |
| `UNAVAILABLE` | cannot be asked now (no world joined, no preview installed) | yellow, the browser stays |
| `FAILED` | it was tried and threw | red, the browser stays |

**Copy command** puts `/t3craft piece show <id> <front> <w> <d> <h> <seed>` on the clipboard (paste it in chat); the Preview button's
tooltip shows the exact command for the current choices.

### The hook (contract for the in-world preview)

Default hook (`PiecePreviews.CommandPiecePreview`): runs the command as the player,

```
/t3craft piece show <id> <north|east|south|west> <w> <d> <h> <seed>
```

All six arguments are always present, in this order; `0 0 0` means "default size". **That command does not exist yet**: until the
in-world preview (feat/piece-catalog-preview) registers it, the game answers "unknown command" in chat, and the browser's status line
says so. Two ways to plug it in, with no change to the browser:

1. Register the command above (a client command is fine: `ClientPacketListener.sendCommand` runs client commands locally).
2. Or, from a client initializer, `PiecePreviews.install(request -> ...)` with a `PiecePreview` that calls its own API and returns
   `PiecePreview.Result.sent("...")` / `unavailable(...)` / `failed(...)`. The message is what the user reads.

## The catalog (and where the real one plugs in)

The browser reads `PieceCatalog.all()`: the entries of every registered `PieceCatalogSource`, merged by id (a later source wins).
A `PieceEntry` is `id`, `name`, `folder` (`"House"` or a path `"House/Japanese"`), `tags` and an optional size range (`Dims`).

- **Provisional source (`CodePieceSource`)**: reads `Catalog.all()` (every `Variant` in `CatalogEntries`, 68 at the time of writing).
  Folder = family (`House`, `Plaza center`...), houses get a style sub-folder (`House/Timber`, `Stone`, `Japanese`, `Terrace`, `Tower`);
  name = id without the family prefix; tags = family + the words of the name; size = the variant's own range.
- **The real catalog**: from its client initializer, `PieceCatalog.register(new MySource())`. Same id as a code piece = replaces it;
  new ids are added. Call `PieceCatalog.refresh()` when the source's content changes. The browser asks again every time it opens.

## Code map

| class | what |
|---|---|
| `PieceBrowserPanel` | the kit page (`T3Panel`): header filters, three columns, footer actions |
| `PieceBrowserScreen` | the page as a screen (`T3PanelScreen`, wider frame) |
| `PieceBrowserState` | session state + favorites file |
| `PieceEntry`, `PieceCatalogSource`, `PieceCatalog`, `CodePieceSource` | the catalog side |
| `PieceRequest`, `PiecePreview`, `PiecePreviews` | the preview side (request, hook, default command) |

Kit additions this needed: `T3List.ensureVisible` / `scrollToTop`, `T3Panel.pinField` (a text box in the pinned header/footer),
`T3PanelScreen.maxWidth` / `maxHeight` (a page that needs more than 560 x 400).

## To check when it is first played

- That `K` is free in the Controls screen and nothing else in the pack takes it.
- Typing in the search box: the list re-filters about one tick later (by design: the page is rebuilt from `tick()`, not from the key event).
- The three columns at GUI scale 2, 3 and 4; at 4 the folder column becomes the header select and the right column may scroll the page.
- `sendCommand` with the default hook while joined to a world: unknown-command message in chat (expected until the preview exists).
- Row text: only ASCII and Latin-1 glyphs are used on purpose (the bitmap font has no star; favorites are a brass square).
