# feat/t3-composer-fidelity: T3 Code's composer, and new threads as drafts

References: `t3craft-ref/ui/t3code-real.png` (T3 Code, composer at the bottom centre) and
`t3craft-ref/ui/t3craft-ingame.png` (our screen before feat/t3-ui-clean). T3 Code sources read in
`t3code-ref`: `ChatComposer.tsx`, `ComposerPrimaryActions.tsx`, `ComposerControlsBar.tsx`,
`BranchToolbarEnvModeSelector.tsx`, `TimelineUserMessageRow.tsx`,
`packages/client-runtime/src/state/composerDispatch.ts`, `operations/commands.ts`.

## 1. The composer (`dev.t3craft.client.chat.compose`)

- **Box**: one dark rounded box (`ComposerSkin`: radius drawn with fills, so it stays crisp at
  every GUI scale and needs no texture; the paper nine-slice kit is cream and the composer is not).
  It sits in T3's column: `ConversationPanel.setColumn(T3UiLayout.COLUMN_MAX = 460)` centres the feed
  and the composer at the same max width, puts the composer inside the panel under the feed and drops
  the inset frame around the feed. `ComposerView.setMaxWidth` does the same for other hosts.
- **Top**: thumbnails of the images waiting to go; hovering one shows its ✕ (Shift+click still
  removes it); the tooltip gives name and size.
- **Text**: grows from 2 to 8 lines, then scrolls (wheel or caret). Placeholder "Ask anything…".
  Enter sends, Shift+Enter is a newline.
- **Editing** (`ComposerText`): caret, mouse selection (click, drag, Shift+click, **double click
  selects a word**), Shift+arrows, Ctrl+A/C/X/V (Ctrl+V pastes an image when the clipboard has one),
  Ctrl+←/→ and Ctrl+Backspace/Delete by word, Home/End (Ctrl: whole text), **Ctrl+Z undo and Ctrl+Y
  / Ctrl+Shift+Z redo** (typing is grouped per word, every other edit is its own step, 200 steps;
  switching thread starts a fresh history). Text still arrives through SDL3 text input
  (`TextInputManager`), key and mouse codes are `InputConstants` / `MouseButtons`.
- **Bar**: clip on the left; on the right the model chip ("✳ Opus 5.5 · Medium ▾": provider glyph,
  model without the provider prefix, effort, "· Plan" in plan mode; `ModelPicker.composerChip()`),
  whose popover opens upwards aligned to its right edge; an access chip ("Full access", "Auto"…) only
  when the access is not the default, as T3's `ComposerFooterModeControls`; then the round button.
- **Round button**: indigo ↑ send, grey and disabled while there is no text and no image; red ■
  while a turn runs and the input is empty (interrupt, confirmed in game); walking dots while in
  flight. The tooltip says what it does.
- **Steer / queue** (`ChatSender.Delivery`), as `resolveComposerDispatchMode`: while a turn runs a
  message **steers** it (`deliveryIntent:"steer"`, `dispatchMode:start_immediately`); **Ctrl+Enter
  or Ctrl+click queues** it (`dispatchMode:queue_after_active`, no `deliveryIntent`); idle threads
  keep `deliveryIntent:"auto"`. `activeSteeringAcceptsAttachments` is the server's business: a steer
  with images on a text-only provider restarts the turn there, so the client sends the same command.
  The server echoes `inputIntent:"steer"` (or `promoted_queued_to_steer`) on the `user_message`;
  `TurnItems` keeps it in the entry's title and the feed draws **"↪ Steer"** over that message (the
  optimistic row shows "↪ Steer" / "⏱ Queued" until the echo replaces it).
- **Drafts** (`ComposerDrafts.shared()`): text and caret per thread for the whole game session, so
  switching thread or closing the screen keeps them; images waiting in a draft too (memory only).
  The texts are also written to `config/t3craft/cache/drafts.json` (`env:threadId` → text + caret;
  no tokens, URLs or images), coalesced on a daemon thread, and read at the next game start.
  Saved when you switch thread, at most every 1.5 s while typing, and when the screen closes.
- **Errors** (send, launch, images): one discreet red line just above the box; hover for the full
  text, click to dismiss; editing clears it.
- **No control** (`OperateGate`): the placeholder says so, the bar reads "Read-only" and carries an
  "Enable control" chip in place of the controls (opens the existing enable/pair screen). No windows.
- Gone: the "Working…" status in the bar (the feed already shows it) and the clip/model/send
  borders.

## 2. New thread = draft, as in T3 Code

- "New thread" (the sidebar's ✎ next to search, the "+" that appears when hovering a project folder,
  Ctrl+N, the ⋯ menu, and `NewThreadScreen.open(env, project)` from blocks) **opens no window**. It adds
  a draft row ("✎ New thread") under its project, selects it, and the thread panel shows
  "project › New thread" with an empty transcript and the same composer, focused.
- The draft's composer bar adds, after the clip, the **project chip** (`▣ project`, or
  `env · project` with several environments) whose popover lists every project of every connected
  environment, and the **workspace chip** (`▢ Local` / `⑂ New worktree from main`) whose popover has
  Local / New worktree, and for a worktree the base branch (prefilled `main`), an optional branch
  name and "Start from origin" (T3's `BranchToolbarEnvModeSelector` + `WorktreeBaseBranchPicker`;
  there is no branch list over the operate API, so the base branch is typed). The model chip edits
  the draft's model (new-thread defaults of that environment, as before).
- **Sending the first message** launches the thread: same `orchestration.launchThread` as before
  (`NewThreadForm.build`, through `OperateGate`; `DraftLauncher`). Images go first with
  `assets.persistChatAttachments` under the new thread id and ride in `initialMessage.attachments`.
  On success the draft row disappears and the real thread is opened and stays selected (as soon as
  the shell lists it); on failure the text and images come back with the reason above the box.
- **Discard**: ✕ on the row or in the header, or Esc (in an empty composer; with text the first Esc
  leaves the input and the second discards). Back to the thread shown before.
- One draft at a time (like T3's one draft per project): asking for another project moves it there
  keeping its text. Closing the screen keeps the draft for the next time the screen opens.
- `NewThreadDialog` is deleted; `T3Slots.newThread` is replaced by `T3Slots.draftProject`.

## 3. Cleanups

- Sidebar: the project filter and "+ New thread" rows became two icons at the end of the search row
  (▾ filter, lit when a project is chosen; ✎ new thread), as T3's search row. Search placeholder
  "Search" with a ⌕.
- Header: the "■ Stop" button is gone (the composer's ■ interrupts; Stop thread stays in the row's
  right-click menu).
- Feed: no inset frame; the composer shares the feed's column.
- Menus and popovers draw in a new GUI stratum (`nextStratum`) so the transcript's text no longer
  shows through them.

## Files

- `chat/compose/ComposerView.java` (rewritten), `ComposerSkin.java` (new), `ComposerText.java`
  (undo/redo, word select), `ComposerState.java` (shared drafts, steer/queue, draft launcher),
  `ComposerDrafts.java` (shared + disk), `ChatSender.java` (`Delivery`, `upload`, `run`),
  `ChatComposer.java` (Esc hook)
- `chat/ConversationPanel.java` (`setColumn`, "↪ Steer" marker), `ChatEntry.java` (`STEER`,
  `QUEUED`), `TurnItems.java` (`inputIntent`)
- `t3ui/T3Screen.java`, `T3UiLayout.java` (`COLUMN_MAX`, search-row icons), `T3Slots.java`,
  `ModelPickerControl.java`, `ThreadDraft.java`, `DraftControls.java`, `DraftLauncher.java` (new)
- `t3ui/actions/ModelPicker.java` (composer chip), `NewThreadForm.java` (doc);
  `NewThreadDialog.java` deleted; `screen/NewThreadScreen.java` (`open` → draft)

## What to try

1. Y: the composer is a dark rounded box centred under the transcript, same width as it.
2. Type several lines (Shift+Enter): it grows to 8 lines, then scrolls. Double click a word, drag to
   select, Ctrl+Z / Ctrl+Y, Ctrl+←/→, Home/End.
3. Paste an image (Ctrl+V) or use the clip: thumbnail on top, ✕ on hover.
4. Model chip: "✳ Opus 5.5 · Medium ▾"; the popover opens upwards.
5. In a running thread, send a message: it shows "↪ Steer" (Ctrl+Enter queues it instead). With an
   empty input the button is a red ■.
6. Write something, switch thread, close the screen, restart the game: the text is still there.
7. ✎ (or hover a project and click "+"): a "New thread" row under the project, breadcrumb
   "project › New thread", project and workspace chips in the bar. Switch project, choose New
   worktree, send: the thread launches and replaces the row. Esc / ✕ discards a draft.
8. Read-only environment: the bar says "Read-only" with "Enable control".
