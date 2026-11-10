package dev.t3craft.client.operate;

import dev.t3craft.client.ModConfig;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.t3.AuditLog;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.OperateCredentials;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.Pairing;
import dev.t3craft.t3.PairingClient;
import dev.t3craft.t3.T3Connection;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Client-side owner of the operate layer: the {@link OperateGate} (backed by
 * {@code config/t3craft/credentials/operate/}, {@code config/t3craft/audit.log} and the operate
 * sockets of {@link DirectController}), plus enabling and revoking control per environment.
 * Lives outside {@link T3CraftClient} so the read-only facade stays untouched.
 */
public final class OperateClient {
	private static @Nullable OperateClient instance;

	private final OperateCredentials credentials;
	private final AuditLog audit;
	private final OperateGate gate;

	private OperateClient() {
		credentials = new OperateCredentials(ModConfig.dataDir());
		audit = AuditLog.in(ModConfig.dataDir());
		gate = new OperateGate(credentials, envId -> {
			T3CraftClient c = T3CraftClient.get();
			DirectController d = c == null ? null : c.direct();
			return d == null ? null : d.operateTransport(envId);
		}, audit);
	}

	public static synchronized OperateClient get() {
		if (instance == null) {
			instance = new OperateClient();
		}
		return instance;
	}

	public OperateGate gate() {
		return gate;
	}

	public static Optional<Environments.Environment> environment(String envId) {
		return new Environments(ModConfig.dataDir()).list().stream().filter(e -> e.id().equals(envId)).findFirst();
	}

	public static String label(String envId) {
		return environment(envId).map(Environments.Environment::label).orElse(envId);
	}

	public record Result(boolean ok, String message) {
	}

	/**
	 * "Enable control" (upgrade) for an environment paired read-only: exchanges a one-time
	 * credential issued with {@code --scope orchestration:read --scope orchestration:operate} for an
	 * operate session and stores it apart from the read-only one, which stays untouched (nothing is
	 * lost if this fails). The link must point at the environment's own host and the grant must
	 * include operate ({@link Pairing#requireOperate}). {@code done} runs on the client thread with
	 * a secret-free message.
	 */
	public void enable(String envId, String linkOrCode, OperateGate.Origin origin, Consumer<Result> done) {
		Minecraft mc = Minecraft.getInstance();
		Environments.Environment env = environment(envId).orElse(null);
		if (env == null) {
			done.accept(new Result(false, "No paired environment " + envId + ": pair it first (/t3craft pair)."));
			return;
		}
		Pairing.Target target;
		try {
			target = Pairing.parse(linkOrCode, env.url().toString());
		} catch (Pairing.PairingException e) {
			done.accept(new Result(false, e.getMessage()));
			return;
		}
		if (!target.baseUrl().equals(env.url())) {
			done.accept(new Result(false, "That link is for " + target.baseUrl() + ", but " + env.label() + " is " + env.url() + "."));
			return;
		}
		String version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("t3craft")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		new PairingClient(java.net.http.HttpClient.newHttpClient())
				.exchange(target, "T3Craft operate (Minecraft " + version + ")", System.getProperty("os.name", ""), Pairing.OPERATE_SCOPES)
				.whenComplete((session, err) -> {
					Result r;
					if (err != null) {
						Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
						r = new Result(false, cause instanceof Pairing.PairingException
								? cause.getMessage()
								: "Could not reach " + env.url() + ": " + T3Connection.sanitize(cause.getClass().getSimpleName()));
					} else {
						try {
							Pairing.requireOperate(session);
							credentials.save(envId, session);
							resetTransport(envId);
							r = new Result(true, "Control (operate) enabled for " + env.label() + " until "
									+ java.time.Instant.ofEpochMilli(session.expiresAtMs()).toString().substring(0, 10)
									+ ". Revoke it any time: \"Revoke control\" or /t3craft operate revoke " + envId + ".");
						} catch (Pairing.PairingException e) {
							r = new Result(false, e.getMessage());
						} catch (IOException | IllegalArgumentException e) {
							r = new Result(false, "Could not save the operate session: " + e.getClass().getSimpleName());
						}
					}
					audit.append(new AuditLog.Entry(System.currentTimeMillis(), envId, null, "operate_enable", false,
							r.ok() ? "ok" : "failed", origin.wire, r.ok() ? "enabled" : r.message()));
					Result fr = r;
					mc.execute(() -> done.accept(fr));
				});
	}

	/**
	 * Deletes the local operate token (never contacts T3): the environment stays paired, read-only.
	 * With a combined read + operate pairing the read zone keeps the same session, which still
	 * carries operate on the host: the message says so. Returns a player-facing message.
	 */
	public Result revoke(String envId, OperateGate.Origin origin) {
		try {
			boolean shared = credentials.sharesReadSession(envId);
			boolean had = credentials.revoke(envId);
			resetTransport(envId);
			audit.append(new AuditLog.Entry(System.currentTimeMillis(), envId, null, "operate_revoke", false,
					had ? "ok" : "none", origin.wire, had ? "local token deleted" : "no operate token"));
			return had
					? new Result(true, "Control revoked for " + label(envId) + ": read-only now (local operate token deleted). "
							+ (shared ? OperateCredentials.SHARED_REVOKE_HINT : OperateCredentials.HOST_REVOKE_HINT) + ".")
					: new Result(false, label(envId) + " has no control (operate) enabled.");
		} catch (IOException e) {
			return new Result(false, "Could not delete the operate token: " + e.getClass().getSimpleName());
		}
	}

	private static void resetTransport(String envId) {
		T3CraftClient c = T3CraftClient.get();
		DirectController d = c == null ? null : c.direct();
		if (d != null) {
			d.resetOperate(envId);
		}
	}
}
