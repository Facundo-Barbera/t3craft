# fix/npc-labels — legible NPC text

Per NPC there is now **one far label** and **one near panel**:

- **Name tag** (vanilla, the avatar's display name): `⚙ Thread title…` — state glyph and the
  title cut to `titleMax` code points, in the state colour. No chips.
- **Detail panel** (`TextDisplay` over the head): `environment · project`, then the model/branch
  chips (`chips.modelPanel` / `chips.branchPanel`) and the helpers line (`⚒ 2 helpers +3`). It
  shows only while the player is within `detailDistance` blocks, aims at the NPC (view ray passes
  ≤ 0.5 blocks from it, up to 16 blocks away) or left-clicked it (open 10 s, a second left-click
  closes it). Hysteresis: it stays until 1 block past the distance / 0.9 blocks off-aim, plus
  10 ticks of grace, so it does not flicker. The activity is left to the speech bubble, which shows
  in the same radius.
- **Speech bubble / attention icon** (BehaviorDirector) now stack above the panel's visible lines
  instead of a fixed height, so they never cross it.
- Right-click still opens the chat. Workstation screens and helper name tags are unchanged (they
  sit on their own entities, not over the parent NPC).

Config (additive, `config/t3craft.json`, re-read within ~2 s):

```json
"labels": { "mode": "compact", "detailDistance": 4.0, "titleMax": 24 }
```

`"mode": "detailed"` brings back the previous layout (chips on the name tag, panel always on).
Pure logic and tests: `NpcLabels`, `LabelConfig`, `NpcLabelsTest`.

**Update (world text, #34):** name tags now show only within `labels.nearDistance` or while aimed
at, at most `labels.maxVisible` at once and never overlapping; only `labels.maxPanels` panel opens at
a time; in the default hologram style the tag becomes a pill plate. See `docs/features/world-text.md`.
