package dev.t3craft.client.debug;

import dev.t3craft.debug.ModuleGuard;
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

	public static ClientTickEvents.EndTick endClientTick(String module, ClientTickEvents.EndTick body) {
		return mc -> ModuleGuard.get().run(module, () -> body.onEndTick(mc));
	}

	public static ClientTickEvents.StartTick startClientTick(String module, ClientTickEvents.StartTick body) {
		return mc -> ModuleGuard.get().run(module, () -> body.onStartTick(mc));
	}

	/** Generic form for any callback that can be expressed as a {@link Runnable}. */
	public static Runnable guarded(String module, Runnable body) {
		return () -> ModuleGuard.get().run(module, body);
	}
}
