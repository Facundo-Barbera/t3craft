import { describe, expect, it } from "vitest";
import { Bridge } from "../src/bridge.ts";
import { parseConfig, parseVisibility, type BridgeConfig } from "../src/config.ts";
import type { BridgeToMod } from "../src/protocol.ts";
import { ShellState } from "../src/t3/shell.ts";
import { isSettled, selectVisibleThreads, type VisibilityOptions } from "../src/state/visibility.ts";
import example from "../config.example.json" with { type: "json" };
import { shell } from "./fixtures.ts";

const H = 3_600_000;
const NOW = Date.parse("2026-11-07T12:00:00.000Z");
const iso = (msAgo: number) => new Date(NOW - msAgo).toISOString();
const opts: VisibilityOptions = { recentWindowMs: 24 * H, maxPerEnvironment: 40, includeSubagents: false };

/** A synthetic thread whose last activity was `ago` ms before NOW. */
const t = (id: string, ago: number, extra: Record<string, unknown> = {}) =>
  shell({
    id,
    title: id,
    lineage: { parentThreadId: null, relationshipToParent: null, rootThreadId: id },
    createdAt: iso(ago),
    updatedAt: iso(ago),
    latestUserMessageAt: iso(ago),
    ...extra,
  });
const ids = (xs: { id: string }[]) => xs.map((x) => x.id);
const pending = (kind: string) => ({ id: `req-${kind}`, kind, createdAt: iso(48 * H), blocking: true });

describe("selectVisibleThreads: exclusions", () => {
  it("always hides archived threads, even when working", () => {
    expect(ids(selectVisibleThreads([t("a", H, { status: "running", archivedAt: iso(H) })], NOW, opts))).toEqual([]);
  });
  it("always hides settled threads, even recent or waiting", () => {
    const settled = { settledAt: iso(H), settledOverride: null };
    expect(ids(selectVisibleThreads([t("s1", H, settled), t("s2", H, { ...settled, status: "waiting" })], NOW, opts))).toEqual([]);
    expect(ids(selectVisibleThreads([t("s3", H, { settledAt: null, settledOverride: "settled" })], NOW, opts))).toEqual([]);
  });
  it("an 'active' override undoes settledAt", () => {
    expect(isSettled({ settledAt: null, settledOverride: "active" })).toBe(false);
    expect(ids(selectVisibleThreads([t("u", H, { settledAt: iso(2 * H), settledOverride: "active" })], NOW, opts))).toEqual(["u"]);
  });
  it("hides deleted threads and subagents", () => {
    const sub = { lineage: { parentThreadId: "p", relationshipToParent: "subagent", rootThreadId: "p" } };
    expect(ids(selectVisibleThreads([t("d", H, { deletedAt: iso(H) }), t("sub", H, { ...sub, status: "running" })], NOW, opts))).toEqual([]);
  });
});

describe("selectVisibleThreads: inclusions", () => {
  it("keeps an old thread that is working", () => {
    expect(ids(selectVisibleThreads([t("old-run", 30 * 24 * H, { status: "running" })], NOW, opts))).toEqual(["old-run"]);
  });
  it("keeps an old idle-looking thread with a turn in progress (activeRunId)", () => {
    expect(ids(selectVisibleThreads([t("turn", 72 * H, { activeRunId: "run-1", latestRunId: "run-1" })], NOW, opts))).toEqual(["turn"]);
  });
  it("keeps old threads waiting for approval or user input", () => {
    const xs = [
      t("approval", 72 * H, { status: "running", pendingRuntimeRequest: pending("command") }),
      t("input", 72 * H, { status: "running", pendingRuntimeRequest: pending("user_input") }),
      t("waiting", 72 * H, { status: "waiting" }),
    ];
    expect(ids(selectVisibleThreads(xs, NOW, opts)).sort()).toEqual(["approval", "input", "waiting"]);
  });
  it("keeps recent idle/done threads and drops older ones", () => {
    const xs = [t("recent-done", 23 * H, { status: "completed" }), t("recent-idle", 2 * H), t("stale-done", 25 * H, { status: "completed" })];
    expect(ids(selectVisibleThreads(xs, NOW, opts)).sort()).toEqual(["recent-done", "recent-idle"]);
  });
  it("uses message/run activity, not updatedAt metadata touches", () => {
    const touched = t("touched", 72 * H, { updatedAt: iso(H) });
    expect(ids(selectVisibleThreads([touched], NOW, opts))).toEqual([]);
    const ran = t("ran", 72 * H, { latestRunCompletedAt: iso(H), status: "completed" });
    expect(ids(selectVisibleThreads([ran], NOW, opts))).toEqual(["ran"]);
  });
});

describe("selectVisibleThreads: cap and priority", () => {
  it("caps per environment: attention first, then working, then most recent", () => {
    const xs = [
      t("recent-1h", H),
      t("recent-2h", 2 * H),
      t("recent-3h", 3 * H),
      t("working-old", 100 * H, { status: "running" }),
      t("attention-old", 200 * H, { status: "waiting" }),
    ];
    const capped = selectVisibleThreads(xs, NOW, { ...opts, maxPerEnvironment: 4 });
    expect(ids(capped)).toEqual(["attention-old", "working-old", "recent-1h", "recent-2h"]);
  });
  it("defaults to 40 per environment", () => {
    const many = Array.from({ length: 55 }, (_, i) => t(`r${String(i).padStart(2, "0")}`, (i + 1) * 60_000));
    const shown = selectVisibleThreads(many, NOW, opts);
    expect(shown).toHaveLength(40);
    expect(shown[0]?.id).toBe("r00");
    expect(shown[39]?.id).toBe("r39");
  });
});

describe("config: visibility block", () => {
  it("defaults to 24 h and 40 when the block is absent (installed configs keep working)", () => {
    const { visibility: _ignored, ...legacy } = example as Record<string, unknown>;
    expect(parseConfig(legacy).visibility).toEqual({ recentWindowHours: 24, maxPerEnvironment: 40 });
  });
  it("fills missing fields with defaults", () => {
    expect(parseVisibility({ maxPerEnvironment: 10 })).toEqual({ recentWindowHours: 24, maxPerEnvironment: 10 });
    expect(parseVisibility({ recentWindowHours: 6 })).toEqual({ recentWindowHours: 6, maxPerEnvironment: 40 });
  });
  it("rejects bad values", () => {
    expect(() => parseVisibility({ maxPerEnvironment: 0 })).toThrow(/maxPerEnvironment/);
    expect(() => parseVisibility({ maxPerEnvironment: 2.5 })).toThrow(/maxPerEnvironment/);
    expect(() => parseVisibility({ recentWindowHours: -1 })).toThrow(/recentWindowHours/);
    expect(() => parseVisibility({ recentWindowHours: "24" })).toThrow(/recentWindowHours/);
  });
});

describe("Bridge: filter on snapshots, updates and expiry", () => {
  const config: BridgeConfig = {
    listen: { host: "127.0.0.1", port: 7878, allowedHostnames: [], clientKeyFile: null },
    includeSubagents: false,
    visibility: { recentWindowHours: 24, maxPerEnvironment: 2 },
    environments: [
      { id: "vps", label: "my-vps", url: new URL("https://vps.example.ts.net"), tokenFile: "/nonexistent/vps.token" },
      { id: "mini", label: "my-acmesite", url: new URL("http://mini.example.ts.net:3773"), tokenFile: "/nonexistent/mini.token" },
    ],
  };
  const setup = () => {
    const sent: BridgeToMod[] = [];
    const bridge = new Bridge(config, () => {});
    bridge.setBroadcast((m) => sent.push(m));
    return { bridge, sent, vps: config.environments[0]!, mini: config.environments[1]! };
  };
  const removed = (sent: BridgeToMod[]) => sent.flatMap((m) => (m.type === "npc.remove" ? [m.npcId] : []));
  const upserted = (sent: BridgeToMod[]) => sent.flatMap((m) => (m.type === "npc.upsert" ? [m.npc.id] : []));

  it("filters the snapshot and applies the cap per environment, not globally", () => {
    const { bridge, vps, mini } = setup();
    const a = new ShellState();
    for (const x of [t("v1", H), t("v2", 2 * H), t("v3", 3 * H), t("v-old", 48 * H)]) a.threads.set(x.id, x);
    const b = new ShellState();
    for (const x of [t("m1", H), t("m2", 2 * H)]) b.threads.set(x.id, x);
    bridge.applyShell(vps, a, NOW);
    bridge.applyShell(mini, b, NOW);
    expect(bridge.store.all().map((n) => n.id).sort()).toEqual(["mini:m1", "mini:m2", "vps:v1", "vps:v2"]);
  });

  it("applies the filter to updates: archiving or settling removes, new activity adds", () => {
    const { bridge, sent, vps } = setup();
    const s = new ShellState();
    s.threads.set("x", t("x", H));
    s.threads.set("old", t("old", 48 * H));
    bridge.applyShell(vps, s, NOW);
    expect(bridge.store.all().map((n) => n.id)).toEqual(["vps:x"]);

    sent.length = 0;
    s.threads.set("x", t("x", H, { settledAt: iso(0) })); // thread.updated: settled
    s.threads.set("old", t("old", 48 * H, { status: "running" })); // thread.updated: old thread starts working
    bridge.applyShell(vps, s, NOW);
    expect(removed(sent)).toEqual(["vps:x"]);
    expect(upserted(sent)).toEqual(["vps:old"]);
  });

  it("a thread that drops out of the top is removed and comes back when it rises again", () => {
    const { bridge, sent, vps } = setup();
    const s = new ShellState();
    for (const x of [t("a", H), t("b", 2 * H)]) s.threads.set(x.id, x);
    bridge.applyShell(vps, s, NOW);
    sent.length = 0;
    s.threads.set("c", t("c", 0, { status: "waiting" })); // new needs-attention thread pushes "b" out
    bridge.applyShell(vps, s, NOW);
    expect(removed(sent)).toEqual(["vps:b"]);
    expect(s.threads.has("b")).toBe(true); // source state intact
    sent.length = 0;
    s.threads.delete("c");
    bridge.applyShell(vps, s, NOW);
    expect(upserted(sent)).toEqual(["vps:b"]);
  });

  it("reevaluate() expires threads past the window without T3 traffic, keeping the source", () => {
    const { bridge, sent, vps } = setup();
    const s = new ShellState();
    s.threads.set("fresh", t("fresh", 23 * H));
    s.threads.set("busy", t("busy", 23 * H, { status: "running" }));
    bridge.applyShell(vps, s, NOW);
    expect(bridge.store.all()).toHaveLength(2);

    sent.length = 0;
    bridge.reevaluate(NOW + 2 * H); // "fresh" is now 25 h old
    expect(removed(sent)).toEqual(["vps:fresh"]);
    expect(bridge.store.all().map((n) => n.id)).toEqual(["vps:busy"]);
    expect(s.threads.size).toBe(2);

    sent.length = 0;
    bridge.reevaluate(NOW + 2 * H); // nothing changed: no traffic to the mod
    expect(sent).toEqual([]);
  });
});
