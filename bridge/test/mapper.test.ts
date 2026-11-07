import { describe, expect, it } from "vitest";
import { isVisibleThread, mapThreadState } from "../src/state/mapper.ts";
import { npcId, presentThread, STATE_STYLES } from "../src/state/presentation.ts";
import cases from "./fixtures/mapper-cases.json" with { type: "json" };
import { shell } from "./fixtures.ts";

describe("mapThreadState", () => {
  for (const c of cases) {
    it(`${c.name} → ${c.expected}`, () => {
      expect(mapThreadState(shell(c.overrides))).toBe(c.expected);
    });
  }
});

describe("isVisibleThread", () => {
  it("shows a normal thread", () => expect(isVisibleThread(shell(), false)).toBe(true));
  it("hides archived", () => expect(isVisibleThread(shell({ archivedAt: "2026-11-02T00:00:00.000Z" }), false)).toBe(false));
  it("hides deleted", () => expect(isVisibleThread(shell({ deletedAt: "2026-11-02T00:00:00.000Z" }), false)).toBe(false));
  const sub = { lineage: { parentThreadId: "p", relationshipToParent: "subagent", rootThreadId: "p" } };
  it("hides subagents by default", () => expect(isVisibleThread(shell(sub), false)).toBe(false));
  it("shows subagents when enabled", () => expect(isVisibleThread(shell(sub), true)).toBe(true));
  it("hides side chats", () =>
    expect(isVisibleThread(shell({ lineage: { parentThreadId: "p", relationshipToParent: null, rootThreadId: "p", sideChat: true } }), false)).toBe(false));
});

describe("presentThread", () => {
  const env = { id: "vps", label: "my-vps" };
  it("namespaces ids per environment", () => {
    const a = presentThread(env, shell(), null);
    const b = presentThread({ id: "acmesite", label: "my-acmesite" }, shell(), null);
    expect(a.id).toBe("vps:thread-base");
    expect(b.id).toBe("acmesite:thread-base");
    expect(a.id).not.toBe(b.id);
    expect(npcId("vps", "x")).toBe("vps:x");
  });
  it("resolves colour, label and environment tag in the bridge", () => {
    const v = presentThread(env, shell({ status: "waiting", title: "Fix login" }), "telar");
    expect(v.state).toBe("needs_attention");
    expect(v.color).toBe(STATE_STYLES.needs_attention.color);
    expect(v.label).toBe("! Fix login");
    expect(v.sublabel).toBe("my-vps · telar");
    expect(v.envLabel).toBe("my-vps");
  });
  it("describes a pending approval", () => {
    const v = presentThread(
      env,
      shell({ pendingRuntimeRequest: { id: "r", kind: "command", createdAt: "2026-11-01T10:05:00.000Z", blocking: true } }),
      null,
    );
    expect(v.activity).toBe("Approval needed: command");
  });
  it("clips long titles", () => {
    const v = presentThread(env, shell({ title: "x".repeat(100) }), null);
    expect(v.title.length).toBe(32);
    expect(v.title.endsWith("…")).toBe(true);
  });
});
