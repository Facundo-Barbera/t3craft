/**
 * t3craft protocol v1 (mod ⇄ bridge). Source of truth; protocol/protocol.md documents it.
 * Envelope: `{ v: 1, type, id? }` + payload fields at top level. Every mod→bridge message with an
 * `id` is answered with `ack { re: id, ok, error? }`.
 *
 * The mod is a dumb view: every visual decision (colour, glyph, label text, ordering) is resolved
 * here and sent ready to draw.
 */
import type { ThreadState } from "./state/mapper.ts";

export const PROTOCOL_VERSION = 1 as const;

export interface Envelope<T extends string> {
  readonly v: typeof PROTOCOL_VERSION;
  readonly type: T;
  readonly id?: string;
}

/** One NPC (= one T3 thread in one environment), fully resolved for display. */
export interface NpcView {
  /** `<env>:<threadId>`, unique across environments. */
  readonly id: string;
  readonly env: string;
  readonly envLabel: string;
  readonly threadId: string;
  readonly title: string;
  readonly project: string | null;
  readonly state: ThreadState;
  /** `#RRGGBB`. */
  readonly color: string;
  readonly glyph: string;
  /** Main nameplate line, drawn in `color`. */
  readonly label: string;
  /** Second line (environment · project), drawn grey. */
  readonly sublabel: string;
  readonly activity: string | null;
  /** Lower first. */
  readonly priority: number;
  readonly settled: boolean;
}

export type EnvironmentLinkStatus = "connecting" | "ok" | "auth_failed" | "offline" | "incompatible";

export interface EnvironmentStatus {
  readonly id: string;
  readonly label: string;
  readonly status: EnvironmentLinkStatus;
  readonly detail?: string;
}

// ---- bridge → mod -------------------------------------------------------------------------

export interface BridgeHello extends Envelope<"hello"> {
  readonly bridge: string;
  readonly protocol: typeof PROTOCOL_VERSION;
}
export interface Snapshot extends Envelope<"snapshot"> {
  readonly npcs: ReadonlyArray<NpcView>;
}
export interface NpcUpsert extends Envelope<"npc.upsert"> {
  readonly npc: NpcView;
}
export interface NpcRemove extends Envelope<"npc.remove"> {
  /** NpcView.id */
  readonly npcId: string;
}
export interface Notify extends Envelope<"notify"> {
  readonly level: "info" | "warn" | "error";
  readonly title: string;
  readonly text: string;
  /** `#RRGGBB` accent for the toast title. */
  readonly color: string;
  readonly npcId?: string;
}
export interface BridgeStatus extends Envelope<"bridge.status"> {
  readonly environments: ReadonlyArray<EnvironmentStatus>;
}
export interface Ack extends Envelope<"ack"> {
  readonly re: string;
  readonly ok: boolean;
  readonly error?: string;
}

// Stubs (phase 2/3). TODO: fill payloads when implemented.
export interface ChatHistory extends Envelope<"chat.history"> {
  readonly npcId: string;
  readonly messages: ReadonlyArray<{ readonly role: string; readonly text: string }>;
}
export interface ChatAppend extends Envelope<"chat.append"> {
  readonly npcId: string;
  readonly message: { readonly role: string; readonly text: string };
}
export interface PermissionOpen extends Envelope<"permission.open"> {
  readonly npcId: string;
  readonly requestId: string;
  readonly kind: string;
  readonly summary: string;
  readonly options: ReadonlyArray<{ readonly decision: string; readonly label: string; readonly warning?: string }>;
}
export interface PermissionClosed extends Envelope<"permission.closed"> {
  readonly npcId: string;
  readonly requestId: string;
}

export type BridgeToMod =
  | BridgeHello
  | Snapshot
  | NpcUpsert
  | NpcRemove
  | Notify
  | BridgeStatus
  | Ack
  | ChatHistory
  | ChatAppend
  | PermissionOpen
  | PermissionClosed;

// ---- mod → bridge -------------------------------------------------------------------------

export interface ModHello extends Envelope<"hello"> {
  readonly client: string;
  readonly version: string;
  readonly protocol: number;
  /** Optional bridge access key (NOT a T3 credential); required when the bridge sets `listen.clientKeyFile`. */
  readonly key?: string;
}
export interface ChatOpen extends Envelope<"chat.open"> {
  readonly npcId: string;
}
export interface ChatClose extends Envelope<"chat.close"> {
  readonly npcId: string;
}
export interface ChatSend extends Envelope<"chat.send"> {
  readonly npcId: string;
  readonly text: string;
}
export interface RunInterrupt extends Envelope<"run.interrupt"> {
  readonly npcId: string;
}
export interface PermissionRespond extends Envelope<"permission.respond"> {
  readonly npcId: string;
  readonly requestId: string;
  readonly decision: "accept" | "acceptForSession" | "acceptAlways" | "decline" | "cancel";
  readonly feedback?: string;
}
export interface ThreadCreate extends Envelope<"thread.create"> {
  readonly env: string;
  readonly projectId: string;
  readonly title: string;
  readonly text: string;
}

export type ModToBridge =
  | ModHello
  | ChatOpen
  | ChatClose
  | ChatSend
  | RunInterrupt
  | PermissionRespond
  | ThreadCreate;

export const MOD_MESSAGE_TYPES: ReadonlySet<ModToBridge["type"]> = new Set([
  "hello",
  "chat.open",
  "chat.close",
  "chat.send",
  "run.interrupt",
  "permission.respond",
  "thread.create",
]);

/** Loose envelope check for incoming mod frames; payload validation happens per handler. */
export const parseModFrame = (
  raw: string,
): { ok: true; msg: { type: ModToBridge["type"]; id?: string } & Record<string, unknown> } | { ok: false; error: string; id?: string } => {
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return { ok: false, error: "invalid JSON" };
  }
  if (typeof data !== "object" || data === null || Array.isArray(data)) return { ok: false, error: "not an object" };
  const o = data as Record<string, unknown>;
  const id = typeof o.id === "string" ? o.id : undefined;
  const withId = id === undefined ? {} : { id };
  if (o.v !== PROTOCOL_VERSION) return { ok: false, error: "unsupported protocol version", ...withId };
  if (typeof o.type !== "string" || !MOD_MESSAGE_TYPES.has(o.type as ModToBridge["type"])) {
    return { ok: false, error: `unknown type ${String(o.type)}`, ...withId };
  }
  return { ok: true, msg: o as { type: ModToBridge["type"]; id?: string } & Record<string, unknown> };
};
