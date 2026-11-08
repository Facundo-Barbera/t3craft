import type { OrchestrationV2ThreadShell } from "../t3/contracts.ts";
import type { NpcView } from "../protocol.ts";
import { isDelegatedThread, mapThreadState, type ThreadState } from "./mapper.ts";

/**
 * Everything visual lives here (colours, glyphs, nameplate text, priority) so it can change by
 * restarting the bridge, never Minecraft. The mod draws `label` in `color` verbatim.
 */
export interface StateStyle {
  readonly color: string;
  readonly glyph: string;
  /** Particle over the head (the mod maps the key to a vanilla particle). */
  readonly particle: NpcView["particle"];
  /** Lower sorts first (the mod lays NPCs out in this order). */
  readonly priority: number;
  readonly word: string;
}

export const STATE_STYLES: Readonly<Record<ThreadState, StateStyle>> = {
  needs_attention: { color: "#FFAA00", glyph: "!", particle: "attention", priority: 0, word: "needs you" },
  error: { color: "#FF5555", glyph: "✖", particle: "error", priority: 1, word: "error" },
  working: { color: "#55FFFF", glyph: "⚙", particle: "working", priority: 2, word: "working" },
  done: { color: "#55FF55", glyph: "✔", particle: null, priority: 3, word: "done" },
  idle: { color: "#AAAAAA", glyph: "·", particle: null, priority: 4, word: "idle" },
};

export interface EnvironmentInfo {
  readonly id: string;
  readonly label: string;
}

const MAX_TITLE = 32;
const MAX_ACTIVITY = 80;

const clip = (s: string, max: number): string => {
  const one = s.replace(/\s+/g, " ").trim();
  return one.length > max ? `${one.slice(0, max - 1)}…` : one;
};

/** Neighbourhood id: one per (environment, project). */
export const groupId = (envId: string, projectId: string): string => `${envId}:${projectId}`;

/**
 * Skin seed: the environment picks the "family" and the state the variant, so threads of one
 * machine look alike and a state change is visible from afar. The mod turns it into a skin.
 */
export const skinSeed = (envId: string, state: ThreadState): string => `${envId}/${state}`;

/** NPC id: unique across environments (`<envId>:<threadId>`). */
export const npcId = (envId: string, threadId: string): string => `${envId}:${threadId}`;

const activityFor = (t: OrchestrationV2ThreadShell, state: ThreadState): string | null => {
  const pending = t.pendingRuntimeRequest;
  if (state === "needs_attention" && pending !== null) {
    return pending.kind === "user_input" ? "Waiting for your answer" : `Approval needed: ${pending.kind}`;
  }
  if (state === "needs_attention" && t.hasActionableProposedPlan) return "Plan ready for review";
  if (state === "error" && t.lastError) return clip(t.lastError, MAX_ACTIVITY);
  const msg = t.latestVisibleMessage;
  return msg ? clip(msg.text, MAX_ACTIVITY) : null;
};

/**
 * `parentTitle` (feat/thread-lineage): title of the parent of a delegated task; the second
 * nameplate line then reads `↳ <parent>` instead of `env · project`.
 */
export const presentThread = (
  env: EnvironmentInfo,
  t: OrchestrationV2ThreadShell,
  projectTitle: string | null,
  parentTitle: string | null = null,
): NpcView => {
  const state = mapThreadState(t);
  const style = STATE_STYLES[state];
  const title = clip(t.title || "(untitled)", MAX_TITLE);
  const delegated = isDelegatedThread(t);
  const parentId = t.lineage.parentThreadId;
  const sublabel =
    delegated && parentId !== null
      ? `↳ ${clip(parentTitle || "(untitled)", MAX_TITLE)}`
      : `${env.label}${projectTitle ? ` · ${projectTitle}` : ""}`;
  return {
    id: npcId(env.id, t.id),
    env: env.id,
    envLabel: env.label,
    threadId: t.id,
    title,
    project: projectTitle,
    state,
    color: style.color,
    glyph: style.glyph,
    label: `${style.glyph} ${title}`,
    sublabel,
    activity: activityFor(t, state),
    priority: style.priority,
    settled: t.settledAt !== null,
    groupId: groupId(env.id, t.projectId),
    groupLabel: clip(projectTitle ?? "(no project)", MAX_TITLE),
    groupSublabel: env.label,
    skin: skinSeed(env.id, state),
    particle: style.particle,
    ...(parentId !== null && t.lineage.relationshipToParent === "subagent"
      ? { parentThreadId: parentId, delegated }
      : {}),
  };
};
