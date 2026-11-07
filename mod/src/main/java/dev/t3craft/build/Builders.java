package dev.t3craft.build;

import org.jspecify.annotations.Nullable;

/**
 * Where the running {@link BuildService} is published. The only static state of the package: one
 * reference typed to an interface, set by the client entrypoint and reset on shutdown, so a hot
 * reload never finds an instance of a stale class behind a concrete type.
 */
public final class Builders {
	private static volatile @Nullable BuildService service;

	private Builders() {
	}

	/** The build service, or null before the client started / after it stopped. */
	public static @Nullable BuildService service() {
		return service;
	}

	public static void install(@Nullable BuildService s) {
		service = s;
	}

	/** Clears the reference if it still points at {@code s}. */
	public static void uninstall(BuildService s) {
		if (service == s) {
			service = null;
		}
	}
}
