import {
  PROVIDER_SEND_TURN_MAX_ATTACHMENTS,
  PROVIDER_SEND_TURN_MAX_IMAGE_BYTES,
  getProviderAttachmentLimitError,
  isProviderSendTurnSupportedImageMimeType,
} from "../t3/contracts.ts";

/** `UploadChatImageAttachment.dataUrl` limit (chatAttachment.ts PROVIDER_SEND_TURN_MAX_IMAGE_DATA_URL_CHARS). */
export const MAX_IMAGE_DATA_URL_CHARS = 14_000_000;
const NAME_MAX = 255;
const DATA_URL = /^data:([a-z0-9.+/-]+);base64,([A-Za-z0-9+/]*={0,2})$/i;

/** Bytes a base64 payload decodes to (without decoding it). */
export const base64Bytes = (b64: string): number => {
  const pad = b64.endsWith("==") ? 2 : b64.endsWith("=") ? 1 : 0;
  return Math.floor((b64.length * 3) / 4) - pad;
};

/**
 * Why a `chat.send` `attachments` value cannot go to T3, or null when it is absent or valid. Mirrors
 * T3's own checks: the contract's limits (`UploadChatImageAttachment`, `getProviderAttachmentLimitError`)
 * and the server's (`apps/server/src/ws.ts persistChatAttachments`: the data URL's type must equal
 * `mimeType` and its bytes `sizeBytes`). Messages are player-facing (shown in the composer).
 */
export const chatSendAttachmentError = (raw: unknown): string | null => {
  if (raw === undefined) return null;
  if (!Array.isArray(raw)) return "attachments must be an array";
  if (raw.length > PROVIDER_SEND_TURN_MAX_ATTACHMENTS) {
    return `too many attachments: up to ${PROVIDER_SEND_TURN_MAX_ATTACHMENTS} per message`;
  }
  const checked: { type: string; mimeType: string; sizeBytes: number }[] = [];
  for (const [i, a] of raw.entries()) {
    const where = `attachment ${i + 1}`;
    if (typeof a !== "object" || a === null) return `${where}: not an object`;
    const o = a as Record<string, unknown>;
    if (o.type !== "image") return `${where}: only images can be attached`;
    if (typeof o.name !== "string" || o.name.trim() === "" || o.name.length > NAME_MAX) return `${where}: name must be 1-${NAME_MAX} characters`;
    if (typeof o.mimeType !== "string" || !isProviderSendTurnSupportedImageMimeType(o.mimeType)) {
      return `${where} (${String(o.name)}): T3 takes PNG, JPEG, GIF or WebP`;
    }
    if (typeof o.sizeBytes !== "number" || !Number.isInteger(o.sizeBytes) || o.sizeBytes < 0) return `${where}: sizeBytes must be a whole number`;
    if (o.sizeBytes > PROVIDER_SEND_TURN_MAX_IMAGE_BYTES) return `${where} (${o.name}): images can be up to 10 MB`;
    if (typeof o.dataUrl !== "string" || o.dataUrl.length > MAX_IMAGE_DATA_URL_CHARS) return `${where} (${o.name}): image data too large`;
    const m = DATA_URL.exec(o.dataUrl);
    if (!m) return `${where} (${o.name}): dataUrl must be data:<type>;base64,...`;
    if (m[1]!.toLowerCase() !== o.mimeType.toLowerCase()) return `${where} (${o.name}): dataUrl type does not match mimeType`;
    if (base64Bytes(m[2]!) !== o.sizeBytes) return `${where} (${o.name}): sizeBytes does not match the data`;
    checked.push({ type: "image", mimeType: o.mimeType, sizeBytes: o.sizeBytes });
  }
  return getProviderAttachmentLimitError(checked) ?? null;
};

/**
 * The bridge's answer to `chat.send` while it is read-only (it holds an `orchestration:read` token
 * and never writes; see t3/rpc.ts). Invalid attachments are named first, so the mod can show what
 * is wrong with the image; valid ones still cannot be sent through here.
 */
export const chatSendReply = (msg: Record<string, unknown>): string => {
  const bad = chatSendAttachmentError(msg.attachments);
  if (bad !== null) return `invalid attachments: ${bad}`;
  return Array.isArray(msg.attachments) && msg.attachments.length > 0
    ? "not implemented: the bridge is read-only and cannot send messages or attach images; the mod sends them straight to T3 over a direct pairing (Enable control)"
    : "not implemented: the bridge is read-only; the mod sends messages straight to T3 over a direct pairing (Enable control)";
};
