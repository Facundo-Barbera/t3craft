import type { NpcView, Notify } from "../protocol.ts";
import { STATE_STYLES } from "./presentation.ts";

export type NpcChange =
  | { readonly kind: "upsert"; readonly npc: NpcView }
  | { readonly kind: "remove"; readonly npcId: string };

/** What a state change should tell the player (toast), or null. Bridge-side so it can be tuned live. */
export const notificationFor = (
  before: NpcView | undefined,
  after: NpcView,
): Omit<Notify, "v" | "type" | "id"> | null => {
  if (before === undefined || before.state === after.state) return null;
  const color = STATE_STYLES[after.state].color;
  const base = { color, npcId: after.id, text: `${after.title} (${after.envLabel})` };
  switch (after.state) {
    case "needs_attention":
      return { ...base, level: "warn", title: after.activity ?? "Needs your attention" };
    case "error":
      return { ...base, level: "error", title: "Thread failed" };
    case "done":
      return before.state === "working" ? { ...base, level: "info", title: "Thread finished" } : null;
    default:
      return null;
  }
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
