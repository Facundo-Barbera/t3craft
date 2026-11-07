import { describe, expect, it } from "vitest";
import { notificationFor, NpcStore } from "../src/state/npcStore.ts";
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

describe("notificationFor", () => {
  it("warns on entering needs_attention", () => {
    const before = presentThread(vps, shell({ status: "running" }), null);
    const after = presentThread(vps, shell({ status: "waiting" }), null);
    expect(notificationFor(before, after)?.level).toBe("warn");
  });
  it("is silent for new NPCs and unchanged states", () => {
    const v = presentThread(vps, shell({ status: "waiting" }), null);
    expect(notificationFor(undefined, v)).toBeNull();
    expect(notificationFor(v, v)).toBeNull();
  });
  it("announces working → done", () => {
    const before = presentThread(vps, shell({ status: "running" }), null);
    const after = presentThread(vps, shell({ status: "completed" }), null);
    expect(notificationFor(before, after)?.title).toBe("Thread finished");
  });
});
