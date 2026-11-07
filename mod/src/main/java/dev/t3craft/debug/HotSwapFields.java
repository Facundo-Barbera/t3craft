package dev.t3craft.debug;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Lazy init for fields of long-lived objects (entrypoints). A field added by an enhanced
 * hot-swap is {@code null} on the existing instance (its initializer and
 * {@code onInitializeClient} never run again), so read it through a getter:
 * <pre>{@code return helpers = HotSwapFields.orInit(helpers, HelperDirector::new);}</pre>
 */
public final class HotSwapFields {
	private HotSwapFields() {
	}

	/** {@code current} if set, otherwise a fresh value from {@code init} (never null). */
	public static <T> T orInit(@Nullable T current, Supplier<T> init) {
		if (current != null) {
			return current;
		}
		T value = init.get();
		if (value == null) {
			throw new IllegalStateException("HotSwapFields init returned null");
		}
		return value;
	}
}
