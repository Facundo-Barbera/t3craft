# Citadel Studio

A small web page for watching and steering the citadel design swarm (the agents polishing the
`hybrid-ringcampus` HQ layout). Facundo opens it on his Mac or phone over Tailscale. With it he can:

- see every agent's renders;
- compare before and after;
- leave notes for an agent;
- ask for renders of his own.

It is one Python 3 file, standard library only (`tools/studio/studio.py`), plus `tools/studio/static/`.
It does no image processing: the PNGs of the render service are served as they are from disk, with
`ETag` / `Last-Modified`.

## For the coordinator: start / stop

```bash
tools/studio/start.sh                       # background, nice -n 10, on http://100.64.0.30:8765/
tools/studio/start.sh --host 100.x.y.z --port 8765
tools/studio/start.sh --status
tools/studio/start.sh --stop
```

Log and pidfile: `~/.cache/t3craft-studio/studio.log`, `studio.pid`. It refuses to listen on `0.0.0.0`.
It only reads the spool and two coordinator files, so it can run from any checkout. Restart it after
pulling a new version.

Environment variables (optional):

- `T3CRAFT_RENDER_HOME`: render spool, default `~/.cache/t3craft-render`.
- `T3CRAFT_COORD_HOME`: coordinator folder, default `~/.cache/t3craft-coord`.
- `T3CRAFT_WORKTREES`: worktree root, default `~/.t3/worktrees/t3craft`.
- `STUDIO_QUIET=1`: no access log.

### Agent list (optional)

The page shows every agent that has jobs in the outbox with the `citadel-` prefix (and Facundo's own
`facundo-*` requests). To give agents a title, a brief, or a worktree, or to list them before their first
render, write `~/.cache/t3craft-coord/studio-agents.json`:

```json
[{"agent": "citadel-2-office", "title": "Oficina",
  "brief": "/home/user/.cache/t3craft-coord/p-2-office.md",
  "worktree": "/home/user/.t3/worktrees/t3craft/citadel-2-office"}]
```

Without it, the title comes from the name (`citadel-2-roofs-yards` → "Roofs yards", fase 2). The worktree
defaults to `~/.t3/worktrees/t3craft/<agent>` when that folder exists. The file is re-read on every request.
Briefs are served only if they are `.md` files under `~/.cache/t3craft-coord/`.

## Using it

URL: **http://100.64.0.30:8765/** (Tailscale).

- **Tablero** (`/`): one card per agent, showing:
  - the state of its last job, the number of jobs and images, and how long ago;
  - thumbnails of its last job with images;
  - unread notes.

  The header shows the render daemon's heartbeat: state, queue, and load against the pause limit. The
  page refreshes itself every 10 s.
- **Agent** (`/agent/<agent>`), with four tabs:
  - **Historial**: every job, newest first, with its arguments, state, duration, wait in the queue,
    images (`compare_*.png` shown wide, as before/after), `report.txt`, the other files, and the log
    on errors. Click an image to open the viewer:
    - wheel / pinch / `+` `-` to zoom, drag to pan, `0` to fit, `1` for real size, `←` `→` for the
      next image;
    - **Nota** to use the image as the reference of a note.
  - **Comparar**: pick a "before" job and image and an "after" job and image. The "after" list puts
    the same view name first (`iso_se`, `plan`…). Show them side by side or with a slider overlay.
  - **Feedback**: write a note, optionally with a reference image, and see earlier notes and whether
    the agent has read them.
  - **Pedir render**: a render request (below), for this agent's worktree by default.
- **Pedir render** (`/render`): the same form, defaulting to the `render-baseline` worktree.

### Render requests from the studio

The form builds the request; the user never types raw arguments. It has these fields:

- layout (`hybrid-ringcampus` by default);
- projects (`4,10,20`);
- target: everything (with `--report`), `--group`, `--anchor` + `--radius`, or `--region`;
- views (`--only`);
- optional `--name`;
- `preview` or `final`;
- worktree.

Every field is checked against a strict pattern. The worktree must be a folder under
`~/.t3/worktrees/t3craft/`. The request is written atomically to `inbox/` as
`facundo-<agent>-<millis>.json` (`facundo-studio-…` from `/render`), following
[render-service.md](render-service.md). Nothing is executed. The studio follows the service's rule of
one pending request per requesting agent. The page then follows the job's state live and shows its
images when it is done.

The agent does not see these renders unless you tell it: leave a note pointing at the image.

### Security

- It listens only on the given IP (Tailscale).
- Agent and job names are matched against `[A-Za-z0-9._-]`. Served files must resolve inside the
  outbox (`outbox/<id>/<file>.png|txt|json|log`), so there is no path traversal.
- POSTs need the `X-Studio: 1` header and `application/json`, with a body of 64 KB at most.
- The page has a strict CSP and builds every value as text, never as HTML.

## Feedback protocol (for agents)

Facundo's notes for you are in:

```
~/.cache/t3craft-coord/feedback/<your agent name>.md      e.g. feedback/citadel-2-office.md
```

Your agent name is the one you render with (`$T3CRAFT_AGENT` or your worktree's folder name, e.g.
`citadel-2-office`). The file only exists once Facundo has written something. Each note is appended
like this:

```markdown
### note 2026-11-08T23:26:37+01:00
ref: `/home/user/.cache/t3craft-render/outbox/citadel-2-office-1794175755619/before_office_iso_se.png`

Make the balcony wider and put a lantern on each corner.
```

The `ref:` line is optional. It is the PNG he was looking at; open it with your image viewer (outbox
folders are deleted after 24 h).

What to do:

1. **Check the file** when you start, before each render request, and before you report back.
2. Notes are in time order. **A note is new if no `<!-- read: … -->` line comes after it.** Act on all the
   new ones; a later note wins over an earlier one.
3. When you have read them, **append one line at the end of the file** (append only: never rewrite or
   delete notes):

   ```bash
   printf '<!-- read: %s -->\n' "$(date -Is)" >> ~/.cache/t3craft-coord/feedback/citadel-2-office.md
   ```

   The studio then shows those notes as read ("leída") to Facundo. Marking read means "seen and taken
   into account". If you disagree or cannot do something, say so in your report to the coordinator.
4. Do not write notes yourself in this file; it is Facundo's channel to you.
