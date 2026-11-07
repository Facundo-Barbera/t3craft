import type { HudPeek, NpcTransition, NpcView } from "../protocol.ts";
import { STATE_STYLES } from "./presentation.ts";

export type NpcChange =
  | { readonly kind: "upsert"; readonly npc: NpcView }
  | { readonly kind: "remove"; readonly npcId: string };

/**
 * What a state change of a visible NPC should tell the player (chat + toast + sound), or null.
 * Decided here so wording and sounds change with a bridge restart only. New NPCs (no `before`)
 * never notify: initial state is seeded silently.
 */
export const transitionFor = (
  before: NpcView | undefined,
  after: NpcView,
): Omit<NpcTransition, "v" | "type" | "id"> | null => {
  if (before === undefined || before.state === after.state) return null;
  const base = {
    npcId: after.id,
    from: before.state,
    to: after.state,
    color: STATE_STYLES[after.state].color,
    text: `${after.title} · ${after.groupLabel} (${after.envLabel})`,
  };
  switch (after.state) {
    case "needs_attention":
      return { ...base, level: "warn", sound: "attention", title: after.activity ?? "Needs your input" };
    case "error":
      return { ...base, level: "error", sound: "error", title: "Thread failed" };
    case "done":
      return before.state === "working" || before.state === "needs_attention"
        ? { ...base, level: "info", sound: "done", title: "Thread finished" }
        : null;
    default:
      return null;
  }
};

/**
 * HUD peek: the single most relevant thread, one line. Needs-attention first, then working;
 * nothing otherwise (the HUD hides). `npcs` must be in store order (priority first).
 */
export const peekFor = (npcs: ReadonlyArray<NpcView>): Omit<HudPeek, "v" | "type" | "id"> => {
  const pick = npcs.find((n) => n.state === "needs_attention") ?? npcs.find((n) => n.state === "working");
  if (pick === undefined) return { text: null, color: STATE_STYLES.idle.color, npcId: null };
  const others = npcs.filter((n) => n.state === pick.state).length - 1;
  const more = others > 0 ? `  (+${others} more)` : "";
  return {
    text: `${pick.glyph} ${pick.title} [${pick.envLabel}]${pick.activity ? ` — ${pick.activity}` : ""}${more}`,
    color: pick.color,
    npcId: pick.id,
  };
};

/**
 * All NPCs across environments. Each environment replaces its own slice wholesale; the store
 * diffs against what the mod already has and returns only real changes.
 */
export class NpcStore {
  private readonly byId = new Map<string, NpcView>();

  all(): NpcView[] {
    return [...this.byId.values()].sort(
      (a, b) => a.priority - b.priority || a.envLabel.localeCompare(b.envLabel) || a.title.localeCompare(b.title),
    );
  }

  get(id: string): NpcView | undefined {
    return this.byId.get(id);
  }

  /** Replace every NPC of `env` with `views`. */
  replaceEnvironment(env: string, views: ReadonlyArray<NpcView>): { changes: NpcChange[]; previous: Map<string, NpcView> } {
    const previous = new Map<string, NpcView>();
    const changes: NpcChange[] = [];
    const next = new Map(views.map((v) => [v.id, v]));
    for (const [id, old] of this.byId) {
      if (old.env !== env) continue;
      previous.set(id, old);
      if (!next.has(id)) {
        this.byId.delete(id);
        changes.push({ kind: "remove", npcId: id });
      }
    }
    for (const [id, v] of next) {
      const old = this.byId.get(id);
      if (old === undefined || JSON.stringify(old) !== JSON.stringify(v)) {
        this.byId.set(id, v);
        changes.push({ kind: "upsert", npc: v });
      }
    }
    return { changes, previous };
  }
}
