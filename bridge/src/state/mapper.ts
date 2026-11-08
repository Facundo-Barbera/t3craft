import type { OrchestrationV2ThreadShell } from "../t3/contracts.ts";

/** What a thread NPC shows. The mod never derives this; it renders what the bridge sends. */
export type ThreadState = "working" | "needs_attention" | "done" | "error" | "idle";

/** The ThreadShell fields the mapper reads (decoded with the vendored T3 schema). */
export type ShellForMapping = Pick<
  OrchestrationV2ThreadShell,
  | "status"
  | "activityRunStatus"
  | "pendingRuntimeRequest"
  | "hasActionableProposedPlan"
  | "pendingGuestMessageCount"
  | "pendingBackgroundTasks"
>;

/** T3's LIVE_SHELL_STATUSES: `waiting` is a live run (tool, thread_wait, subagents, background work). */
const WORKING_STATUSES: ReadonlySet<string> = new Set(["queued", "preparing", "starting", "running", "waiting"]);

/**
 * ThreadShell → state, first match wins:
 * 1. needs_attention: a blocking `pendingRuntimeRequest` (`blocking !== false`, any kind: approval,
 *    `user_input`, `dynamic_tool_call`, `auth_refresh`), an actionable proposed plan, or guest messages
 *    waiting for the owner.
 * 2. working: `activityRunStatus` set, `status` in queued/preparing/starting/running/waiting, or
 *    pending background tasks.
 * 3. error: `status === "failed"`.
 * 4. done: `status === "completed"`.
 * 5. idle: everything else (`idle`, `interrupted`, `cancelled`, `rolled_back`).
 *
 * `waiting` alone is working, as in T3 Code: the run waits on a tool, a thread_wait, subagents or
 * background tasks, not on the user. Only the blocking request needs them (docs/plan.md §1.4).
 */
export const mapThreadState = (t: ShellForMapping): ThreadState => {
  const pending = t.pendingRuntimeRequest;
  if (pending !== null && pending.blocking !== false) return "needs_attention";
  if (t.hasActionableProposedPlan) return "needs_attention";
  if ((t.pendingGuestMessageCount ?? 0) > 0) return "needs_attention";

  if ((t.activityRunStatus ?? null) !== null) return "working";
  if (WORKING_STATUSES.has(t.status)) return "working";
  if ((t.pendingBackgroundTasks?.length ?? 0) > 0) return "working";

  if (t.status === "failed") return "error";
  if (t.status === "completed") return "done";
  return "idle";
};

/** Threads that never get an NPC: archived, deleted, and (unless enabled) subagents / side chats. */
export const isVisibleThread = (
  t: Pick<OrchestrationV2ThreadShell, "archivedAt" | "deletedAt" | "lineage">,
  includeSubagents: boolean,
): boolean => {
  if (t.archivedAt !== null || t.deletedAt !== null) return false;
  if (includeSubagents) return true;
  return t.lineage.relationshipToParent !== "subagent" && t.lineage.sideChat !== true;
};
