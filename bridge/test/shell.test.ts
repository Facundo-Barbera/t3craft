import * as Schema from "effect/Schema";
import * as Stream from "effect/Stream";
import { describe, expect, it } from "vitest";
import { Bridge } from "../src/bridge.ts";
import type { BridgeConfig } from "../src/config.ts";
import type { BridgeToMod } from "../src/protocol.ts";
import { OrchestrationV2ShellStreamItem } from "../src/t3/contracts.ts";
import { ORCHESTRATION_SHELL_METHOD, type T3RpcClient } from "../src/t3/rpc.ts";
import { ShellState, subscribeShell } from "../src/t3/shell.ts";
import { readFixture } from "./fixtures.ts";

const decodeItem = Schema.decodeUnknownSync(Schema.toCodecJson(OrchestrationV2ShellStreamItem));

const thread = (id: string, extra: Record<string, unknown> = {}) => ({
  ...readFixture("thread-base"),
  id,
  title: `Thread ${id}`,
  lineage: { parentThreadId: null, relationshipToParent: null, rootThreadId: id },
  ...extra,
});
const project = (extra: Record<string, unknown> = {}) => ({ ...readFixture("project-base"), ...extra });

/** Authoritative initial snapshot, as T3 sends it first (no resolvedRepositoryIdentityRoots). */
const authoritative = (threads: unknown[], sequence = 100) =>
  decodeItem({
    kind: "snapshot",
    snapshot: { schemaVersion: 2, snapshotSequence: sequence, threads, archivedThreads: [], projects: [project()] },
  });

/**
 * Enrichment refresh, as T3 sends right after the initial snapshot and whenever a repository
 * identity resolves: threads are always empty, only matching projects are listed.
 */
const enrichment = (sequence = 100) =>
  decodeItem({
    kind: "snapshot",
    snapshot: {
      schemaVersion: 2,
      snapshotSequence: sequence,
      threads: [],
      archivedThreads: [],
      projects: [
        project({
          repositoryIdentity: {
            canonicalKey: "github.com/synthetic/project-1",
            locator: { source: "git-remote", remoteName: "origin", remoteUrl: "https://github.com/synthetic/project-1.git" },
          },
        }),
      ],
    },
    resolvedRepositoryIdentityRoots: ["/srv/synthetic/project-1"],
  });

const synchronized = decodeItem({ kind: "synchronized" });

describe("subscribeShell input", () => {
  const capture = () => {
    const seen: unknown[] = [];
    const client = {
      [ORCHESTRATION_SHELL_METHOD]: (input: unknown) => {
        seen.push(input);
        return Stream.empty;
      },
    } as unknown as T3RpcClient;
    return { client, seen };
  };

  // Regression: without the marker T3 never sends `synchronized`, so probe timed out after 30 s
  // and the bridge stayed "connecting".
  it("always asks T3 for the synchronized completion marker", () => {
    const { client, seen } = capture();
    subscribeShell(client);
    expect(seen).toEqual([{ requestCompletionMarker: true }]);
  });

  it("keeps caller options and cannot turn the marker off", () => {
    const { client, seen } = capture();
    subscribeShell(client, { afterSequence: 5, requestCompletionMarker: false });
    expect(seen).toEqual([{ afterSequence: 5, requestCompletionMarker: true }]);
  });
});

describe("ShellState initial sync", () => {
  it("syncs from the real T3 order: authoritative snapshot, enrichment snapshot, synchronized", () => {
    const state = new ShellState();
    expect(state.apply(authoritative([thread("a"), thread("b"), thread("c")]))).toBe(true);
    state.apply(enrichment());
    state.apply(synchronized);
    expect(state.synchronized).toBe(true);
    expect([...state.threads.keys()].sort()).toEqual(["a", "b", "c"]);
    expect(state.sequence).toBe(100);
  });

  // Regression: the enrichment snapshot (threads: []) used to wipe every thread (547 → 0 on the VPS).
  it("an enrichment snapshot keeps threads and sequence and only updates project metadata", () => {
    const state = new ShellState();
    state.apply(authoritative([thread("a"), thread("b")], 200));
    state.apply(enrichment(999));
    expect(state.threads.size).toBe(2);
    expect(state.sequence).toBe(200);
    expect(state.projects.get("project-1")?.repositoryIdentity?.canonicalKey).toBe("github.com/synthetic/project-1");
  });

  it("an enrichment snapshot never adds unknown projects", () => {
    const state = new ShellState();
    state.apply(authoritative([thread("a")]));
    state.projects.clear();
    state.apply(enrichment());
    expect(state.projects.size).toBe(0);
    expect(state.threads.size).toBe(1);
  });

  it("a later authoritative snapshot still replaces everything (reconnect)", () => {
    const state = new ShellState();
    state.apply(authoritative([thread("a"), thread("b")]));
    state.apply(authoritative([thread("c")], 300));
    expect([...state.threads.keys()]).toEqual(["c"]);
    expect(state.sequence).toBe(300);
  });
});

describe("Bridge with a partial snapshot", () => {
  const config: BridgeConfig = {
    listen: { host: "127.0.0.1", port: 7878, allowedHostnames: [], clientKeyFile: null },
    includeSubagents: false,
    environments: [
      { id: "vps", label: "my-vps", url: new URL("https://vps.example.ts.net"), tokenFile: "/nonexistent/vps.token" },
    ],
  };

  it("does not remove NPCs when T3 sends an enrichment snapshot", () => {
    const sent: BridgeToMod[] = [];
    const bridge = new Bridge(config, () => {});
    bridge.setBroadcast((m) => sent.push(m));
    const env = config.environments[0]!;
    const state = new ShellState();

    state.apply(authoritative([thread("a"), thread("b")]));
    bridge.applyShell(env, state);
    expect(bridge.store.all().map((n) => n.id).sort()).toEqual(["vps:a", "vps:b"]);

    sent.length = 0;
    if (state.apply(enrichment())) bridge.applyShell(env, state);
    state.apply(synchronized);
    expect(sent.filter((m) => m.type === "npc.remove")).toEqual([]);
    expect(bridge.store.all()).toHaveLength(2);
  });
});
