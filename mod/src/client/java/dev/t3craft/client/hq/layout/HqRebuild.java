package dev.t3craft.client.hq.layout;

import dev.t3craft.hq.layout.HqLayouts;
import org.jspecify.annotations.Nullable;

/**
 * The single entry point to switch the HQ to another layout: it saves {@code hqLayout} in the config
 * and runs the existing HQ world reset ({@code /t3craft debug reset-hq}: only inside the HQ world, zip
 * backup first unless {@code demo.resetBackup = false}); the fresh world then builds with that layout.
 * A layout is never built over an HQ built with another one.
 */
public final class HqRebuild {
	private HqRebuild() {
	}

	/** Null when {@link #resetAndRebuild} may run now, else why not (not in the HQ world, ...). */
	public static @Nullable String refusal() {
		return dev.t3craft.client.demo.DemoClient.resetRefusal();
	}

	/**
	 * Saves {@code layoutId} ({@link HqLayouts#resolve}) as {@code hqLayout}, then resets the HQ world
	 * (backup, delete, recreate) on the next client tick; the new world is built with that layout.
	 * Returns the refusal (and does nothing, not even saving the config) when a reset is not possible.
	 * Ask the user to confirm before calling: everything built in the HQ world is lost.
	 */
	public static @Nullable String resetAndRebuild(String layoutId) {
		String why = refusal();
		if (why != null) {
			return why;
		}
		HqLayoutConfig.select(layoutId);
		dev.t3craft.client.demo.DemoClient.requestReset();
		return null;
	}
}
