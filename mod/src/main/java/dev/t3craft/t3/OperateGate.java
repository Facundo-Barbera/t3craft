package dev.t3craft.t3;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The single door for writes ({@code orchestration:operate}). Action threads build an
 * {@link OperateAction} and call {@link #submit}; on the client they go through
 * {@code dev.t3craft.client.operate.OperateUi}, which adds the confirmation screen.
 * <ul>
 *   <li>No operate session for the environment → {@link OperateResult.Outcome#READ_ONLY}, nothing
 *   sent, no exception. Without operate tokens everything keeps working read-only.</li>
 *   <li>Destructive action ({@link OperateKind#destructive}) without {@code confirmed} →
 *   {@link OperateResult.Outcome#NEEDS_CONFIRMATION}, nothing sent.</li>
 *   <li>Otherwise the environment's {@link OperateTransport} sends it.</li>
 *   <li>Every attempt, whatever the outcome, lands in the {@link AuditLog}.</li>
 * </ul>
 * Futures never complete exceptionally. They complete on a connection thread: hop to the client
 * thread before touching Minecraft.
 */
public final class OperateGate {
	/** Where the action came from, for the audit log. */
	public enum Origin {
		UI, COMMAND;

		public final String wire = name().toLowerCase(java.util.Locale.ROOT);
	}

	public enum Availability {
		/** Operate session present and not expired. */
		ENABLED,
		/** No operate session: read-only. */
		READ_ONLY,
		/** There was one, it expired: read-only until enabled again. */
		EXPIRED
	}

	/** Finds the live transport for an environment, or null (not connected / bridge mode). */
	public interface Transports {
		@Nullable OperateTransport forEnv(String envId);
	}

	private final OperateCredentials credentials;
	private final Transports transports;
	private final AuditLog audit;
	private final LongSupplier clock;

	public OperateGate(OperateCredentials credentials, Transports transports, AuditLog audit, LongSupplier clock) {
		this.credentials = credentials;
		this.transports = transports;
		this.audit = audit;
		this.clock = clock;
	}

	public OperateGate(OperateCredentials credentials, Transports transports, AuditLog audit) {
		this(credentials, transports, audit, System::currentTimeMillis);
	}

	public OperateCredentials credentials() {
		return credentials;
	}

	public AuditLog audit() {
		return audit;
	}

	public Availability availability(String envId) {
		if (credentials.get(envId, clock.getAsLong()) != null) {
			return Availability.ENABLED;
		}
		return credentials.stored(envId) != null ? Availability.EXPIRED : Availability.READ_ONLY;
	}

	public boolean canOperate(String envId) {
		return availability(envId) == Availability.ENABLED;
	}

	/** {@code operate(env).submit(action, origin, confirmed)}: a view bound to one environment. */
	public Env operate(String envId) {
		return new Env(envId);
	}

	public final class Env {
		private final String envId;

		private Env(String envId) {
			this.envId = envId;
		}

		public String id() {
			return envId;
		}

		public boolean available() {
			return canOperate(envId);
		}

		public Availability availability() {
			return OperateGate.this.availability(envId);
		}

		/** Builds a {@code dispatchCommand} action for this environment. */
		public OperateAction dispatch(OperateKind kind, @Nullable String threadId, @Nullable String threadTitle, com.google.gson.JsonObject command) {
			return OperateAction.dispatch(kind, envId, threadId, threadTitle, command);
		}

		public CompletableFuture<OperateResult> submit(OperateAction action, Origin origin, boolean confirmed) {
			if (!action.envId().equals(envId)) {
				return CompletableFuture.completedFuture(OperateResult.of(OperateResult.Outcome.REJECTED, "action is for another environment"));
			}
			return OperateGate.this.submit(action, origin, confirmed);
		}
	}

	/**
	 * Sends {@code action} if allowed. {@code confirmed} must be true for destructive actions and
	 * only the confirmation screen (or an explicit command argument) may set it.
	 */
	public CompletableFuture<OperateResult> submit(OperateAction action, Origin origin, boolean confirmed) {
		Availability av = availability(action.envId());
		if (av != Availability.ENABLED) {
			return done(action, origin, OperateResult.of(OperateResult.Outcome.READ_ONLY, av == Availability.EXPIRED
					? "operate session expired for " + action.envId() + ": enable control again"
					: "read-only: control (operate) is not enabled for " + action.envId()));
		}
		if (action.destructive() && !confirmed) {
			return done(action, origin, OperateResult.of(OperateResult.Outcome.NEEDS_CONFIRMATION,
					action.kind().wire + " is destructive and needs an in-game confirmation"));
		}
		OperateTransport t = transports.forEnv(action.envId());
		if (t == null) {
			return done(action, origin, OperateResult.of(OperateResult.Outcome.OFFLINE, "no direct connection to " + action.envId()));
		}
		CompletableFuture<OperateResult> sent;
		try {
			sent = t.call(action.rpcTag(), action.payload());
		} catch (RuntimeException e) {
			sent = CompletableFuture.completedFuture(OperateResult.of(OperateResult.Outcome.FAILED, e.getClass().getSimpleName()));
		}
		return sent.handle((r, err) -> {
			OperateResult res = err != null || r == null
					? OperateResult.of(OperateResult.Outcome.FAILED, err == null ? "no result" : err.getClass().getSimpleName())
					: r;
			record(action, origin, res);
			return res;
		});
	}

	/** Records an attempt the player cancelled in the confirmation screen. */
	public OperateResult cancelled(OperateAction action, Origin origin) {
		OperateResult r = OperateResult.of(OperateResult.Outcome.CANCELLED, "cancelled");
		record(action, origin, r);
		return r;
	}

	private CompletableFuture<OperateResult> done(OperateAction action, Origin origin, OperateResult r) {
		record(action, origin, r);
		return CompletableFuture.completedFuture(r);
	}

	private void record(OperateAction a, Origin origin, OperateResult r) {
		audit.append(new AuditLog.Entry(clock.getAsLong(), a.envId(), a.threadId(), a.kind().wire, a.destructive(),
				r.outcome().wire, origin.wire, r.message()));
	}
}
