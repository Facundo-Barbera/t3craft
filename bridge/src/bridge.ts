import * as Effect from "effect/Effect";
import * as Redacted from "effect/Redacted";
import type { BridgeConfig, EnvironmentConfig } from "./config.ts";
import { readSecretFile } from "./config.ts";
import { PROTOCOL_VERSION, type BridgeToMod, type EnvironmentStatus } from "./protocol.ts";
import { NpcStore, peekFor, transitionFor } from "./state/npcStore.ts";
import { presentThread } from "./state/presentation.ts";
import { selectVisibleThreads, type VisibilityOptions } from "./state/visibility.ts";
import { runEnvironment, type ShellState } from "./t3/shell.ts";

export const BRIDGE_VERSION = "0.1.0";

/** How often visibility is re-evaluated without T3 traffic (recent-window expiry). */
export const REEVALUATE_INTERVAL_MS = 60_000;

export const visibilityOptions = (config: Pick<BridgeConfig, "visibility" | "includeSubagents">): VisibilityOptions => ({
  recentWindowMs: config.visibility.recentWindowHours * 3_600_000,
  maxPerEnvironment: config.visibility.maxPerEnvironment,
  includeSubagents: config.includeSubagents,
});

/** Turns one environment's shell state into NpcViews, applying the visibility filter. */
export const viewsFor = (env: EnvironmentConfig, state: ShellState, opts: VisibilityOptions, nowMs: number = Date.now()) =>
  selectVisibleThreads(state.threads.values(), nowMs, opts).map((t) => {
    const parentId = t.lineage.relationshipToParent === "subagent" ? t.lineage.parentThreadId : null;
    const parentTitle = parentId === null ? null : (state.threads.get(parentId)?.title ?? null);
    return presentThread(env, t, state.projectTitle(t.projectId), parentTitle);
  });

/**
 * Core: one shell subscription per environment → NpcStore → broadcast diffs to every mod.
 * `broadcast` is wired to the mod socket by main.ts.
 */
export class Bridge {
  readonly store = new NpcStore();
  private readonly statuses = new Map<string, EnvironmentStatus>();
  private readonly synced = new Set<string>();
  /** Latest shell state per environment: the filter's source, never trimmed by it. */
  private readonly shells = new Map<string, { env: EnvironmentConfig; state: ShellState }>();
  private broadcastFn: (msg: BridgeToMod) => void = () => {};
  private lastPeek = "";

  private readonly config: BridgeConfig;
  private readonly log: (msg: string) => void;

  constructor(config: BridgeConfig, log: (msg: string) => void = console.log) {
    this.config = config;
    this.log = log;
    for (const e of config.environments) this.statuses.set(e.id, { id: e.id, label: e.label, status: "connecting" });
  }

  setBroadcast(fn: (msg: BridgeToMod) => void): void {
    this.broadcastFn = fn;
  }

  /** Frames for a freshly connected mod. */
  initialFrames(): BridgeToMod[] {
    return [
      { v: PROTOCOL_VERSION, type: "snapshot", npcs: this.store.all() },
      { v: PROTOCOL_VERSION, type: "bridge.status", environments: [...this.statuses.values()] },
      { v: PROTOCOL_VERSION, type: "hud.peek", ...peekFor(this.store.all()) },
    ];
  }

  /** Broadcasts `hud.peek` only when its content changed. */
  private updatePeek(): void {
    const peek = peekFor(this.store.all());
    const key = JSON.stringify(peek);
    if (key === this.lastPeek) return;
    this.lastPeek = key;
    this.broadcastFn({ v: PROTOCOL_VERSION, type: "hud.peek", ...peek });
  }

  private setStatus(env: EnvironmentConfig, status: EnvironmentStatus["status"], detail?: string): void {
    const prev = this.statuses.get(env.id);
    const next: EnvironmentStatus = { id: env.id, label: env.label, status, ...(detail ? { detail } : {}) };
    if (prev?.status === next.status && prev.detail === next.detail) return;
    this.statuses.set(env.id, next);
    this.log(`[${env.id}] ${status}${detail ? `: ${detail}` : ""}`);
    this.broadcastFn({ v: PROTOCOL_VERSION, type: "bridge.status", environments: [...this.statuses.values()] });
  }

  /** Applies a new shell state for `env`, broadcasting upserts/removes and notifications. */
  applyShell(env: EnvironmentConfig, state: ShellState, nowMs: number = Date.now()): void {
    this.shells.set(env.id, { env, state });
    const views = viewsFor(env, state, visibilityOptions(this.config), nowMs);
    const { changes, previous } = this.store.replaceEnvironment(env.id, views);
    // No toasts for the first snapshot after start/reconnect: only real transitions.
    const notify = this.synced.has(env.id);
    for (const c of changes) {
      if (c.kind === "remove") {
        this.broadcastFn({ v: PROTOCOL_VERSION, type: "npc.remove", npcId: c.npcId });
        continue;
      }
      this.broadcastFn({ v: PROTOCOL_VERSION, type: "npc.upsert", npc: c.npc });
      const tr = notify ? transitionFor(previous.get(c.npc.id), c.npc) : null;
      if (tr) {
        this.broadcastFn({ v: PROTOCOL_VERSION, type: "npc.transition", ...tr });
        // Older mods only know `notify`.
        const { level, title, text, color, npcId } = tr;
        this.broadcastFn({ v: PROTOCOL_VERSION, type: "notify", level, title, text, color, npcId });
      }
    }
    if (state.synchronized) this.synced.add(env.id);
    if (changes.length > 0) this.updatePeek();
  }

  /**
   * Re-runs the filter on every environment's stored state, so threads leave when their recent
   * window expires even if T3 sends nothing. Only real changes are broadcast.
   */
  reevaluate(nowMs: number = Date.now()): void {
    for (const { env, state } of this.shells.values()) this.applyShell(env, state, nowMs);
  }

  /** Runs every environment concurrently, forever. Tokens are read once per environment at start. */
  run(): Effect.Effect<void> {
    const loops = this.config.environments.map((env) =>
      Effect.suspend(() => {
        let token: string;
        try {
          token = readSecretFile(env.tokenFile, `token for ${env.id}`, this.log);
        } catch (e) {
          this.setStatus(env, "auth_failed", (e as Error).message);
          return Effect.void;
        }
        return runEnvironment(env, Redacted.make(token), {
          onStatus: (s, d) => {
            if (s !== "ok") this.synced.delete(env.id);
            this.setStatus(env, s, d);
          },
          onShell: (state) => this.applyShell(env, state),
        });
      }),
    );
    const ticker = Effect.forever(
      Effect.sleep(REEVALUATE_INTERVAL_MS).pipe(Effect.andThen(Effect.sync(() => this.reevaluate()))),
    );
    return Effect.all([...loops, ticker], { concurrency: "unbounded", discard: true });
  }
}
