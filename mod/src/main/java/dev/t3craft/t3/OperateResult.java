package dev.t3craft.t3;

import com.google.gson.JsonElement;
import org.jspecify.annotations.Nullable;

/**
 * What happened to an {@link OperateAction}. Never thrown: every path of {@link OperateGate}
 * completes with one of these. {@code message} is player-facing and secret-free; {@code value} is
 * the RPC success value (e.g. {@code {sequence}} for dispatchCommand) when {@link Outcome#OK}.
 */
public record OperateResult(Outcome outcome, String message, @Nullable JsonElement value) {
	public enum Outcome {
		/** T3 accepted it. */
		OK,
		/** No operate session for that environment: nothing was sent (the UI disables the button). */
		READ_ONLY,
		/** A destructive action reached the gate without the in-game confirmation: nothing was sent. */
		NEEDS_CONFIRMATION,
		/** The player said no in {@code ConfirmActionScreen}: nothing was sent. */
		CANCELLED,
		/** T3 refused the operate session (revoked/expired): enable control again. */
		AUTH_FAILED,
		/** Could not reach T3: nothing was sent. */
		OFFLINE,
		/** T3 answered with a failure (validation, unknown thread…). */
		REJECTED,
		/** Sent, but the answer never came (timeout or connection lost): it may or may not have applied. */
		FAILED;

		public final String wire = name().toLowerCase(java.util.Locale.ROOT);
	}

	public static OperateResult of(Outcome outcome, String message) {
		return new OperateResult(outcome, T3Connection.sanitize(message), null);
	}

	public static OperateResult ok(@Nullable JsonElement value) {
		return new OperateResult(Outcome.OK, "done", value);
	}

	public boolean ok() {
		return outcome == Outcome.OK;
	}
}
