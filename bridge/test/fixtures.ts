import { readFileSync } from "node:fs";
import * as Schema from "effect/Schema";
import { OrchestrationV2ThreadShell } from "../src/t3/contracts.ts";

const decode = Schema.decodeUnknownSync(Schema.toCodecJson(OrchestrationV2ThreadShell));

export const readFixture = (name: string): Record<string, unknown> =>
  JSON.parse(readFileSync(new URL(`./fixtures/${name}.json`, import.meta.url), "utf8")) as Record<string, unknown>;

/** Base fixture + overrides, decoded through the real (vendored) T3 schema, so fixtures stay realistic. */
export const shell = (overrides: Record<string, unknown> = {}, base = "thread-base") =>
  decode({ ...readFixture(base), ...overrides });
