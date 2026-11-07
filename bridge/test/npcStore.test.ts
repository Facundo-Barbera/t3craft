import { describe, expect, it } from "vitest";
import { NpcStore, peekFor, transitionFor } from "../src/state/npcStore.ts";
import { presentThread } from "../src/state/presentation.ts";
import { shell } from "./fixtures.ts";

const vps = { id: "vps", label: "my-vps" };
const mini = { id: "acmesite", label: "my-acmesite" };

describe("NpcStore", () => {
  it("keeps environments separate and diffs per environment", () => {
    const store = new NpcStore();
    const a = presentThread(vps, shell(), null);
    const b = presentThread(mini, shell(), null);
    expect(store.replaceEnvironment("vps", [a]).changes).toHaveLength(1);
    expect(store.replaceEnvironment("acmesite", [b]).changes).toHaveLength(1);
    expect(store.all()).toHaveLength(2);
    // Same content again: no changes.
    expect(store.replaceEnvironment("vps", [a]).changes).toHaveLength(0);
    // Emptying one environment removes only its NPCs.
    expect(store.replaceEnvironment("vps", []).changes).toEqual([{ kind: "remove", npcId: "vps:thread-base" }]);
    expect(store.all().map((n) => n.id)).toEqual(["acmesite:thread-base"]);
  });
  it("sorts by priority first", () => {
    const store = new NpcStore();
    store.replaceEnvironment("vps", [
      presentThread(vps, shell({ id: "t-idle", title: "A" }), null),
      presentThread(vps, shell({ id: "t-wait", title: "Z", status: "waiting" }), null),
    ]);
    expect(store.all()[0]?.state).toBe("needs_attention");
  });
});

describe("transitionFor", () => {
  it("warns with the attention sound on entering needs_attention", () => {
    const before = presentThread(vps, shell({ status: "running" }), "telar");
    const after = presentThread(vps, shell({ status: "waiting" }), "telar");
    expect(transitionFor(before, after)).toMatchObject({ level: "warn", sound: "attention", from: "working", to: "needs_attention" });
  });
  it("is silent for new NPCs (initial seed) and unchanged states", () => {
    const v = presentThread(vps, shell({ status: "waiting" }), null);
    expect(transitionFor(undefined, v)).toBeNull();
    expect(transitionFor(v, v)).toBeNull();
  });
  it("announces working → done and errors", () => {
    const working = presentThread(vps, shell({ status: "running" }), null);
    expect(transitionFor(working, presentThread(vps, shell({ status: "completed" }), null))).toMatchObject({ title: "Thread finished", sound: "done" });
    expect(transitionFor(working, presentThread(vps, shell({ status: "failed" }), null))).toMatchObject({ level: "error", sound: "error" });
  });
  it("does not announce idle → done", () => {
    const idle = presentThread(vps, shell(), null);
    expect(transitionFor(idle, presentThread(vps, shell({ status: "completed" }), null))).toBeNull();
  });
});

describe("peekFor", () => {
  it("prefers needs_attention, then working, and hides otherwise", () => {
    const w = presentThread(vps, shell({ id: "w", title: "Build", status: "running" }), null);
    const a = presentThread(mini, shell({ id: "a", title: "Login", status: "waiting" }), null);
    const i = presentThread(vps, shell({ id: "i" }), null);
    expect(peekFor([a, w, i])).toMatchObject({ npcId: "acmesite:a", color: a.color });
    expect(peekFor([a, w, i]).text).toContain("Login [my-acmesite]");
    expect(peekFor([w, i])).toMatchObject({ npcId: "vps:w" });
    expect(peekFor([i])).toEqual({ text: null, color: expect.any(String), npcId: null });
  });
});
