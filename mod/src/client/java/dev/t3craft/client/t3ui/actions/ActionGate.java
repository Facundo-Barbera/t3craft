package dev.t3craft.client.t3ui.actions;

import dev.t3craft.t3.OperateGate;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Whether a write widget of this package is usable for an environment, and what it shows when it
 * is not. Every write goes through {@link OperateGate}; without an operate session the widget is
 * drawn disabled and its button reads "Enable control" (lang {@code t3craft.actions.enable_control},
 * "Habilitar control"), which opens the enable/pair screen. Pure.
 */
public final class ActionGate {
	public enum State {
		/** Operate session present: the widget works. */
		ENABLED,
		/** No operate session: disabled, "Enable control". */
		READ_ONLY,
		/** The session expired: disabled, "Enable control" (again). */
		EXPIRED,
		/** No environment chosen / known. */
		NO_ENV
	}

	private final Function<String, OperateGate.Availability> availability;

	public ActionGate(Function<String, OperateGate.Availability> availability) {
		this.availability = availability;
	}

	public static ActionGate of(OperateGate gate) {
		return new ActionGate(gate::availability);
	}

	public State state(@Nullable String envId) {
		if (envId == null || envId.isBlank()) {
			return State.NO_ENV;
		}
		return switch (availability.apply(envId)) {
			case ENABLED -> State.ENABLED;
			case READ_ONLY -> State.READ_ONLY;
			case EXPIRED -> State.EXPIRED;
		};
	}

	public boolean enabled(@Nullable String envId) {
		return state(envId) == State.ENABLED;
	}

	/** Lang key of the disabled button / hint, or null when enabled. */
	public static @Nullable String disabledKey(State s) {
		return switch (s) {
			case ENABLED -> null;
			case READ_ONLY -> "t3craft.actions.enable_control";
			case EXPIRED -> "t3craft.actions.enable_control.expired";
			case NO_ENV -> "t3craft.actions.no_env";
		};
	}
}
