package dev.t3craft.hq;

import dev.t3craft.build.Offload;
import dev.t3craft.debug.PerfStats;
import dev.t3craft.hq.plan.BuildPlan;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * fix/hq-load-build-portal: the plan of the empty HQ ({@link HqLayout#EMPTY}), computed once and shared.
 *
 * <p>Entering the HQ world asks for it several times to place the spawn (the viewpoint, the centre of the hub,
 * the old spawns): each ask used to run the whole generator (the citadel's ground, lighting, packs: seconds)
 * on the server thread inside {@code SERVER_STARTED}, which is what froze the loading screen at 16% for ~30 s.
 * Now the first ask starts one background computation ({@link #start}, also called ahead of time when the game
 * starts and when the player opens the world), later asks join the same future, and a change of what decides the
 * blocks (style, fixtures, kit, layout pack) simply makes a new one. The plan is immutable, so sharing it is safe.
 * The last few plans are kept (two callers may draw with different styles), not one per style ever seen.
 */
public final class HqEmptyPlan {
	/** What decides the blocks of the empty plan. */
	private record Key(String style, String fixtures, String kit, int packs) {
	}

	private static final int KEEP = 3;
	private static final Map<Key, CompletableFuture<BuildPlan>> PLANS = new LinkedHashMap<>(8, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, CompletableFuture<BuildPlan>> eldest) {
			return size() > KEEP;
		}
	};

	private HqEmptyPlan() {
	}

	/** Starts (or joins) the computation of the empty plan drawn by {@code style}; never blocks. */
	public static synchronized CompletableFuture<BuildPlan> start(HqStyle style) {
		Objects.requireNonNull(style, "style");
		Key k = new Key(style.id(), HqFixtures.fingerprint(), HqKit.fingerprint(), dev.t3craft.hq.pack.HqLayoutPacks.generation());
		CompletableFuture<BuildPlan> f = PLANS.get(k);
		if (f == null || f.isCompletedExceptionally()) {
			f = Offload.supply(PerfStats.STEP_HQ_LOAD_ASYNC, () -> HqPlans.generate(HqLayout.EMPTY, style));
			PLANS.put(k, f);
		}
		return f;
	}

	/**
	 * The empty plan drawn by {@code style}. Waits for the background computation if it is still running
	 * (nothing to wait for when it was started ahead of time); a failure comes out as a
	 * {@link RuntimeException}, like the generator itself.
	 */
	public static BuildPlan of(HqStyle style) {
		return start(style).join();
	}
}
