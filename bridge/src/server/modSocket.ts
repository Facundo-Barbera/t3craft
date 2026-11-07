import type { IncomingMessage } from "node:http";
import { timingSafeEqual } from "node:crypto";
import { WebSocketServer, type WebSocket } from "ws";
import { isAllowedListenHost } from "../config.ts";
import { parseModFrame, PROTOCOL_VERSION, type BridgeToMod, type ModToBridge } from "../protocol.ts";

export interface ModSocketOptions {
  /** Tailscale IP or loopback only (validated again here). */
  readonly host: string;
  readonly port: number;
  /** Host-header names accepted besides `host` and loopback names. */
  readonly allowedHostnames?: ReadonlyArray<string>;
  /** If set, the mod's `hello.key` must equal it. */
  readonly clientKey?: string | null;
  readonly bridgeVersion: string;
  /** Called after a successful hello; return the frames to send (snapshot, status, ...). */
  readonly onReady: (send: (msg: BridgeToMod) => void) => void;
  /** A validated mod message (not hello). Return an error string to ack ok:false. */
  readonly onMessage: (msg: { type: ModToBridge["type"] } & Record<string, unknown>) => string | null;
  readonly log?: (msg: string) => void;
}

const LOOPBACK_NAMES = new Set(["localhost", "127.0.0.1", "[::1]", "::1"]);
const HELLO_TIMEOUT_MS = 5_000;

/** Hostname part of a Host header (handles `[v6]:port`). */
export const hostnameOf = (hostHeader: string | undefined): string | null => {
  if (!hostHeader) return null;
  const h = hostHeader.trim().toLowerCase();
  if (h.startsWith("[")) {
    const end = h.indexOf("]");
    return end < 0 ? null : h.slice(0, end + 1);
  }
  const colon = h.indexOf(":");
  return colon < 0 ? h : h.slice(0, colon);
};

/**
 * Upgrade-time policy: refuse anything with an `Origin` header (browsers; stops web pages from
 * driving the bridge) and any Host that is not loopback, the listen IP, or an allowed name
 * (DNS rebinding).
 */
export const isAcceptableUpgrade = (
  headers: IncomingMessage["headers"],
  listenHost: string,
  allowedHostnames: ReadonlyArray<string> = [],
): boolean => {
  if (headers.origin !== undefined) return false;
  const name = hostnameOf(headers.host);
  if (name === null) return false;
  const listen = listenHost.includes(":") ? `[${listenHost.toLowerCase()}]` : listenHost;
  return LOOPBACK_NAMES.has(name) || name === listen || allowedHostnames.includes(name);
};

const keyMatches = (expected: string, got: unknown): boolean => {
  if (typeof got !== "string") return false;
  const a = Buffer.from(expected);
  const b = Buffer.from(got);
  return a.length === b.length && timingSafeEqual(a, b);
};

export interface ModSocketServer {
  broadcast(msg: BridgeToMod): void;
  clientCount(): number;
  close(): Promise<void>;
  /** Actual bound port (useful with port 0 in tests). */
  port(): number;
}

export const startModSocket = (opts: ModSocketOptions): Promise<ModSocketServer> => {
  if (!isAllowedListenHost(opts.host)) {
    return Promise.reject(new Error(`refusing to listen on ${opts.host}: only Tailscale or loopback IPs`));
  }
  const log = opts.log ?? (() => {});
  const ready = new Set<WebSocket>();
  const wss = new WebSocketServer({
    host: opts.host,
    port: opts.port,
    maxPayload: 256 * 1024,
    verifyClient: (info: { req: IncomingMessage }, cb: (ok: boolean, code?: number, msg?: string) => void) => {
      const ok = isAcceptableUpgrade(info.req.headers, opts.host, opts.allowedHostnames);
      if (!ok) log(`refused upgrade from ${info.req.socket.remoteAddress} (origin/host policy)`);
      cb(ok, 403, "Forbidden");
    },
  });

  const sendTo = (ws: WebSocket, msg: BridgeToMod) => {
    if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(msg));
  };
  const ack = (ws: WebSocket, re: string | undefined, error: string | null) => {
    if (re === undefined) return;
    sendTo(ws, { v: PROTOCOL_VERSION, type: "ack", re, ok: error === null, ...(error === null ? {} : { error }) });
  };

  wss.on("connection", (ws, req) => {
    const peer = req.socket.remoteAddress ?? "?";
    const helloTimer = setTimeout(() => ws.close(4001, "hello timeout"), HELLO_TIMEOUT_MS);
    ws.on("close", () => {
      clearTimeout(helloTimer);
      ready.delete(ws);
    });
    ws.on("message", (data, isBinary) => {
      if (isBinary) {
        ws.close(1003, "text frames only");
        return;
      }
      const parsed = parseModFrame(data.toString());
      if (!parsed.ok) {
        ack(ws, parsed.id, parsed.error);
        return;
      }
      const { msg } = parsed;
      if (!ready.has(ws)) {
        if (msg.type !== "hello") {
          ws.close(4002, "hello first");
          return;
        }
        if (opts.clientKey && !keyMatches(opts.clientKey, msg.key)) {
          log(`refused mod ${peer}: bad or missing key`);
          ws.close(4003, "bad key");
          return;
        }
        clearTimeout(helloTimer);
        ready.add(ws);
        log(`mod connected from ${peer}`);
        ack(ws, msg.id, null);
        sendTo(ws, { v: PROTOCOL_VERSION, type: "hello", bridge: opts.bridgeVersion, protocol: PROTOCOL_VERSION });
        opts.onReady((m) => sendTo(ws, m));
        return;
      }
      let error: string | null;
      try {
        error = msg.type === "hello" ? "already said hello" : opts.onMessage(msg);
      } catch (e) {
        error = `internal error: ${(e as Error).message}`;
      }
      ack(ws, msg.id, error);
    });
  });

  return new Promise((resolve, reject) => {
    wss.once("error", reject);
    wss.once("listening", () => {
      wss.off("error", reject);
      wss.on("error", (e) => log(`mod socket error: ${e.message}`));
      resolve({
        broadcast: (msg) => {
          const text = JSON.stringify(msg);
          for (const ws of ready) if (ws.readyState === ws.OPEN) ws.send(text);
        },
        clientCount: () => ready.size,
        port: () => {
          const a = wss.address();
          return typeof a === "object" && a !== null ? a.port : opts.port;
        },
        close: () =>
          new Promise<void>((res) => {
            for (const ws of wss.clients) ws.terminate();
            wss.close(() => res());
          }),
      });
    });
  });
};
