import { describe, expect, it } from "vitest";
import { ConfigError, isAllowedListenHost, parseConfig, REPO_ROOT } from "../src/config.ts";
import example from "../config.example.json" with { type: "json" };

describe("isAllowedListenHost", () => {
  it.each(["100.64.0.1", "100.101.102.103", "100.127.255.255", "127.0.0.1", "::1", "fd7a:115c:a1e0::1234"])(
    "allows %s",
    (h) => expect(isAllowedListenHost(h)).toBe(true),
  );
  it.each(["0.0.0.0", "::", "100.128.0.1", "100.63.255.255", "192.168.1.10", "8.8.8.8", "localhost", "fd00::1", ""])(
    "refuses %s",
    (h) => expect(isAllowedListenHost(h)).toBe(false),
  );
});

describe("parseConfig", () => {
  it("accepts the example config", () => {
    const c = parseConfig(example);
    expect(c.environments.map((e) => e.id)).toEqual(["vps", "acmesite"]);
    expect(c.environments[1]?.url.port).toBe("3773");
    expect(c.listen.port).toBe(7878);
  });
  const withListen = (host: string) => ({ ...example, listen: { ...example.listen, host } });
  it("refuses 0.0.0.0", () => expect(() => parseConfig(withListen("0.0.0.0"))).toThrow(ConfigError));
  it("refuses a public IP", () => expect(() => parseConfig(withListen("203.0.113.5"))).toThrow(/not allowed/));
  it("refuses duplicate environment ids", () =>
    expect(() => parseConfig({ ...example, environments: [example.environments[0], example.environments[0]] })).toThrow(/duplicate/));
  it("refuses credentials in URLs", () =>
    expect(() =>
      parseConfig({ ...example, environments: [{ ...example.environments[0], url: "https://u:p@host.ts.net" }] }),
    ).toThrow(/credentials/));
  it("refuses token files inside the repo", () =>
    expect(() =>
      parseConfig({ ...example, environments: [{ ...example.environments[0], tokenFile: `${REPO_ROOT}/vps.token` }] }),
    ).toThrow(/inside the repo/));
  it("refuses relative token paths", () =>
    expect(() =>
      parseConfig({ ...example, environments: [{ ...example.environments[0], tokenFile: "vps.token" }] }),
    ).toThrow(/absolute/));
});
