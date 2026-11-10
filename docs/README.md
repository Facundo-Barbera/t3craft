# docs/

What lives where. Rules for agents are in `../AGENTS.md`.

| path | what |
|---|---|
| `CHANGELOG-dev.md` | One RELOAD/RESTART line per structural commit on `main`. Taller reads it at this exact path; do not move it. |
| `dev.md` | How to build and iterate on the mod (Gradle only on the render PC). |
| `api.md` | Map of the mod's public classes. |
| `features/` | One doc per feature or fix (`<name>.md`, fixes as `fix-<name>.md`). New features add a file here. |
| `plans/` | Technical plans (`plan.md`, `plan-v2.md`) and design proposals. |
| `../bridge/README.md` | The bridge and `protocol/` are legacy and frozen (D2, #7): what it is, what replaces it (direct mode), what changes are allowed. |
| `tools/` | Render tooling: hq-render, ui-render, icon pipeline (`tools/icons.md`), render service (PC daemon + relay), Citadel Studio, catalog gallery. |
| `research/` | Research notes (HQ layouts, styles, AgentCraft elements, compat). The render PNGs were removed from the tree; they are in the tag `archive/research-renders-2026-11-09`. |
| `ops/` | Coordination: `pending-execution.md` (what still needs running), nightly summaries, `incidents/`, `reviews/`. |

No images go into `docs/` (`.gitignore` blocks `docs/**/*.png`): renders live on the render PC and are viewed in
Citadel Studio. Threads never open full-size renders (`AGENTS.md`, rule 9).
