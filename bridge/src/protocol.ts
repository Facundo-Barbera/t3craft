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
  /** Neighbourhood key (`<env>:<projectId>`); NPCs with the same key are placed together. */
  readonly groupId: string;
  /** Neighbourhood sign, line 1 (project title). */
  readonly groupLabel: string;
  /** Neighbourhood sign, line 2 (environment label). */
  readonly groupSublabel: string;
  /** Seed for the player skin; the mod maps it to a skin deterministically. */
  readonly skin: string;
  /** Particle shown over the head, or null: `working | attention | error | done`. */
  readonly particle: "working" | "attention" | "error" | "done" | null;
}

/** A chat line, resolved for display. */
export interface ChatMessageView {
  readonly id: string;
  readonly role: "user" | "assistant" | "system";
  /** Who to show as the author ("You", "Agent", "System"). */
  readonly author: string;
  /** `#RRGGBB` for the author. */
  readonly color: string;
  /** Raw message text (basic markdown); clipped by the bridge. */
  readonly text: string;
  /** ISO 8601. */
  readonly createdAt: string;
  readonly streaming: boolean;
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
/** A visible NPC changed state in a way worth telling the player. Never sent for initial state. */
export interface NpcTransition extends Envelope<"npc.transition"> {
  readonly npcId: string;
  readonly from: ThreadState;
  readonly to: ThreadState;
  readonly level: "info" | "warn" | "error";
  readonly title: string;
  readonly text: string;
  readonly color: string;
  /** Sound cue key: `attention | done | error`. */
  readonly sound: "attention" | "done" | "error";
}

/** One-line HUD summary of the most relevant thread, or `text: null` to hide it. */
export interface HudPeek extends Envelope<"hud.peek"> {
  readonly text: string | null;
  readonly color: string;
  readonly npcId: string | null;
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

/** A page of history, oldest first. Reply to `chat.open` / `chat.history`; sent only to that mod. */
export interface ChatHistory extends Envelope<"chat.history"> {
  readonly npcId: string;
  readonly messages: ReadonlyArray<ChatMessageView>;
  /** Older messages exist; ask with `chat.history { before: cursor }`. */
  readonly hasMore: boolean;
  /** Id of the oldest message in this page, or null when empty. */
  readonly cursor: string | null;
  /** True when this page ends at the newest message (the mod scrolls to bottom). */
  readonly latest: boolean;
}
/** Live upsert by `message.id` (new message or streaming update) to mods with that chat open. */
export interface ChatMessage extends Envelope<"chat.message"> {
  readonly npcId: string;
  readonly message: ChatMessageView;
}
/** Chat could not be loaded or stopped updating (environment offline, thread gone). */
export interface ChatStatus extends Envelope<"chat.status"> {
  readonly npcId: string;
  readonly status: "loading" | "live" | "offline" | "error";
  readonly detail?: string;
}

// Stubs (operate phase, see docs/plan-v2.md). TODO: fill payloads when implemented.
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
  | NpcTransition
  | HudPeek
  | BridgeStatus
  | Ack
  | ChatHistory
  | ChatMessage
  | ChatStatus
  | PermissionOpen
  | PermissionClosed;

/** Mod → bridge messages that need `orchestration:operate`; answered `read_only` until enabled. */
export const OPERATE_MESSAGE_TYPES: ReadonlySet<string> = new Set([
  "chat.send",
  "run.interrupt",
  "permission.respond",
  "thread.create",
]);

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
/** Older page: messages strictly before `before` (a message id from a previous page). */
export interface ChatHistoryRequest extends Envelope<"chat.history"> {
  readonly npcId: string;
  readonly before?: string;
  readonly limit?: number;
}
/** Re-read the bridge's `visibility` settings from bridge.json and re-evaluate (read-only op). */
export interface BridgeReload extends Envelope<"bridge.reload"> {}
/**
 * An image for `chat.send`, as T3's `UploadChatImageAttachment` (vendor/t3-contracts/src/chatAttachment.ts):
 * PNG/JPEG/GIF/WebP, at most 10 MiB, `dataUrl` = `data:<mimeType>;base64,...`. Validated by
 * `chatSendAttachmentError` (state/attachments.ts).
 */
export interface ChatSendImage {
  readonly type: "image";
  readonly name: string;
  readonly mimeType: string;
  readonly sizeBytes: number;
  readonly dataUrl: string;
}
export interface ChatSend extends Envelope<"chat.send"> {
  readonly npcId: string;
  readonly text: string;
  /** Optional images (feat/t3-ui-clean). The bridge is read-only: it validates them and refuses. */
  readonly attachments?: ReadonlyArray<ChatSendImage>;
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
  | ChatHistoryRequest
  | BridgeReload
  | ChatSend
  | RunInterrupt
  | PermissionRespond
  | ThreadCreate;

export const MOD_MESSAGE_TYPES: ReadonlySet<ModToBridge["type"]> = new Set([
  "hello",
  "chat.open",
  "chat.close",
  "chat.history",
  "bridge.reload",
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
