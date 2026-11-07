import * as Effect from "effect/Effect";
import * as Redacted from "effect/Redacted";
import type { BridgeConfig, EnvironmentConfig } from "./config.ts";
import { readSecretFile } from "./config.ts";
import { PROTOCOL_VERSION, type BridgeToMod, type EnvironmentStatus } from "./protocol.ts";
import { isVisibleThread } from "./state/mapper.ts";
import { notificationFor, NpcStore } from "./state/npcStore.ts";
import { presentThread } from "./state/presentation.ts";
import { runEnvironment, type ShellState } from "./t3/shell.ts";

export const BRIDGE_VERSION = "0.1.0";

/** Turns one environment's shell state into NpcViews. */
export const viewsFor = (env: EnvironmentConfig, state: ShellState, includeSubagents: boolean) =>
  [...state.threads.values()]
    .filter((t) => isVisibleThread(t, includeSubagents))
    .map((t) => presentThread(env, t, state.projectTitle(t.projectId)));

/**
 * Core: one shell subscription per environment → NpcStore → broadcast diffs to every mod.
 * `broadcast` is wired to the mod socket by main.ts.
 */
export class Bridge {
  readonly store = new NpcStore();
  private readonly statuses = new Map<string, EnvironmentStatus>();
  private readonly synced = new Set<string>();
  private broadcastFn: (msg: BridgeToMod) => void = () => {};

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
    ];
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
  applyShell(env: EnvironmentConfig, state: ShellState): void {
    const { changes, previous } = this.store.replaceEnvironment(env.id, viewsFor(env, state, this.config.includeSubagents));
    // No toasts for the first snapshot after start/reconnect: only real transitions.
    const notify = this.synced.has(env.id);
    for (const c of changes) {
      if (c.kind === "remove") {
        this.broadcastFn({ v: PROTOCOL_VERSION, type: "npc.remove", npcId: c.npcId });
        continue;
      }
      this.broadcastFn({ v: PROTOCOL_VERSION, type: "npc.upsert", npc: c.npc });
      const n = notify ? notificationFor(previous.get(c.npc.id), c.npc) : null;
      if (n) this.broadcastFn({ v: PROTOCOL_VERSION, type: "notify", ...n });
    }
    if (state.synchronized) this.synced.add(env.id);
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
    return Effect.all(loops, { concurrency: "unbounded", discard: true });
  }
}
