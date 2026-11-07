import { afterEach, describe, expect, it } from "vitest";
import WebSocket from "ws";
import { hostnameOf, isAcceptableUpgrade, startModSocket, type ModSocketServer } from "../src/server/modSocket.ts";

describe("upgrade policy", () => {
  it("parses Host headers", () => {
    expect(hostnameOf("127.0.0.1:7878")).toBe("127.0.0.1");
    expect(hostnameOf("[::1]:7878")).toBe("[::1]");
    expect(hostnameOf("my-Homelab.ts.net")).toBe("my-homelab.ts.net");
  });
  it("refuses any Origin", () =>
    expect(isAcceptableUpgrade({ host: "127.0.0.1:7878", origin: "http://evil.example" }, "127.0.0.1")).toBe(false));
  it("refuses foreign Host (DNS rebinding)", () =>
    expect(isAcceptableUpgrade({ host: "evil.example:7878" }, "100.64.0.10")).toBe(false));
  it("accepts the listen IP and allowed names", () => {
    expect(isAcceptableUpgrade({ host: "100.64.0.10:7878" }, "100.64.0.10")).toBe(true);
    expect(isAcceptableUpgrade({ host: "homelab.ts.net:7878" }, "100.64.0.10", ["homelab.ts.net"])).toBe(true);
  });
  it("refuses a missing Host", () => expect(isAcceptableUpgrade({}, "127.0.0.1")).toBe(false));
});

describe("startModSocket (127.0.0.1, ephemeral port)", () => {
  let server: ModSocketServer | undefined;
  afterEach(async () => {
    await server?.close();
    server = undefined;
  });

  it("refuses to bind a wildcard address", async () => {
    await expect(
      startModSocket({ host: "0.0.0.0", port: 0, bridgeVersion: "t", onReady: () => {}, onMessage: () => null }),
    ).rejects.toThrow(/refusing/);
  });

  const open = (port: number, headers: Record<string, string> = {}) =>
    new WebSocket(`ws://127.0.0.1:${port}/`, { headers });

  const collect = (ws: WebSocket, n: number) =>
    new Promise<Record<string, unknown>[]>((resolve, reject) => {
      const got: Record<string, unknown>[] = [];
      ws.on("message", (d) => {
        got.push(JSON.parse(d.toString()) as Record<string, unknown>);
        if (got.length === n) resolve(got);
      });
      ws.on("error", reject);
    });

  it("handshakes, sends initial frames and acks unimplemented messages", async () => {
    server = await startModSocket({
      host: "127.0.0.1",
      port: 0,
      bridgeVersion: "test",
      onReady: (send) => send({ v: 1, type: "snapshot", npcs: [] }),
      onMessage: () => "not implemented",
    });
    const ws = open(server.port());
    await new Promise((r) => ws.once("open", r));
    const frames = collect(ws, 4);
    ws.send(JSON.stringify({ v: 1, type: "hello", id: "m1", client: "test", version: "0", protocol: 1 }));
    ws.send(JSON.stringify({ v: 1, type: "chat.open", id: "m2", npcId: "vps:x" }));
    const got = await frames;
    expect(got.map((f) => f.type)).toEqual(["ack", "hello", "snapshot", "ack"]);
    expect(got[3]).toMatchObject({ re: "m2", ok: false, error: "not implemented" });
    ws.close();
  });

  it("rejects connections with an Origin header", async () => {
    server = await startModSocket({ host: "127.0.0.1", port: 0, bridgeVersion: "t", onReady: () => {}, onMessage: () => null });
    const ws = open(server.port(), { origin: "http://evil.example" });
    const status = await new Promise<number | undefined>((resolve) =>
      ws.on("unexpected-response", (_req, res) => resolve(res.statusCode)),
    );
    expect(status).toBe(403);
  });

  it("closes on a wrong client key", async () => {
    server = await startModSocket({
      host: "127.0.0.1",
      port: 0,
      clientKey: "right-key",
      bridgeVersion: "t",
      onReady: () => {},
      onMessage: () => null,
    });
    const ws = open(server.port());
    await new Promise((r) => ws.once("open", r));
    const code = new Promise<number>((resolve) => ws.on("close", (c) => resolve(c)));
    ws.send(JSON.stringify({ v: 1, type: "hello", client: "t", version: "0", protocol: 1, key: "wrong" }));
    expect(await code).toBe(4003);
  });
});
