# feat/t3-ui-unified — one T3 screen (key Y)

The T3 key (Y, unchanged), the T3 Terminal item, and every click on a desk, monitor, thread wall card,
NPC or search hit now open **one screen**, `dev.t3craft.client.t3ui.T3Screen`. It replaces the old
selector (`PortableScreen`, removed) and the chat browser screen (`ThreadBrowserScreen`, removed). The
`ThreadBrowser` widget and `ConversationPanel` stay as reusable components.

All entry points go through `ChatScreens.create(key)` (returns a `T3Screen`): `PortableClient.open`
(key + item), `ChatScreens.open/openFor` (NPCs), `ChatScreenOpener` (desks, monitors, thread wall),
`PortableLinks.openChat` (search), `ChatScreens.createReadOnly` (archive, no actions).

## Layout

```
┌ sidebar ──────────────┐ ┌ thread ───────────────────────────────────────┐
│ T3  12 threads · 2 ⋯  │ │ [env] project › Thread title      (● Working) │  header: breadcrumb + ThreadStatusChip
│ [ Search threads    ] │ │ [Interrupt][Stop][Settle][Snooze][Pin][…]     │  header actions
│ [ ▾ All projects    ] │ ├───────────────────────────────────────────────┤
│ [ + New thread      ] │ │ conversation (ConversationPanel, no header)   │
│ ▾ Pinned            1 │ │                                               │
│     ● Pinned one   2h │ │                                               │
│ ▾ t3craft        ● 3  │ │                                               │
│     ⚙ HQ layouts   5m │ ├───────────────────────────────────────────────┤
│     ! Fix login    1h │ │ composer [model picker ▾]          [Send]     │
│ [Archive][Envs][Set.] │ └───────────────────────────────────────────────┘
└───────────────────────┘
```

Below 330×170 scaled pixels (narrow window or high GUI scale) the screen is compact: one panel at a
time. Opened on a thread (a block) it starts on the thread; opened with Y it starts on the list. `‹`
or Esc goes back to the list.

## Right panel, resizing and the saved layout (#39)

```
┌ sidebar ─┐┊┌ thread ─────────────────────── [▥] ┐┊┌ Piece browser ──── x ┐
│          │┊│ header                              │┊│ (a RightPanels page) │
│          │┊│ conversation                        │┊│                      │
│          │┊│ composer                            │┊│                      │
└──────────┘┊└─────────────────────────────────────┘┊└──────────────────────┘
            ┊ splitters (T3Splitter, the 4 px gaps): drag; double click = default width
```

- **Frame.** Still centred, now as large as the window minus 6 px per side, capped at 1280×720 by default
  (was a fixed 900×520). `layout.maxWidth` / `layout.maxHeight` change the cap (at least 900×520).
- **Right panel** (`T3RightPanel`, kit): shows one page registered in `RightPanels` (id → factory). The
  first one is the piece browser (`"pieces"`, registered by `T3Screen`; under 430 px it uses its narrow
  layout with the folder select). Header with the title and an `x`; Esc closes it while it has the
  keyboard (keys follow the last click: inside the panel, or anywhere else).
- **Toggle.** The `panel-right` icon at the end of the thread header, the ⋯ menu's "Show / Hide side
  panel", or the rebindable key *Show/hide the T3 right panel* (default U, Controls → T3Craft; it fires
  when the list has the keyboard). Ctrl+B stays the sidebar's.
- **Widths.** Drag the gap between sidebar and thread, or between thread and right panel. Sidebar
  120..360 px, right panel 180 px..half the frame, and the thread never under 220 px: a small window
  shrinks the right panel first, then the sidebar, then leaves the right panel out, without changing
  the saved widths. Double click on a gap goes back to the default (27 % sidebar, 280 px panel).
- **Compact** (under 330×170): no right panel; the header icon opens the page full-screen in the
  thread's place. The widths and the open/closed wish are kept for the next wide window.
- **Saved** in `config/t3craft.json`, an additive top-level block (`T3LayoutPrefs`):

  ```json
  "layout": {"sidebarWidth": 180, "rightWidth": 300, "rightOpen": true, "rightPanel": "pieces"}
  ```

  0 = the default width. Written 500 ms after a drag ends or the panel opens or closes (never per
  frame), and when the screen closes; other keys of the block (`maxWidth`, `maxHeight`) are kept. The
  open screen re-reads the block every second, so `/t3craft config reload` applies a hand edit.
- **Plugging a page in:** `RightPanels.register("diff", "Diff", MyDiffPanel::new)` from the feature's
  client init; any `T3Panel` works (it gets the same host API as a page in the thread's place).
- **Queue** (#39): the ⋯ menu's "Queue" opens the kit queue page (`QueuePanel`) in the thread's place.

Gallery: `/t3dev ui inbox-right-panel`.

## Own UI scale (#42, part of #15)

The T3 screens (this one, every kit page screen and kit dialog screen) can be drawn at their own GUI scale,
apart from Minecraft's, so the T3 Code-like UI gets more density on a large monitor without changing the
game's scale. Key `uiScale` in the same `layout` block:

```json
"layout": {"uiScale": "auto"}
```

- `"minecraft"` (default in this first delivery): the game's GUI scale, exactly as before; nothing is transformed.
- `"auto"`: the largest **whole** scale at which the window keeps at least 960×540 logical units (1080p → 2,
  1440p → 2, 4K → 4). Minecraft's "Auto" picks the largest scale that keeps 320×240, so this one is denser.
- a whole number 1..8: that scale, meaning the same as Minecraft's GUI scale number (never more than the game
  would allow for the window). Half steps are not offered: the bitmap font is uneven at them, and they wait for
  the TTF font of #15. A decimal is rounded.

Also in Settings → General → Display → *UI scale (T3 screens)*, written on Save. The open screen re-reads the
block every second (`/t3craft config reload` or a hand edit) and lays itself out again when the scale changes.

How (`t3ui/UiScale`): each root screen computes a `UiScale.Frame` in `init()` and sets its `width`/`height` to
the logical size, so `T3UiLayout` (frame cap, compact mode under 330×170, panel limits) works in logical units.
Drawing runs under a pose scaled by `scale / guiScale`, and during that pass the window reports the T3 scale as
its GUI scale (restored in a `finally`), so scissor rectangles, `getGuiScaledWidth()`, the IME candidate area and
tooltip placement need no change at their call sites; the frame's tooltips are drawn at the end of the scaled
pass. Mouse events (click, drag, release, wheel, move) are divided once in the root screen before any panel sees
them. Panels, kit widgets, menus and modals keep working in logical units and know nothing about it.

Gallery: `/t3dev ui inbox-scale` (forced `auto`), `inbox-scale-2`, `inbox-scale-3` (scale 3 with the right panel
open). If the game's own scale already equals the forced one the screen looks the same as `inbox`.

## What is copied from T3 Code (structure and behaviour only, no TS code)

| t3craft | T3 Code (`~/projects/t3code-ref`) |
|---|---|
| `T3UiLayout` (sidebar left, thread right; header / conversation / composer) | `apps/web/src/components/AppSidebarLayout.tsx`, `ChatView.tsx`, `chat/ChatComposer.tsx` |
| Sidebar chrome: brand + menu, search, project scope, "New thread", footer with settings | `apps/web/src/components/sidebar/SidebarChrome.tsx`, `Sidebar.tsx` |
| Pinned section first, then one folder per project, open/close folders | `Sidebar.logic.ts` `groupSidebarThreadsByProject`, `sortPinnedThreadsForSidebar`, `sidebar/SidebarProjectFolderHeader.tsx`, `sidebarFolderExpansionKeys` |
| Logical projects: same project on several environments = one folder, env shown as detail | `apps/web/src/sidebarProjectGrouping.ts` (`buildProjectGroups`, `environmentPresence`, `remoteEnvironmentLabels`) |
| Folder order by most recent thread, ties by name; threads by most recent activity | `Sidebar.logic.ts` `sortProjectsForSidebar` / `getProjectSortTimestamp`, `sortThreadsForSidebar` (updated_at) |
| Search folding case/accents, every word must match title/project/env | `Sidebar.logic.ts` `searchSidebarThreads`, `pullRequestMatchesSearchQuery` (`foldSearchQuery`, token match) |
| Project filter as a dropdown ("All projects" + each project) | `Sidebar.logic.ts` `filterSidebarProjectScopeItems`, `reduceSidebarProjectScopeMenuState` |
| Folder dot = most urgent hidden state | `Sidebar.logic.ts` `resolveSidebarFolderDot` |
| Row: state dot, title, relative time; pinned/env/project in the tooltip | `sidebar/SidebarThreadRow.tsx`, `resolveSidebarThreadStatus` |
| Header breadcrumb `project › title` | `apps/web/src/components/chat/ChatHeader.tsx` |
| Ctrl+B sidebar, ↑/↓ thread previous/next, Ctrl+N new thread | `packages/shared/src/keybindings.ts` (`sidebar.toggle`, `thread.previous/next`, `chat.new`) |
| Mobile-style one-panel sheet on small windows | the sidebar sheet of `AppSidebarLayout.tsx` |

## What is the game's own

- The environment is **not a filter** any more (the old env chips and the portable's environment
  column are gone). It shows as an env icon on rows when threads span several environments, in the
  folder tooltip and in the header.
- Ordering inside folders breaks ties by urgency (needs you → error → approval → working → done →
  idle), the mod's `Attention`, so a waiting thread does not sink among equals.
- ←/→ step the project filter; right-click on a folder filters that project.
- The ⋯ menu carries what the portable terminal had: **Notifications** (session log, shown in the
  thread panel; click → its thread), Archive, Environments, Settings, plus game shortcuts:
  **Locate the thread's desk** (closes the screen and prints the desk's coordinates and distance in
  the chat), hide/show sidebar, Close. The T3 key closes the screen again when the list has focus.
- Header actions reuse the existing guarded flows: Interrupt / Stop (`ThreadControlUi`, op-stop)
  and Settle / Snooze / Pin / Unpin / Read (`ThreadOpsBar`, op-settle). Read-only environments get
  disabled buttons ("Requires control"); destructive ones ask first. Archived threads (opened from the
  archive) are read-only: no actions, no composer.
- Visuals: AgentCraft's warm-studio chat theme (`ChatTheme`, `ChatDraw`).

## Components of the parallel branches (mounted)

Both branches were merged into this one (`origin/feat/chat-compose` 627e8e0, `origin/feat/t3-actions`
0334fb2) because they were not on main yet. `T3Screen` only talks to `T3Slots`:

| Where | Component | How |
|---|---|---|
| Bottom of the thread panel | feat/chat-compose `ComposerView` | `ChatComposer.create/attach/sync`: the conversation draws it as its `Footer`; `T3Screen` owns it (drafts survive dialogs) and forwards keys (all keys while focused, Esc leaves), chars, drag and scroll. Read-only (archive): none. |
| Composer bar, left slot | feat/t3-actions `ModelPicker` (provider · model · effort, access, plan) | `ModelPickerControl` (a `ComposerView.Control`), one picker per thread from `ModelPick.fromMeta(shell.meta())`; its popup gets clicks/keys/scroll first while open (`T3Slots.Popup`). |
| Header, right | feat/t3-actions `ThreadStatusChip` / `ThreadStatus.of(shell)` | `T3Slots.drawStatusChip`; without a shell (bridge mode) a pill with the attention word. |
| Sidebar "New thread", Ctrl+N, ⋯ | feat/t3-actions `NewThreadDialog` | environment from the selected thread, project from the sidebar filter (or the selected thread's project) mapped to a real T3 project by `ProjectMatch` (same name, selected environment first); the created thread is selected in the screen. |
| ⋯ menu | feat/t3-actions `RegisterProjectDialog`, `ActionsClient.archive` | "Register project…", "Archive thread" (destructive, asks first). |

feat/t3-actions' `ThreadActionsBar` was not used (and was removed in cleanup #3): the header keeps `T3HeaderActions` (Interrupt, Stop,
Settle, Snooze, Pin, Unpin, Read) and Archive lives in the menu.

## Keys

| Key | Action |
|---|---|
| ↑/↓, Tab/Shift+Tab | previous/next thread (opens it) |
| ←/→ | previous/next project filter |
| Ctrl+F | search (↑/↓ still step; Esc clears, then leaves the field) |
| Ctrl+N | new thread |
| Ctrl+B | hide/show sidebar (compact: switch panel) |
| U (rebindable) | show/hide the right panel (compact: open its page full-screen) |
| Enter | compact: open the selected thread; wide: focus the composer (then Enter sends, Shift+Enter newline, Esc leaves) |
| PgUp/PgDn/End | scroll the conversation |
| Esc | close menu → leave field → back to the list (compact) → close |
| Y | close (list focused) |

## Tests

`SidebarModelTest` (grouping and order, search + project filter, collapse, keyboard selection,
selection kept across reorders, opening from a block selects its thread and clears hiding filters),
`T3UiLayoutTest` (no overlap at many window sizes / GUI scales, compact mode, hidden sidebar, sidebar
hit-testing with scroll, reveal), `T3UiKeysTest` (navigation per focus, compact back-out, T3 shortcuts).
