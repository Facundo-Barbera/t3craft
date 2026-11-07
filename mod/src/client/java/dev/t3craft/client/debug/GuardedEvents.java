package dev.t3craft.client.debug;

import dev.t3craft.debug.ModuleGuard;
import dev.t3craft.debug.PerfStats;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Wrappers that run a Fabric client callback inside {@link ModuleGuard}, so an exception logs,
 * counts toward auto-disabling that module, and never crashes the game.
 *
 * <pre>{@code
 * // before
 * ClientTickEvents.END_CLIENT_TICK.register(mc -> npcs.tick(mc));
 * // after
 * ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("npcs", npcs::tick));
 * }</pre>
 */
public final class GuardedEvents {
	private GuardedEvents() {
	}

	/** Also timed: {@code /t3dev perf} shows {@code tick.<module>} in ms per second. */
	public static ClientTickEvents.EndTick endClientTick(String module, ClientTickEvents.EndTick body) {
		String timer = PerfStats.TICK_PREFIX + module + ".ns";
		return mc -> {
			long t0 = System.nanoTime();
			ModuleGuard.get().run(module, () -> body.onEndTick(mc));
			PerfStats.since(timer, t0);
		};
	}

	public static ClientTickEvents.StartTick startClientTick(String module, ClientTickEvents.StartTick body) {
		String timer = PerfStats.TICK_PREFIX + module + ".ns";
		return mc -> {
			long t0 = System.nanoTime();
			ModuleGuard.get().run(module, () -> body.onStartTick(mc));
			PerfStats.since(timer, t0);
		};
	}

	/** Generic form for any callback that can be expressed as a {@link Runnable}. */
	public static Runnable guarded(String module, Runnable body) {
		return () -> ModuleGuard.get().run(module, body);
	}
}
