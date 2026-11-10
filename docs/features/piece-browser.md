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
A `PieceEntry` is `id`, `name`, `folder` (`"House"` or a path `"House/Japanese"`), `tags` and an optional size range (`Dims`).

- **Provisional source (`CodePieceSource`)**: reads `Catalog.all()` (every `Variant` in `CatalogEntries`, 68 at the time of writing).
  Folder = family (`House`, `Plaza center`...), houses get a style sub-folder (`House/Timber`, `Stone`, `Japanese`, `Terrace`, `Tower`);
  name = id without the family prefix; tags = family + the words of the name; size = the variant's own range.
- **The real catalog**: from its client initializer, `PieceCatalog.register(new MySource())`. Same id as a code piece = replaces it;
  new ids are added. Call `PieceCatalog.refresh()` when the source's content changes. The browser asks again every time it opens.

## Audit of the right panel and the narrow layout (#50)

Read-through audit (no game run), done before the #50 changes, as a user would meet the browser in the main
screen's right panel at its default 280 px and at its minimum 180 px, with the mouse and with the keyboard. The panel's
frame has an 8 px pad, so the page gets 264 / 164 px and its body 260 / 160 px. Facundo's report: "it is a bit hard to
navigate, and things are missing".

**Panel header** (`T3RightPanel` over `T3PanelHost`)
1. The header says "Piece browser" and has an `x`. Nothing tells there could be other pages, and there is no way to
   change page: the only way is the `layout.rightPanel` key by hand (and only one page is registered).
2. No way to open the page full-size from the panel. The ⋯ menu's "Pieces" does it, but it is a different path that
   the panel does not point to; in the compact layout the header icon already opens the page in the thread's place.
3. Closing works (`x`, Esc while the panel has the keyboard, U, the thread header's panel icon). Keys go to the panel
   only after a click inside it, which is invisible: nothing in the panel shows it has the keyboard.

**Narrow browser, controls on top** (header of 2 rows = 40 px)
4. Row 1: search box + Sort select (92 px fixed). At 160 px the search box is 48 px wide: three or four letters.
5. Row 2: Folder, Tag, Size selects and "Favorites only" (104 px fixed) share what is left. At 260 px the three
   selects get 43 / 57 / 43 px, i.e. 23 / 37 / 23 px of text ("All..." / "Any t..."); at 160 px 13 / 17 / 13 px, which
   is less than the chevron: unusable. No select has a tooltip, so a clipped value cannot be read anywhere.
6. Six controls compete for the top of a 160-260 px column although most visits only search and pick.

**Narrow browser, list and detail side by side** (body)
7. The list (min 110 px) and the detail column (min 110 px) sit side by side. At 260 px: list 143, detail 111. At 160 px
   the detail keeps its 110 px and the list drops to 80, so the two need 196 px in 160: **the detail column runs 36 px
   past the panel's edge** (clipped: the seed buttons and the right end of the selects are cut off).
8. In 111 px the detail wraps a lot: "Builds 9-15 x 9-15, h 10+" takes 2 lines, the size line under "Plot size" 4,
   "Seed (same seed, same blocks)" 2; "Stay here after asking" does not fit beside its switch. The column is about
   240 px tall: with a panel body under ~240 px (GUI scale 3 on 1080p) the whole page scrolls, and **the list scrolls
   away with it**, because list and detail share the scrolling body.
9. Rows (24 px, two lines) are fine, but the second line ("folder | 9-15 x 9-15, h 10+") is clipped at 113 px of text and
   with 3-digit sizes ("120-180 x 120-180, h 40+") it is cut in every row. A clipped **name** has no tooltip (the row
   tooltip shows id, size and tags, not the name).
10. The star (18 px square) is a fine target; folder rows (14 px) only exist in the wide layout.
11. Preview / Copy / Favorite are in the pinned footer, so "Preview in game" is always visible and needs no scroll
    (good). At 160 px "Clear filters" does not fit in the footer (it needs 80 px after the other three), yet the
    "no match" text says "Clear the filters (button below)".

**Keyboard**
12. Up / Down move the selection from anywhere, also from a focused select: the browser's key handler is pinned in the
    header, before the body, so Down never reaches a focused Faces or Plot size select (Enter still opens it).
13. After a click on the list it takes the keyboard: Home / End / Page Up / Page Down then move the list's own cursor,
    not the selection, and Up / Down move the selection but not that cursor: **two highlights** on different rows.
    Enter on the list does nothing (Enter previews only from the search or seed box). No key for favorite.
14. Tab walks search, Sort, Folder, Tag, Size, Favorites, list, Faces, Plot size, seed and its 3 buttons, Stay here,
    Preview, Copy, Favorite, Clear, the `x`: about 17 stops before Preview.
15. Esc closes an open select, otherwise the whole panel (there is no intermediate level to go back to).

**Resizing with the splitter**
16. The right panel can grow to half the frame (640 px at 1280), so dragging crosses the 430 px threshold: the folder
    column and the header jump in and out. Selection (session state) and the list's scroll survive, but the selected
    row is not brought back into view after the jump, and the focus, remembered by position in the Tab order, can land
    on another control because the number of header controls changes.

**Empty states**
17. Empty catalog and "no match" have texts; with no selection the detail column says what to do in 111 px (wraps to 6
    lines). With no selection the footer buttons are disabled with a "Select a piece first" tooltip (good).

**Folders**: in narrow the folder is only the clipped select (item 5); there is no breadcrumb, so a chosen sub-folder
("House/Japanese") is not visible once the select is clipped.

What #50 changed is in "Narrow layout (#50)" below and in `docs/features/t3-ui.md` ("Right panel pages").

## Code map

| class | what |
|---|---|
| `PieceBrowserPanel` | the kit page (`T3Panel`): header filters, three columns, footer actions |
| `PieceBrowserScreen` | the page as a screen (`T3PanelScreen`, wider frame) |
| `PieceBrowserState` | session state + favorites file |
| `PieceEntry`, `PieceCatalogSource`, `PieceCatalog`, `CodePieceSource` | the catalog side |
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
