# feat/t3-ui-clean: T3 screen (Y) closer to T3 Code, less noise, images in the composer

References: `t3craft-ref/ui/t3code-real.png` (T3 Code) and `t3craft-ref/ui/t3craft-ingame.png`
(the screen before this branch).

## Layout

- **No action row.** Interrupt / Stop / Settle / Snooze… / Pin / Unpin / Read are gone from the
  header, which is now one row: env icon, `project › thread` breadcrumb, state chip.
  - **Right-click on a sidebar thread** opens its menu (`T3ThreadActions.menu`): Interrupt turn
    and Stop thread (only while it runs), Settle, Snooze…, Pin/Unpin, Mark as read, Archive,
    Locate the thread's desk; writes are disabled on a read-only environment, which adds
    "Enable control…". Right click = `MouseButtons.isRight` (26.x button 3, not GLFW's 1).
  - **Hover a sidebar thread**: the age gives way to two icons, ✓ settle and ☆/★ pin/unpin.
  - **While the thread runs**: the composer's round send button turns into ■ (interrupt the turn,
    `run.interrupt`), and the header shows a small "■ Stop" (`thread.stop`) next to the state.
  - Every action keeps its guarded flow (`ThreadOpsUi`, `ThreadControlUi`, `ActionsClient.archive`
    over `OperateUi.run`: nothing is sent read-only, destructive ones ask in game).
  - The header's flash line is now a toast (`T3ThreadActions.notice`).
- **Sidebar footer**: Archive / Envs / Settings buttons → three discreet icons bottom-left
  (⚙ settings, ☁ environments, ▤ archive) with tooltips. The ⋯ menu still lists them too.
- **Conversation**: no key-cap hint bar (↑↓ thread, PgUp scroll, Ctrl+F search, Esc close; the keys
  still work) and no minute separators (`ConversationPanel.setShowHints/setShowSeparators`, both
  default on for the other hosts).
- **Composer** (T3's): image thumbnails on top, the text, then a bar with the clip on the left and,
  on the right, the model picker and a round send button (↑ send, ■ stop, … in flight). The
  "Enter to send · Shift+Enter…" hint and the bar's separator line are gone (the send button's
  tooltip says it). Errors and "Working…" still show in the bar.

## Images

- **Clip button** → the platform's file dialog via SDL3 (`SDL_ShowOpenFileDialog`; Minecraft 26.x
  ships `lwjgl-sdl` and no longer `lwjgl-tinyfd`), PNG/JPEG/GIF/WebP filter, several files at once.
  If SDL cannot open a dialog, the input turns into a **path field** (Enter attaches, Esc cancels;
  quotes, `~` and `file://` accepted) and the draft comes back afterwards.
- **Ctrl+V** pastes an image when the clipboard has one (`SDL_GetClipboardData` for
  `image/png|jpeg|webp|gif`, then `text/uri-list` for a file copied in a file manager, then AWT if
  the JVM is not headless); otherwise it pastes text as before.
- **Drop** image files on the window: they go to the composer (`T3Screen.onFilesDrop`).
- **Thumbnails** (`ImageThumbs`): stb_image off-thread, ≤ 64 px texture; WebP (no decoder in game)
  shows a "WEBP" tile. ✕ in the corner (or Shift+click) removes it; hover shows name and size.
  Images are kept per thread like drafts.
- **Limits** (`ImageAttachment`, from `chatAttachment.ts`): type sniffed from the bytes (T3 checks
  the data URL type against `mimeType`), ≤ 10 MiB each, ≤ 80 MiB and ≤ 100 per message, name ≤ 255,
  data URL ≤ 14 M chars. A refusal shows in the composer as the error.
- **Sending** (`ChatSender.send(…, images, …)`), as T3's web composer: `assets.persistChatAttachments
  {threadId, messageId, attachments:[{type:"image", name, mimeType, sizeBytes, dataUrl}]}` →
  stored `ChatAttachment`s → `message.dispatch` with them in `attachments` (same `messageId`). Both
  over the direct operate connection (`OperateAction.PERSIST_ATTACHMENTS` is allowed next to
  `orchestration.*` and `projects.mutate`; T3 authorizes it with `orchestration:operate`). Images
  alone (empty text) can be sent. On failure the text and the images come back to the composer
  with the reason ("Image upload failed: …"; without a direct connection: "Images go straight to
  T3 … pair it directly").
- **Bridge**: read-only and its mod socket caps frames at 256 KiB, so images never go through it.
  `protocol/protocol.md` documents `chat.send.attachments`; the bridge validates them against the
  contract (`bridge/src/state/attachments.ts`) and acks `ok:false` with the reason or with
  "not implemented: the bridge is read-only …".

## Caveats

- The upload rides the same 20 s operate call timeout; a 10 MB image over a slow link may time out
  ("may or may not have applied").
- The SDL dialog and clipboard are used directly; if a platform's SDL lacks them the code falls back
  (path field / text paste) instead of failing.

## Files

- `mod/src/client/java/dev/t3craft/client/t3ui/T3Screen.java`, `T3UiLayout.java`,
  `T3ThreadActions.java` (new; replaces `T3HeaderActions`)
- `mod/src/client/java/dev/t3craft/client/chat/compose/ComposerView.java`, `ComposerState.java`,
  `ChatSender.java`, `ImageAttachment.java`, `ImageSources.java`, `ImageThumbs.java` (new)
- `mod/src/client/java/dev/t3craft/client/chat/ConversationPanel.java` (two switches)
- `mod/src/main/java/dev/t3craft/t3/OperateAction.java` (`PERSIST_ATTACHMENTS`)
- `bridge/src/protocol.ts`, `bridge/src/state/attachments.ts`, `bridge/src/main.ts`,
  `bridge/src/t3/contracts.ts`; `protocol/protocol.md`
