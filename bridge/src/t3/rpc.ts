import * as Effect from "effect/Effect";
import * as Layer from "effect/Layer";
import * as Redacted from "effect/Redacted";
import * as Schema from "effect/Schema";
import type * as Scope from "effect/Scope";
import * as Rpc from "effect/rpc/Rpc";
import * as RpcClient from "effect/rpc/RpcClient";
import type { RpcClientError } from "effect/rpc/RpcClientError";
import * as RpcGroup from "effect/rpc/RpcGroup";
import * as RpcSerialization from "effect/rpc/RpcSerialization";
import * as Socket from "effect/socket/Socket";
import {
  EnvironmentAuthorizationError,
  ORCHESTRATION_PROTOCOL_QUERY_PARAM,
  ORCHESTRATION_PROTOCOL_VERSION,
  ORCHESTRATION_V2_WS_METHODS,
  OrchestrationV2GetShellSnapshotError,
  OrchestrationV2GetThreadProjectionError,
  OrchestrationV2RpcSchemas,
} from "./contracts.ts";
import type { WebSocketTicket } from "./auth.ts";

/**
 * The subset of T3's `WsRpcGroup` (vendor/t3-contracts/src/rpc.ts) the bridge calls. Tags,
 * payload, success and error schemas are the upstream ones, so the wire format is identical to the
 * official client's; the server's scope middleware runs server-side only.
 *
 * Read-only (`orchestration:read`): `orchestration.subscribeShell`, `orchestration.subscribeThread`.
 * TODO(operate phase, needs Facundo's OK): `orchestration.dispatchCommand` for `message.dispatch` /
 *   `run.interrupt` (scope `orchestration:operate`).
 * TODO(phase 3): `runtime-request.respond` via `dispatchCommand`.
 * TODO(phase 4): `thread.create` via `dispatchCommand`, or `orchestration.launchThread`.
 */
export const SubscribeShellRpc = Rpc.make(ORCHESTRATION_V2_WS_METHODS.subscribeShell, {
  payload: OrchestrationV2RpcSchemas.subscribeShell.input,
  success: OrchestrationV2RpcSchemas.subscribeShell.output,
  error: Schema.Union([OrchestrationV2GetShellSnapshotError, EnvironmentAuthorizationError]),
  stream: true,
});

export const SubscribeThreadRpc = Rpc.make(ORCHESTRATION_V2_WS_METHODS.subscribeThread, {
  payload: OrchestrationV2RpcSchemas.subscribeThread.input,
  success: OrchestrationV2RpcSchemas.subscribeThread.output,
  error: Schema.Union([OrchestrationV2GetThreadProjectionError, EnvironmentAuthorizationError]),
  stream: true,
});

export const ORCHESTRATION_SHELL_METHOD = ORCHESTRATION_V2_WS_METHODS.subscribeShell;
export const ORCHESTRATION_THREAD_METHOD = ORCHESTRATION_V2_WS_METHODS.subscribeThread;

export const T3BridgeRpcGroup = RpcGroup.make(SubscribeShellRpc, SubscribeThreadRpc);

export type T3RpcClient = RpcClient.FromGroup<typeof T3BridgeRpcGroup, RpcClientError>;

/** `https://host` → `wss://host/ws?orchestrationProtocol=2&wsTicket=…`. Contains a secret: never log it. */
export const socketUrl = (baseUrl: URL, ticket: WebSocketTicket): string => {
  const url = new URL("/ws", baseUrl);
  url.protocol = baseUrl.protocol === "http:" ? "ws:" : "wss:";
  url.searchParams.set(ORCHESTRATION_PROTOCOL_QUERY_PARAM, String(ORCHESTRATION_PROTOCOL_VERSION));
  url.searchParams.set("wsTicket", Redacted.value(ticket.ticket));
  return url.toString();
};

/** Same liveness numbers as the official client (packages/client-runtime/src/rpc/protocol.ts). */
const PING_TIMEOUT = "15 seconds";
const OPEN_TIMEOUT = "10 seconds";

/**
 * Opens one RPC session on `/ws` with a fresh ticket. The socket lives as long as the returned
 * scope. Effect RPC + `RpcSerialization.layerJson`, as the T3 server (apps/server/src/ws.ts).
 * No transport retries here: the caller re-issues a ticket and reconnects with backoff.
 */
export const connectT3 = (
  baseUrl: URL,
  ticket: WebSocketTicket,
): Effect.Effect<T3RpcClient, never, Scope.Scope> =>
  Effect.gen(function* () {
    const protocolLayer = RpcClient.layerProtocolSocket({
      pingTimeout: PING_TIMEOUT,
      retryTransientErrors: false,
    }).pipe(
      Layer.provide(
        Layer.mergeAll(
          Socket.layerWebSocket(socketUrl(baseUrl, ticket), { openTimeout: OPEN_TIMEOUT }).pipe(
            Layer.provide(Socket.layerWebSocketConstructorGlobal),
          ),
          RpcSerialization.layerJson,
        ),
      ),
    );
    const context = yield* Layer.build(protocolLayer);
    return yield* RpcClient.make(T3BridgeRpcGroup).pipe(Effect.provide(context));
  });
