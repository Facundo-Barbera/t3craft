import { existsSync, readFileSync, statSync } from "node:fs";
import { isIP } from "node:net";
import { homedir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

/** Repo root (bridge/..). Token files are refused inside it so secrets never land in git. */
export const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");

export interface EnvironmentConfig {
  /** Short stable id, part of every NPC id (`<id>:<threadId>`). */
  readonly id: string;
  /** Shown on the NPC (which machine the thread lives on). */
  readonly label: string;
  /** Base URL of that T3 Code server (no credentials, no query). */
  readonly url: URL;
  /** Absolute path of a file holding that environment's own revocable session token. */
  readonly tokenFile: string;
}

export interface ListenConfig {
  /** IP literal: the host's Tailscale IP (100.64.0.0/10 or fd7a:115c:a1e0::/48) or loopback. */
  readonly host: string;
  readonly port: number;
  /** Extra Host-header names accepted besides `host` and loopback (e.g. the MagicDNS name). */
  readonly allowedHostnames: ReadonlyArray<string>;
  /** Optional shared key the mod must send in `hello` (bridge access only; not a T3 credential). */
  readonly clientKeyFile: string | null;
}

export interface BridgeConfig {
  readonly listen: ListenConfig;
  readonly includeSubagents: boolean;
  readonly environments: ReadonlyArray<EnvironmentConfig>;
}

export class ConfigError extends Error {
  override readonly name = "ConfigError";
}

/** `T3CRAFT_CONFIG`, else ~/.config/t3craft/bridge.json (the systemd unit points at /etc/t3craft/bridge.json). */
export const defaultConfigPath = (env: NodeJS.ProcessEnv = process.env): string =>
  env.T3CRAFT_CONFIG?.trim() ||
  path.join(env.XDG_CONFIG_HOME ?? path.join(homedir(), ".config"), "t3craft", "bridge.json");

const isInside = (child: string, parent: string): boolean => {
  const rel = path.relative(parent, child);
  return rel === "" || (!rel.startsWith("..") && !path.isAbsolute(rel));
};

// ---- listen address policy ----------------------------------------------------------------

const ipv4ToInt = (ip: string): number =>
  ip.split(".").reduce((acc, part) => (acc << 8) + Number(part), 0) >>> 0;

const expandIpv6 = (ip: string): string[] => {
  const [head = "", tail = ""] = ip.toLowerCase().split("::");
  const h = head ? head.split(":") : [];
  const t = tail ? tail.split(":") : [];
  const fill = ip.includes("::") ? Array<string>(8 - h.length - t.length).fill("0") : [];
  return [...h, ...fill, ...t].map((g) => g.padStart(4, "0"));
};

/** Loopback, Tailscale CGNAT (100.64.0.0/10) or Tailscale ULA (fd7a:115c:a1e0::/48). Never wildcard/public. */
export const isAllowedListenHost = (host: string): boolean => {
  const kind = isIP(host);
  if (kind === 4) {
    const n = ipv4ToInt(host);
    const inRange = (base: string, bits: number) =>
      (n & (bits === 0 ? 0 : (~0 << (32 - bits)) >>> 0)) >>> 0 === ipv4ToInt(base);
    return inRange("127.0.0.0", 8) || inRange("100.64.0.0", 10);
  }
  if (kind === 6) {
    const g = expandIpv6(host);
    if (g.join(":") === "0000:0000:0000:0000:0000:0000:0000:0001") return true;
    return g[0] === "fd7a" && g[1] === "115c" && g[2] === "a1e0";
  }
  return false;
};

// ---- parsing ------------------------------------------------------------------------------

const ENV_ID = /^[a-z0-9][a-z0-9-]{0,31}$/;

const obj = (v: unknown, where: string): Record<string, unknown> => {
  if (typeof v !== "object" || v === null || Array.isArray(v)) throw new ConfigError(`${where} must be an object`);
  return v as Record<string, unknown>;
};
const str = (v: unknown, where: string): string => {
  if (typeof v !== "string" || v.trim() === "") throw new ConfigError(`${where} must be a non-empty string`);
  return v.trim();
};

export const parseT3Url = (raw: string, where: string): URL => {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new ConfigError(`${where} is not a valid URL`);
  }
  if (url.username || url.password) throw new ConfigError(`${where} must not contain credentials`);
  if (url.search || url.hash) throw new ConfigError(`${where} must not contain a query or fragment`);
  if (url.protocol !== "https:" && url.protocol !== "http:") throw new ConfigError(`${where} must be http(s)`);
  return url;
};

const tokenPath = (raw: string, where: string): string => {
  if (!path.isAbsolute(raw)) throw new ConfigError(`${where} must be an absolute path`);
  const p = path.resolve(raw);
  if (isInside(p, REPO_ROOT)) throw new ConfigError(`${where} points inside the repo; keep tokens outside it`);
  return p;
};

export const parseConfig = (data: unknown): BridgeConfig => {
  const root = obj(data, "config");
  const listen = obj(root.listen, "listen");
  const host = str(listen.host, "listen.host");
  if (!isAllowedListenHost(host)) {
    throw new ConfigError(
      `listen.host ${host} is not allowed: use the host's Tailscale IP (100.64.0.0/10, fd7a:115c:a1e0::/48) or loopback. Wildcard and public addresses are refused.`,
    );
  }
  const port = listen.port ?? 7878;
  if (typeof port !== "number" || !Number.isInteger(port) || port < 1 || port > 65535) {
    throw new ConfigError("listen.port must be an integer 1..65535");
  }
  const allowedHostnames = (listen.allowedHostnames ?? []) as unknown;
  if (!Array.isArray(allowedHostnames) || allowedHostnames.some((h) => typeof h !== "string")) {
    throw new ConfigError("listen.allowedHostnames must be an array of strings");
  }
  const clientKeyFile =
    listen.clientKeyFile === undefined || listen.clientKeyFile === null
      ? null
      : tokenPath(str(listen.clientKeyFile, "listen.clientKeyFile"), "listen.clientKeyFile");

  if (!Array.isArray(root.environments) || root.environments.length === 0) {
    throw new ConfigError("environments must be a non-empty array");
  }
  const seen = new Set<string>();
  const environments = root.environments.map((e, i): EnvironmentConfig => {
    const where = `environments[${i}]`;
    const o = obj(e, where);
    const id = str(o.id, `${where}.id`);
    if (!ENV_ID.test(id)) throw new ConfigError(`${where}.id must match ${ENV_ID}`);
    if (seen.has(id)) throw new ConfigError(`duplicate environment id ${id}`);
    seen.add(id);
    return {
      id,
      label: o.label === undefined ? id : str(o.label, `${where}.label`),
      url: parseT3Url(str(o.url, `${where}.url`), `${where}.url`),
      tokenFile: tokenPath(str(o.tokenFile, `${where}.tokenFile`), `${where}.tokenFile`),
    };
  });

  return {
    listen: {
      host,
      port,
      allowedHostnames: (allowedHostnames as string[]).map((h) => h.toLowerCase()),
      clientKeyFile,
    },
    includeSubagents: root.includeSubagents === true,
    environments,
  };
};

export const loadConfig = (file: string = defaultConfigPath()): BridgeConfig => {
  if (!existsSync(file)) {
    throw new ConfigError(`Config not found: ${file} (copy bridge/config.example.json and set T3CRAFT_CONFIG).`);
  }
  let data: unknown;
  try {
    data = JSON.parse(readFileSync(file, "utf8"));
  } catch (e) {
    throw new ConfigError(`Config ${file} is not valid JSON: ${(e as Error).message}`);
  }
  return parseConfig(data);
};

/** Reads a secret file (token or client key). Warns when it is readable by group/others. */
export const readSecretFile = (file: string, what: string, warn: (m: string) => void = console.warn): string => {
  if (!existsSync(file)) throw new ConfigError(`${what}: file not found (${file})`);
  if (process.platform !== "win32" && (statSync(file).mode & 0o077) !== 0) {
    warn(`${what}: ${file} is readable by group/others; chmod 600 it`);
  }
  const value = readFileSync(file, "utf8").trim();
  if (!value) throw new ConfigError(`${what}: file is empty (${file})`);
  return value;
};
