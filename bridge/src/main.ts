import * as Effect from "effect/Effect";
import * as Fiber from "effect/Fiber";
import { defaultConfigPath, loadConfig, readSecretFile } from "./config.ts";
import { Bridge, BRIDGE_VERSION } from "./bridge.ts";
import { startModSocket } from "./server/modSocket.ts";

const log = (m: string) => console.log(`${new Date().toISOString()} ${m}`);

const main = async () => {
  const configPath = defaultConfigPath();
  const config = loadConfig(configPath);
  log(`t3craft bridge ${BRIDGE_VERSION}, config ${configPath}, ${config.environments.length} environment(s)`);
  const clientKey = config.listen.clientKeyFile ? readSecretFile(config.listen.clientKeyFile, "client key", log) : null;

  const bridge = new Bridge(config, log);
  const server = await startModSocket({
    host: config.listen.host,
    port: config.listen.port,
    allowedHostnames: config.listen.allowedHostnames,
    clientKey,
    bridgeVersion: BRIDGE_VERSION,
    log,
    onReady: (send) => bridge.initialFrames().forEach(send),
    // TODO(phase 2): chat.open/close/send, run.interrupt. TODO(phase 3): permission.respond. TODO(phase 4): thread.create.
    onMessage: () => "not implemented",
  });
  bridge.setBroadcast((m) => server.broadcast(m));
  log(`mod socket listening on ws://${config.listen.host.includes(":") ? `[${config.listen.host}]` : config.listen.host}:${server.port()}`);

  const fiber = Effect.runFork(bridge.run());
  const stop = async (sig: string) => {
    log(`${sig}: stopping`);
    await Effect.runPromise(Fiber.interrupt(fiber));
    await server.close();
    process.exit(0);
  };
  process.once("SIGINT", () => void stop("SIGINT"));
  process.once("SIGTERM", () => void stop("SIGTERM"));
};

main().catch((e: unknown) => {
  console.error(e instanceof Error ? `${e.name}: ${e.message}` : e);
  process.exit(1);
});
