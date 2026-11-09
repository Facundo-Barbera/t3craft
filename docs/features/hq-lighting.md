# HQ lighting: design first, invisible fill second

Branch `citadel/light-aesthetic` (2026-11-09). Applies to every HQ layout and to every catalog variant.

Facundo's rule: **what the lighting looks like matters more than covering every dark cell.** A lamp is
part of the build, so it stands where a builder would put one. Cells that stay dark after the design lamps
get light no one can see.

## 1. Visible light is design

Put a visible light source only where it makes sense as part of the design: on an axis, at a corner, on a
regular rhythm, or symmetric about a door or an axis. Never put one just because a spot was dark.

| Where | What | Rule |
|---|---|---|
| Streets, avenues, ring edges, terraces, galleries, bridges | posts: toro (shroomlight under a pagoda roof), vitral (lantern + amber/rose stained glass), brazier in plazas | every `CitadelAmbience.SPACING` (9) blocks along the edge, counted from the hub's axes (`|x|` or `|z|`). The two sides of a street are paired, and the four quarters mirror each other. The kind of post follows its place in the rhythm: toro, vitral, toro, brazier. |
| Under eaves, arcades, porticoes, bridges | hanging lantern under the roof block | mirrored lattice `(2|x| + 5|z|) mod 9`, never next to another lamp |
| Doors | a lantern on each side (porch posts, lamp tops or lanterns hung from the hood) | symmetric about the door's axis (`HallFacade.porch`) |
| Plazas (patio, terraces, inner street) | braziers: a campfire in a slab tray above head height | every fourth post of the plaza's rhythm |
| Windows, posts | lit stained glass (vitral) | part of the post or facade design |
| Halls, rooms | pendant lanterns on chains | grid centred on the room: centre line, then every 4 cells to each side, symmetric about the door axis. One-floor halls get a chandelier in the centre. |
| Tables | a lantern on a side table | `HallInterior.lamp`, between the side desks |
| Towers, cupolas, rotunda, obelisks | `CitadelCanvas.LIGHT` (froglight) as a lit band, a lamp in a cupola, or the clock marks | only as part of the piece's own design |

Hard limits, for both kinds of light:

- nothing in a walkway or on an NPC path. A post takes a cell only if the way stays connected
  (`keepsConnected`); lanterns hang at foot + 3 or higher;
- no light over a desk or its standing cell, or on a screen, a computer or an anchor's cell and the cell
  above it;
- deterministic: positions come only from coordinates and the geometry, never from scan order or chance.

**Forbidden:** letting froglights, sea lanterns, glowstone, shroomlight, lit shoji panels or any other
visible light into a floor, wall or ceiling just to cover a dark spot. The old ground lattice
(`CitadelCanvas.lattice` → froglight) and the hall ceiling grid of froglights are gone.

## 2. Invisible light fills what is left

Whatever is still under `MIN_LIGHT` (10) after the design lamps is lit with the invisible vanilla block
`minecraft:light`:

- **`CitadelFinish.light`** (citadel): levels 10 to 12 (`FILL_MAX`). First a lattice `(x + 5z) mod 13`
  (radius-2 diamonds), placed only where it lights at least 4 dark cells. Then one light per remaining dark
  cell, put in the best free air within 4 blocks. Each light uses the weakest level that does the job.
- **Hub interior** (`HubInterior.lights`): level 13, one cell above the floor (12 at the feet), with a
  covering radius of 2.
- **`HqLighting`** (every layout, after generation): the same moderate fill, 10 to 12, in the group's own
  air only. That is a plain air placement, or an unplaced cell a module of the group owns, which then
  becomes a placement of that group. It never swaps a block for a visible lamp any more: no froglight in
  floors, no shoji panel in walls or ceilings. The `lighting.grid` and `lighting.lattice` pack parameters
  are legacy and ignored.

Because the fill tops out at 12 and the design lamps are 14 or 15 (lantern, shroomlight, campfire 15;
torch 14), you can still see where the light comes from. `minecraft:light` has no collision, is
replaceable, and is not charged in the survival bill (`BlockCosts`, `HqCost`).

## 3. Checklist for catalog designers

1. Each variant brings its own visible light as design: lanterns hung under its eaves or porch, a lantern
   on each side of its door, lamps on its tables, lit glass. Put them on axes and keep them symmetric.
2. Do not try to light every cell. The invisible fill covers the rest, so a variant does not need a lamp
   in every corner.
3. Never put a light block in a floor, wall or ceiling to "cover" an area. If a room is dark, hang a
   pendant in the centre of its ceiling.
4. Keep lights off desks, screens, doors' openings and walkways. Hanging lanterns go at foot + 3 or
   higher.
5. A lamp is a piece of furniture: it needs a post, a chain, a bracket or a table under or over it.
