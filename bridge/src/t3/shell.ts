import * as Cause from "effect/Cause";
import * as Effect from "effect/Effect";
import type * as Redacted from "effect/Redacted";
import * as Stream from "effect/Stream";
import type { EnvironmentConfig } from "../config.ts";
import type { EnvironmentLinkStatus } from "../protocol.ts";
import type {
  OrchestrationProjectShell,
  OrchestrationV2ShellStreamItem,
  OrchestrationV2SubscribeShellInput,
  OrchestrationV2ThreadShell,
} from "./contracts.ts";
import { issueWebSocketTicket, T3AuthError } from "./auth.ts";
import { connectT3, ORCHESTRATION_SHELL_METHOD, type T3RpcClient } from "./rpc.ts";

type ThreadShell = OrchestrationV2ThreadShell;
type ProjectShell = typeof OrchestrationProjectShell.Type;
type ShellItem = typeof OrchestrationV2ShellStreamItem.Type;

/**
 * Pure reducer over `orchestration.subscribeShell` items for one environment. Archived threads are
 * dropped (they never get an NPC). Returns whether anything changed.
 */
export class ShellState {
  readonly threads = new Map<string, ThreadShell>();
  readonly projects = new Map<string, ProjectShell>();
  sequence = -1;
  synchronized = false;

  apply(item: ShellItem): boolean {
    switch (item.kind) {
      case "snapshot": {
        if (item.resolvedRepositoryIdentityRoots !== undefined) {
          return this.applyEnrichment(item.snapshot.projects, item.resolvedRepositoryIdentityRoots);
        }
        this.threads.clear();
        this.projects.clear();
        for (const t of item.snapshot.threads) this.threads.set(t.id, t);
        for (const p of item.snapshot.projects) this.projects.set(p.id, p);
        this.sequence = item.snapshot.snapshotSequence;
        return true;
      }
      case "synchronized":
        this.synchronized = true;
        return false;
      case "thread.updated":
        this.sequence = Math.max(this.sequence, item.sequence);
        if (item.location === "active") this.threads.set(item.thread.id, item.thread);
        else this.threads.delete(item.thread.id);
        return true;
      case "thread.removed":
        this.sequence = Math.max(this.sequence, item.sequence);
        return this.threads.delete(item.threadId);
      case "project.updated":
        this.sequence = Math.max(this.sequence, item.sequence);
        this.projects.set(item.project.id, item.project);
        return true;
      case "project.removed":
        this.sequence = Math.max(this.sequence, item.sequence);
        return this.projects.delete(item.projectId);
    }
  }

  /**
   * Metadata-only enrichment refresh (a `snapshot` item carrying `resolvedRepositoryIdentityRoots`,
   * always with `threads: []`). T3 sends one right after the authoritative snapshot and whenever a
   * repository identity resolves. It never changes structure or sequence: it only updates the
   * repository identity of projects we already have (same rules as upstream
   * packages/client-runtime/src/state/shellReducer.ts `mergeShellSnapshotProjects`).
   */
  private applyEnrichment(projects: ReadonlyArray<ProjectShell>, resolvedRoots: ReadonlyArray<string>): boolean {
    const resolved = new Set(resolvedRoots);
    let changed = false;
    for (const candidate of projects) {
      const current = this.projects.get(candidate.id);
      if (current === undefined || current.workspaceRoot !== candidate.workspaceRoot) continue;
      const accept = resolved.has(current.workspaceRoot)
        ? JSON.stringify(current.repositoryIdentity ?? null) !== JSON.stringify(candidate.repositoryIdentity ?? null)
        : current.repositoryIdentity == null && candidate.repositoryIdentity != null;
      if (accept) {
        this.projects.set(current.id, { ...current, repositoryIdentity: candidate.repositoryIdentity ?? null });
        changed = true;
      }
    }
    return changed;
  }

  projectTitle(projectId: string): string | null {
    return this.projects.get(projectId)?.title ?? null;
  }
}

/**
 * `orchestration.subscribeShell` as a Stream. Full snapshot first (no compact-id options).
 * Always requests the completion marker: T3 only emits `{kind:"synchronized"}` after the initial
 * snapshot when `requestCompletionMarker: true` (the official client does the same), and both
 * `probe` and the bridge's "ok" status wait for it.
 */
export const subscribeShell = (client: T3RpcClient, input: OrchestrationV2SubscribeShellInput = {}) =>
  client[ORCHESTRATION_SHELL_METHOD]({ ...input, requestCompletionMarker: true });

/** Strips anything that could carry a ticket out of error text. */
export const sanitize = (text: string): string =>
  text.replace(/wsTicket=[^&\s"'<>]+/g, "wsTicket=<redacted>").replace(/Bearer\s+\S+/gi, "Bearer <redacted>");

export interface EnvironmentListener {
  onStatus(status: EnvironmentLinkStatus, detail?: string): void;
  /** Called after every change with the environment's current state. */
  onShell(state: ShellState): void;
  /** The live RPC client while connected, `null` when it goes away (used for thread subscriptions). */
  onClient?(client: T3RpcClient | null): void;
}

const MIN_BACKOFF_MS = 1_000;
const MAX_BACKOFF_MS = 30_000;
const AUTH_BACKOFF_MS = 5 * 60_000;

const classify = (cause: Cause.Cause<unknown>): { status: EnvironmentLinkStatus; detail: string } => {
  const err = Cause.squash(cause);
  if (err instanceof T3AuthError) {
    if (err.reason === "unauthorized" || err.reason === "forbidden") return { status: "auth_failed", detail: err.detail };
    return { status: "offline", detail: err.detail };
  }
  const text = sanitize(err instanceof Error ? `${err.name}: ${err.message}` : String(err));
  if (/426|orchestration_protocol_incompatible/.test(text)) {
    return { status: "incompatible", detail: "T3 server speaks another orchestration protocol; update vendor/t3-contracts" };
  }
  return { status: "offline", detail: text.slice(0, 300) };
};

/**
 * Keeps one `subscribeShell` alive for one environment forever: fresh ticket per attempt
 * (tickets are single-use), exponential backoff with jitter, longer wait after auth failures.
 * Each reconnect asks for a full snapshot, so the state is simply replaced.
 * TODO: resume with `afterSequence` once delta replay is worth it.
 */
export const runEnvironment = (
  env: EnvironmentConfig,
  token: Redacted.Redacted<string>,
  listener: EnvironmentListener,
): Effect.Effect<never> =>
  Effect.gen(function* () {
    let attempt = 0;
    while (true) {
      listener.onStatus("connecting");
      const state = new ShellState();
      const exit = yield* Effect.exit(
        Effect.scoped(
          Effect.gen(function* () {
            const ticket = yield* issueWebSocketTicket(env.url, token);
            const client = yield* connectT3(env.url, ticket);
            listener.onClient?.(client);
            yield* Effect.addFinalizer(() => Effect.sync(() => listener.onClient?.(null)));
            yield* subscribeShell(client).pipe(
              Stream.runForEach((item) =>
                Effect.sync(() => {
                  const changed = state.apply(item);
                  if (item.kind === "synchronized" || (item.kind === "snapshot" && attempt > 0)) {
                    attempt = 0;
                    listener.onStatus("ok");
                  }
                  if (changed) listener.onShell(state);
                }),
              ),
            );
          }),
        ),
      );
      const { status, detail } =
        exit._tag === "Failure" ? classify(exit.cause) : { status: "offline" as const, detail: "shell stream ended" };
      listener.onStatus(status, detail);
      const cap =
        status === "auth_failed" ? AUTH_BACKOFF_MS : Math.min(MAX_BACKOFF_MS, MIN_BACKOFF_MS * 2 ** Math.min(attempt, 10));
      attempt++;
      yield* Effect.sleep(cap / 2 + Math.random() * (cap / 2));
    }
  });

