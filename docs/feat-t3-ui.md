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
│ T3  12 threads · 2 ⋯  │ │ [env] project › Thread title      (● Working) │  header: breadcrumb + state chip
│ [ Search threads    ] │ │ [Interrupt][Stop][Settle][Snooze][Pin][…]     │  header actions
│ [ ▾ All projects    ] │ ├───────────────────────────────────────────────┤
│ [ + New thread      ] │ │ conversation (ConversationPanel, no header)   │
│ ▾ Pinned            1 │ │                                               │
│     ● Pinned one   2h │ │                                               │
│ ▾ t3craft        ● 3  │ │                                               │
│     ⚙ HQ layouts   5m │ ├───────────────────────────────────────────────┤
│     ! Fix login    1h │ │ composer slot (bottom)                        │
│ [Archive][Envs][Set.] │ └───────────────────────────────────────────────┘
└───────────────────────┘
```

Below 330×170 scaled pixels (narrow window or high GUI scale) the screen is compact: one panel at a
time. Opened on a thread (a block) it starts on the thread; opened with Y it starts on the list. `‹`
or Esc goes back to the list.

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

## Slots for the parallel branches (`T3Slots`)

`T3Screen` only talks to `T3Slots`; mounting a component is a change there:

| Slot | Component | Fallback on main |
|---|---|---|
| composer (bottom of the thread panel) | feat/chat-compose `ComposerView` via `ChatComposer.create/attach/sync`: the conversation draws it as its `Footer`; `T3Screen` owns it (drafts survive dialogs) and forwards keys (all keys while focused, Esc leaves), chars, drag and scroll. **Mounted** (branch merged into this one). | — |
| `composerControls()` (composer bar's left slot, `ComposerView.Control`) | feat/t3-actions `ModelPicker` | none |
| `newThread(...)` (sidebar button, Ctrl+N, menu) | feat/t3-actions `NewThreadDialog` | `NewThreadScreen` |
| `menuItems(...)` → "Register project…" | feat/t3-actions `RegisterProjectDialog` | not shown |
| `drawStatusChip(...)` (header) | feat/t3-actions `ThreadStatusChip` | pill with the attention word |

## Keys

| Key | Action |
|---|---|
| ↑/↓, Tab/Shift+Tab | previous/next thread (opens it) |
| ←/→ | previous/next project filter |
| Ctrl+F | search (↑/↓ still step; Esc clears, then leaves the field) |
| Ctrl+N | new thread |
| Ctrl+B | hide/show sidebar (compact: switch panel) |
| Enter | compact: open the selected thread; wide: focus the composer (then Enter sends, Shift+Enter newline, Esc leaves) |
| PgUp/PgDn/End | scroll the conversation |
| Esc | close menu → leave field → back to the list (compact) → close |
| Y | close (list focused) |

## Tests

`SidebarModelTest` (grouping and order, search + project filter, collapse, keyboard selection,
selection kept across reorders, opening from a block selects its thread and clears hiding filters),
`T3UiLayoutTest` (no overlap at many window sizes / GUI scales, compact mode, hidden sidebar, sidebar
hit-testing with scroll, reveal), `T3UiKeysTest` (navigation per focus, compact back-out, T3 shortcuts).
