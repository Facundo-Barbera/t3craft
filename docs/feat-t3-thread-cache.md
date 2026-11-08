# feat/t3-thread-cache — thread cache

Goal: opening or switching threads in the T3 screen (Y) should feel instant. Before this change every
view opened its own `orchestration.subscribeThread`, folded the stream into a brand-new
`ChatTranscript` and dropped all of it on close. Coming back to a thread meant waiting for a new
snapshot (up to `ThreadFeed.ANSWER_TIMEOUT_MS` = 20 s before it says so). Older pages and full turn
items were fetched again every time, and the sidebar list was rebuilt on every tick of the screen.

## What is cached, and where

| What | Where | Limits |
|---|---|---|
| Transcripts (rows, runs, requests, cursor) per `env:threadId` | memory, `ThreadCache` (LRU, access order) | 24 threads, about 6 M chars of rows (`approxChars`: body + detail + title + 96 per row). Threads a view holds are never evicted |
| Live `subscribeThread` subscriptions with no view ("lingering") | memory, `ThreadCache.Entry.sub` | 60 s after the last view closes, at most 4 at once (the oldest released closes first) |
| `threadHistory` pages, by cursor | memory, per entry | 8 pages per thread |
| `getTurnItem` results, by item id | memory, per entry | 64 items per thread, only finished ones (no `streaming`, status not pending/running/…) |
| Sidebar rows | memory, `ThreadListCache` (memoized) | rebuilt only when a shell view, the environment labels or the 5 s time bucket change |
| Last list + tails of the most recent threads | disk, `config/t3craft/cache/threads.json` | 400 rows; 6 threads × last 40 finished rows; bodies clipped to 4000 chars; tool details/output dropped |

The disk file holds no tokens, URLs or credentials: only what the sidebar shows and the text of the
last rows. Thread text can still contain whatever you typed in a thread. The file is optional and
disposable: delete `config/t3craft/cache/` whenever you like.

## How it behaves

- **Reopening a cached thread** paints its rows at once. A small `⟳ updating` pill sits at the top
  right of the feed until the new snapshot is in (`⟳ cached` for rows read from disk,
  `offline` when the environment is down). The snapshot is then reconciled over the cached rows
  (`ChatTranscript.applyStreamItems`, one lock per batch, so a half-applied batch is never drawn).
  Rows keep their keys, so nothing is duplicated, and older pages you already loaded stay put when they
  line up with the new window. The history cursor is kept too, so you don't jump or fetch again.
- **Switching threads** releases the old one into the lingering set, so going back within 60 s
  reuses the live subscription. Nothing is fetched and the transcript stays up to date.
- **Prewarm**: when the pointer rests 250 ms on a sidebar thread, it is subscribed in the background.
  When the screen opens (once the list is there), up to 3 threads are warmed: the last one opened,
  then running ones, then pinned ones. Prewarm goes through a token bucket (3 at once, 1 more per
  second), only touches environments whose connection is `OK`, and counts towards the 4 lingering
  subscriptions. Keyboard moves in the sidebar open the thread directly, so the thread you leave
  lingers.
- **Sidebar**: `T3Screen.refresh` only rebuilds `SidebarModel` when the directory returns a
  different list. After a game restart, environments whose shell has not synced yet show the rows
  from disk, and the sidebar header says `· cached` until the real list replaces them.
- **Invalidation**: when an environment's connection is replaced (`ChatSource.Env.current()` turns
  false after a reload, a pairing or Enable control), the open view already re-asks the source
  (`ConversationPanel.tick`). `ThreadCache.acquire` then closes the old subscription and subscribes
  on the new connection, keeping the transcript so the view still paints at once. Lingering
  subscriptions on a replaced connection are closed by the housekeeping. A thread that is no longer
  in its environment's synced shell (archived or deleted) leaves the cache, unless a view, such as
  the archive's read-only view, still holds it.

## Concurrency

- `ThreadCache`'s monitor guards the map, the refcounts and the subscriptions. The client thread
  calls in: views, prewarm and the tick.
- Stream listeners run on the connection thread. They only touch the entry's volatile fields and its
  self-synchronized `ChatTranscript`, and never take the cache monitor, so there is no lock cycle with
  `StreamRegistry`.
- History and `getTurnItem` complete on connection threads and write only the entry's own
  synchronized maps and the transcript. Nothing blocks on a future. The render reads the
  transcript's immutable `View`.
- Disk reads and writes run on one daemon thread (`t3craft-thread-cache`). The JSON is built on the
  client thread from bounded data, written to a temp file and then moved into place.

## Server load

Each thread has at most one server subscription, shared by every view (screen, monitors) through the
existing refcounted `StreamRegistry`. Only 4 subscriptions without a view can be open at once, and
they close after 60 s. Prewarm is rate-limited and skipped on environments that are not connected.
Cached history pages and finished items are not fetched again.

## Code

- `client/chat/ThreadCache.java`: the LRU, the lingering subscriptions, prewarm, invalidation and
  housekeeping.
- `client/chat/ThreadFeed.java`: now a view over a cache entry (same API). `State` adds
  `refreshing` and `fromDisk`.
- `client/chat/ChatTranscript.java`: `applyStreamItems`, re-snapshot reconciliation (keeps the
  aligned older prefix), `seed` (disk rows), `tail`, `approxChars`, `snapshots`.
- `client/chat/ThreadListCache.java`: memoized sidebar rows, the disk fallback, and the client tick
  hook (once a second) with saves every 30 s and when the T3 screen closes.
- `client/chat/ThreadDiskCache.java`: `config/t3craft/cache/threads.json` (format 1).
- `client/t3ui/T3Screen.java`: skips the model rebuild when the list is unchanged, prewarms on
  open and on hover, shows `· cached`.
- `client/chat/ConversationPanel.java`: the `⟳ updating` pill, and `noteOpened`.
- `client/T3CraftClient.java`: calls `ThreadListCache.clientTick` from the existing client tick.

Restart needed: this adds new classes with static state, a new call in the client tick and a
changed record shape (`ThreadFeed.State`). No config keys, registries, keybinds or lang changes.

## Sidebar: every thread, as T3 Code (fix/t3-sidebar-all-threads)

Bug: a thread pinned on another machine, idle for 3 days, did not show in the Y screen although the
environment was connected. The sidebar took its rows from `DirectController.npcs(now)`, the NPC
selection of `dev.t3craft.t3.Visibility` (needs you / running / active in the last 24 h, at most 40
per environment), so an old pinned thread was left out.

Now `ThreadListCache.rows` builds the rows from each environment's full shell
(`DirectController.shellViews()` → `ShellState.View.threads()`), like T3 Code's sidebar:

- Listed: every thread that is not archived, deleted, a subagent or a side chat
  (`ThreadListCache.listed`). No recent window, no per-environment cap.
- Row fields as before (title/state/activity via `Presentation.present`, last activity via
  `Visibility.lastActivity`, pending request, pinned); `settled` now uses `Visibility.isSettled`
  (honours `settledOverride`).
- `SidebarModel`: **Pinned** first (always, whatever the age or settled state), then one folder per
  project with the active threads sorted by activity, then a **Settled** section with its count,
  closed by default (`SidebarModel.SETTLED`; opens on click, while searching, or when `focus` opens a
  settled thread). Search and project filter apply to all three.
- Memo: the key is shells + labels + disk load only (rows no longer depend on time, the 5 s bucket is
  gone), so `T3Screen` still rebuilds only on shell changes. The disk cache saves the real rows in
  `SAVE_ORDER` (pinned, active, settled; each by activity), so the 400-row cap never drops a pinned one.
- Drawing was already virtualized: `T3Screen.drawSidebar` draws only the rows in the visible window;
  the model is rebuilt once per change (O(n)), not per frame.
- The header "N threads" and the screen-open prewarm read the same list (so an old pinned thread is
  counted and can be prewarmed).
- NPCs unchanged: `DirectController.npcs` and `Visibility` are untouched.
- Other users of `ChatScreens.defaultDirectory()` see the full list too: the embeddable
  `ThreadBrowser` and the thread walls (walls already skip settled rows, so they now also show older
  active threads).

**Bridge mode limitation:** without a direct controller the rows come from the bridge's NPC model,
which the bridge already filters with the same visibility rules (24 h, cap). An old pinned or settled
thread does not show there; listing them would need a separate thread list from the bridge (the NPC
list must stay as is), not done here.
