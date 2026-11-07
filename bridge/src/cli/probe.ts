/**
 * `pnpm probe [--env <id>]`: for each configured environment, issue a ticket, open /ws, read
 * `orchestration.subscribeShell` until the first snapshot is synchronized, print every thread with
 * its mapped state, then exit. Read-only (needs only `orchestration:read`).
 */
import * as Effect from "effect/Effect";
import * as Redacted from "effect/Redacted";
import * as Stream from "effect/Stream";
import { defaultConfigPath, loadConfig, readSecretFile, type EnvironmentConfig } from "../config.ts";
import { viewsFor } from "../bridge.ts";
import { issueWebSocketTicket } from "../t3/auth.ts";
import { connectT3 } from "../t3/rpc.ts";
import { sanitize, ShellState, subscribeShell } from "../t3/shell.ts";

const args = process.argv.slice(2);
const only = args.includes("--env") ? args[args.indexOf("--env") + 1] : undefined;

const probe = (env: EnvironmentConfig, token: string, includeSubagents: boolean) =>
  Effect.scoped(
    Effect.gen(function* () {
      const ticket = yield* issueWebSocketTicket(env.url, Redacted.make(token));
      const client = yield* connectT3(env.url, ticket);
      const state = new ShellState();
      yield* subscribeShell(client).pipe(
        Stream.takeUntil((item) => {
          state.apply(item);
          return item.kind === "synchronized";
        }),
        Stream.runDrain,
      );
      return viewsFor(env, state, includeSubagents);
    }),
  ).pipe(Effect.timeout("30 seconds"));

const main = async () => {
  const config = loadConfig(defaultConfigPath());
  let failed = false;
  for (const env of config.environments) {
    if (only && env.id !== only) continue;
    console.log(`\n== ${env.label} (${env.id}) ${env.url.origin}`);
    let token: string;
    try {
      token = readSecretFile(env.tokenFile, `token for ${env.id}`);
    } catch (e) {
      console.log(`   skipped: ${(e as Error).message}`);
      failed = true;
      continue;
    }
    const exit = await Effect.runPromiseExit(probe(env, token, config.includeSubagents));
    if (exit._tag === "Failure") {
      console.log(`   failed: ${sanitize(String(exit.cause)).slice(0, 400)}`);
      failed = true;
      continue;
    }
    const views = exit.value.sort((a, b) => a.priority - b.priority || a.title.localeCompare(b.title));
    if (views.length === 0) console.log("   (no visible threads)");
    for (const v of views) {
      console.log(`   ${v.state.padEnd(15)} ${v.label}  [${v.sublabel}]${v.activity ? `  — ${v.activity}` : ""}`);
    }
  }
  process.exit(failed ? 1 : 0);
};

main().catch((e: unknown) => {
  console.error(e instanceof Error ? `${e.name}: ${e.message}` : e);
  process.exit(1);
});
