package dev.t3craft.client.chat;

import dev.t3craft.t3.ThreadState;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The one thread order of every list in the mod (unified chat list, portable terminal, held
 * screen): threads pinned in T3 first (like T3's own "Pinned" section), then {@link Attention} (needs you → error → approval pending → working → done → idle),
 * then most recent activity (unknown last), then active before settled, then title, then key
 * (stable). Pure Java (tested).
 */
public final class ThreadOrder {
	private ThreadOrder() {
	}

	/** Builds the order for any row type. */
	public static <T> Comparator<T> of(Function<T, Attention> attention, Function<T, @Nullable Long> activityMs, Predicate<T> settled,
			Function<T, String> title, Function<T, String> key) {
		return of(r -> false, attention, activityMs, settled, title, key);
	}

	/** Same, with pinned rows first (then the usual order inside the pinned and the other group). */
	public static <T> Comparator<T> of(Predicate<T> pinned, Function<T, Attention> attention, Function<T, @Nullable Long> activityMs,
			Predicate<T> settled, Function<T, String> title, Function<T, String> key) {
		return Comparator.<T, Boolean>comparing(r -> !pinned.test(r))
				.thenComparingInt(r -> attention.apply(r).ordinal())
				.thenComparing(r -> {
					Long at = activityMs.apply(r);
					return at == null ? Long.MIN_VALUE : at;
				}, Comparator.reverseOrder())
				.thenComparing(settled::test)
				.thenComparing(r -> title.apply(r).toLowerCase(Locale.ROOT))
				.thenComparing(key);
	}

	/** The unified chat list's rows. */
	public static final Comparator<ThreadRow> ROWS = of(ThreadRow::pinned, ThreadRow::attention, ThreadRow::activityMs, ThreadRow::settled, ThreadRow::title,
			ThreadRow::key);

	/** Attention of a state wire name ({@code needs_attention}, {@code error}…) for sources without a ThreadState. */
	public static Attention attention(@Nullable String stateWire, boolean pendingRequest) {
		return Attention.of(state(stateWire), pendingRequest);
	}

	public static ThreadState state(@Nullable String wire) {
		for (ThreadState s : ThreadState.values()) {
			if (s.wire.equals(wire)) {
				return s;
			}
		}
		return ThreadState.IDLE;
	}
}
