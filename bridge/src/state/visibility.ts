import * as DateTime from "effect/DateTime";
import type { OrchestrationV2ThreadShell } from "../t3/contracts.ts";
import { isVisibleThread, mapThreadState } from "./mapper.ts";

export interface VisibilityOptions {
  /** A thread with activity inside this window stays visible even when idle/done. */
  readonly recentWindowMs: number;
  /** Max NPCs per environment, after ranking. */
  readonly maxPerEnvironment: number;
  readonly includeSubagents: boolean;
}

export const DEFAULT_RECENT_WINDOW_HOURS = 24;
export const DEFAULT_MAX_PER_ENVIRONMENT = 40;

type Shell = OrchestrationV2ThreadShell;

const ms = (d: DateTime.Utc | null | undefined): number | null => (d == null ? null : DateTime.toEpochMillis(d));

/**
 * Settled in T3: an explicit "settled" override, or a settle time not undone by an "active"
 * override.
 */
export const isSettled = (t: Pick<Shell, "settledAt" | "settledOverride">): boolean =>
  t.settledOverride === "settled" || (t.settledAt !== null && t.settledOverride !== "active");

/**
 * Last real activity: messages and runs (plus creation). `updatedAt` is deliberately not used,
 * so metadata-only touches (visits, title regeneration, pins) do not keep a thread visible.
 */
export const lastActivityMs = (t: Shell): number =>
  Math.max(
    ms(t.createdAt) ?? 0,
    ms(t.latestVisibleMessage?.updatedAt) ?? 0,
    ms(t.latestUserMessageAt) ?? 0,
    ms(t.latestRunRequestedAt) ?? 0,
    ms(t.latestRunStartedAt) ?? 0,
    ms(t.latestRunCompletedAt) ?? 0,
    ms(t.activityRunStartedAt) ?? 0,
  );

/** 0 = needs the user, 1 = working / turn in progress, 2 = recent only, null = hidden. */
export const visibilityTier = (t: Shell, nowMs: number, opts: VisibilityOptions): 0 | 1 | 2 | null => {
  if (!isVisibleThread(t, opts.includeSubagents)) return null; // archived, deleted, subagent
  if (isSettled(t)) return null;
  const state = mapThreadState(t);
  if (state === "needs_attention") return 0;
  if (state === "working" || t.activeRunId !== null) return 1;
  if (nowMs - lastActivityMs(t) <= opts.recentWindowMs) return 2;
  return null;
};

/**
 * The threads of ONE environment that get an NPC: never archived/deleted/settled; included when
 * they need the user, are working (or have a turn in progress), or had activity inside the
 * recent window. Ranked by tier, then most recent activity, capped at `maxPerEnvironment`.
 * Pure: the shell state itself is never modified, so a thread reappears as soon as it qualifies.
 */
export const selectVisibleThreads = (
  threads: Iterable<Shell>,
  nowMs: number,
  opts: VisibilityOptions,
): Shell[] => {
  const ranked: { t: Shell; tier: number; last: number }[] = [];
  for (const t of threads) {
    const tier = visibilityTier(t, nowMs, opts);
    if (tier !== null) ranked.push({ t, tier, last: lastActivityMs(t) });
  }
  ranked.sort((a, b) => a.tier - b.tier || b.last - a.last || a.t.id.localeCompare(b.t.id));
  return ranked.slice(0, Math.max(0, opts.maxPerEnvironment)).map((r) => r.t);
};
