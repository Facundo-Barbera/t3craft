package dev.t3craft.debug.gametest;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.debug.DevReload;
import dev.t3craft.debug.ModuleGuard;
import dev.t3craft.debug.ResetReason;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Smoke tests that need a real client: the mod boots, a tick module that throws is contained and
 * auto-disabled while the game keeps ticking, and a full reset with a world loaded does not crash.
 *
 * <p>Run with {@code ./gradlew runClientGameTest} (boots Minecraft; CI only, never on the VPS).
 */
public final class T3CraftClientGameTest implements FabricClientGameTest {
	private static final String BOOM = "gametest.boom";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> check(T3CraftClient.get() != null, "T3CraftClient entrypoint did not run"));

		AtomicInteger calls = new AtomicInteger();
		AtomicInteger ticksAfter = new AtomicInteger();
		context.runOnClient(mc -> {
			ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick(BOOM, c -> {
				calls.incrementAndGet();
				throw new ClassCastException("simulated stale object after hot-swap");
			}));
			ClientTickEvents.END_CLIENT_TICK.register(c -> ticksAfter.incrementAndGet());
		});

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);

			int ticked = ticksAfter.get();
			check(ticked >= 20, "game stopped ticking after a module failure (" + ticked + " ticks)");
			check(!ModuleGuard.get().isEnabled(BOOM), "failing module was not auto-disabled");
			check(calls.get() == ModuleGuard.DEFAULT_MAX_FAILURES,
					"failing module ran " + calls.get() + " times, expected " + ModuleGuard.DEFAULT_MAX_FAILURES);

			// Manual reset with a world loaded: every registered holder resets, nothing crashes,
			// and disabled modules get another chance (and get disabled again).
			context.runOnClient(mc -> DevReload.get().resetAll(ResetReason.MANUAL));
			context.waitTicks(10);
			check(calls.get() == 2 * ModuleGuard.DEFAULT_MAX_FAILURES,
					"module was not retried exactly once more after a manual reset (" + calls.get() + ")");
			check(!ModuleGuard.get().isEnabled(BOOM), "failing module stayed enabled after the retry");

			// Redefinition check on unchanged classes is a no-op.
			context.runOnClient(mc -> check(!DevReload.get().checkForRedefinition(), "false redefinition"));
		}
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}
}
