# World text: fewer floating labels, pill plates and the hub card (#34)

Section 8 of the "Plan: UI next steps". Every floating text in the HQ is a client-only display
entity; this pass cuts how many show at once and gives what stays an AgentCraft-style hologram look.
Code: `client/npc/LabelConfig`, `NpcRegistry` (visibility pass), `PlateLayout` (declutter),
`Nameplate`/`NpcLabels`/`WorldText` (pill text, bubble timing), the bubble block of
`npc/behavior/BehaviorDirector`, `client/hologram/*` (card, `PodiumBubbles`),
`workstation/WorkstationRenderer` and `schedule/ScheduleClient` (source switches).

## Phase A: reduce

- **Name tags** (compact mode) show only for NPCs within `labels.nearDistance` (12) blocks or
  while aimed at (view ray within 0.5 blocks, 16 blocks out), with hysteresis (1 block / 0.9
  blocks of aim). Before: every NPC within vanilla's 64 blocks.
- **At most `labels.maxVisible` (6) plates** at once: the open panel first, then the aimed NPC, then
  the nearest.
- **Declutter**: two plates never overlap; the lower-ranked one hides. Plates are compared by their
  angles from the eye (so turning the head changes nothing) and the pass reruns only when the eye or a
  plate moved more than 0.25 blocks, the aimed set or a plate's size changed. It decides visibility
  only: no entity is created and no id is allocated (`ClientEntityIds` ranges untouched).
- **One detail panel at a time** (`labels.maxPanels` = 1): of the panels that want to open (near,
  aimed or clicked, as in fix/npc-labels), the clicked one wins, then the aimed one, then the nearest.
- **Workstation screens** stay blank under a `t3craft:monitor` (as before) and now also beyond the
  monitor's level-of-detail distance (`blocks.monitor.nearDistance`, default 8 blocks, +2 blocks of
  hysteresis before they come back).
- The vanilla tag is hidden through a client-only team with name tag visibility NEVER (never added
  to the scoreboard).

## Phase B: hologram style (`labels.style: "hologram"`, default)

Applies in compact mode; `"style": "classic"` (or `"mode": "detailed"`) keeps the look before.

- **Pill plate**: one per NPC, on the NPC's existing panel display: a status dot in the state
  colour and one activity line (first line of the activity, up to 40 characters; the title when there
  is none). The vanilla tag stays hidden. The open detail lines (title, environment and project,
  chips, helpers) appear under the pill. Text is rebuilt only when the NPC's info changes.
- **Speech bubble only on events**: a new summary or a change of state shows it for 3.5 s plus
  0.1 s per character, at most 11 s; it fades out over the last second in 5 steps. No bubble just for
  standing near an NPC any more.
- **Decision podium bubble**: over each `decision_podium_<i>` while the i-th thread that needs the
  player waits (the order of its lamp and of its click): `! 2 waiting · <agent>` and the question
  under it. Gone when nobody waits. It reads the HQ plan and the roster, never the podium block, so it
  also shows over the lectern of an HQ without the blocks module. A podium given a manual binding
  still shows its default `board:<i>` thread here.
- **Hub card**: the hub hologram becomes one card: a progress ring drawn by code (16 billboarded
  stained-glass segments, lime = done today, grey = still open) with `done/total` in its middle, the
  card text under it (counts, most urgent thread, one line of per-environment or per-project counts
  when there are several, offline note) and a soft beam (thin core and a wider halo, tinted by the
  most urgent state). Ring total = open threads + threads done today.
- **Colours** only from `ChatTheme` tokens: the dark terminal palette (`panel` for backgrounds with
  the alphas in `WorldText`, `text`, `muted`, `disabled`, status colours), so F3+T restyles it.
- **No new sprites**: text displays cannot draw the chat kit's `bubble`/`bubble_tail` sprites, so
  the pill, bubbles and ring are plain text backgrounds and block displays.

## Config (additive, `config/t3craft.json`, re-read within ~2 s)

```json
"labels": {
  "mode": "compact", "detailDistance": 4.0, "titleMax": 24,
  "style": "hologram", "nearDistance": 12, "maxVisible": 6, "maxPanels": 1, "declutter": true,
  "hologram": { "npcTags": true, "workstationScreens": true, "hubHologram": true, "scheduleBoard": true }
}
```

Every key is documented in `docs/features/config.md` ("World text").

## Settings page

Settings has a fourth tab, **World text** (`client/config/SettingsPanel`, state and file merge in
`client/config/WorldTextSettings`; gallery `/t3dev ui settings-world-text`). It edits `labels.style` (select),
`nearDistance`, `maxVisible` and `maxPanels` (number fields, same ranges as the parser: 0 to 128 blocks, 0 to 256),
`declutter` and the four `labels.hologram.*` switches. Like the other tabs it edits a draft; **Save** validates the
numbers and writes the `labels` block of `config/t3craft.json` only when the tab changed something in it. The rest of
the block (`mode`, `detailDistance`, `titleMax`, unknown keys) is kept, and a `"hologram": false` shorthand becomes the
four explicit switches. **Reset to defaults** puts the draft back to the defaults of the table in `config.md` (Save keeps
them). The game re-reads the file within ~2 s: no restart. "Reload from disk" (Connections) re-reads this tab too. To get the previous behaviour
back: `"style": "classic", "nearDistance": 0, "maxVisible": 0, "maxPanels": 0, "declutter": false`
(and `blocks.monitor.nearDistance` large, e.g. 64, for the desk screens).

## Out of scope

The per-surface `world` scale.
