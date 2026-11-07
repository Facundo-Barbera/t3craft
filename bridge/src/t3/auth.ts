import * as Data from "effect/Data";
import * as DateTime from "effect/DateTime";
import * as Effect from "effect/Effect";
import * as Redacted from "effect/Redacted";
import * as Schema from "effect/Schema";
import { AuthWebSocketTicketResult } from "./contracts.ts";

/** A short-lived, single-use ticket for the `/ws` RPC route. Re-issue on every (re)connect. */
export interface WebSocketTicket {
  readonly ticket: Redacted.Redacted<string>;
  readonly expiresAt: DateTime.Utc;
}

export class T3AuthError extends Data.TaggedError("T3AuthError")<{
  readonly reason: "unauthorized" | "forbidden" | "http" | "network" | "decode";
  readonly status?: number;
  readonly detail: string;
}> {}

/** JSON codec: ISO string → DateTime.Utc, plain string → Redacted. */
const TicketJson = Schema.toCodecJson(AuthWebSocketTicketResult);

export const ticketUrl = (baseUrl: URL): URL => new URL("/api/auth/websocket-ticket/rpc", baseUrl);

/**
 * `POST {T3_URL}/api/auth/websocket-ticket/rpc` with `Authorization: Bearer <token>` →
 * `{ ticket, expiresAt }` (contracts `AuthWebSocketTicketResult`). Neither token nor ticket is
 * ever included in errors or logs.
 */
export const issueWebSocketTicket = (
  baseUrl: URL,
  token: Redacted.Redacted<string>,
  fetchImpl: typeof fetch = fetch,
): Effect.Effect<WebSocketTicket, T3AuthError> =>
  Effect.gen(function* () {
    const res = yield* Effect.tryPromise({
      try: (signal) =>
        fetchImpl(ticketUrl(baseUrl), {
          method: "POST",
          headers: { authorization: `Bearer ${Redacted.value(token)}`, accept: "application/json" },
          signal,
        }),
      catch: (e) => new T3AuthError({ reason: "network", detail: String((e as Error)?.message ?? e) }),
    });
    if (res.status === 401 || res.status === 403) {
      return yield* new T3AuthError({
        reason: res.status === 401 ? "unauthorized" : "forbidden",
        status: res.status,
        detail: `T3 rejected the token (${res.status}). Re-issue it with \`t3 auth session issue\`.`,
      });
    }
    if (!res.ok) {
      return yield* new T3AuthError({ reason: "http", status: res.status, detail: `HTTP ${res.status}` });
    }
    const body = yield* Effect.tryPromise({
      try: () => res.json() as Promise<unknown>,
      catch: () => new T3AuthError({ reason: "decode", detail: "ticket response is not JSON" }),
    });
    return yield* Schema.decodeUnknownEffect(TicketJson)(body).pipe(
      Effect.mapError(() => new T3AuthError({ reason: "decode", detail: "unexpected ticket response shape" })),
    );
  });
