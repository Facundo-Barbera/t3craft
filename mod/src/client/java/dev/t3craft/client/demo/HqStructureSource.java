package dev.t3craft.client.demo;

import dev.t3craft.build.BuildPlan;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Generic adapter: any fixed list of plans as the {@code "hq"} source. The demo itself registers
 * {@link DemoHqSource} (the real HQ with the live roster, layout hash and diffs); this class stays for
 * callers that just want to plug a static plan list in (registering it replaces {@link DemoHqSource},
 * same id).
 *
 * <p>Wiring, once {@code dev.t3craft.hq} is on main (one call, e.g. in {@code DemoClient} or in the
 * HQ feature's own init):
 * <pre>{@code
 * DemoClient.structures().register(new HqStructureSource(HQ_PLAN_VERSION,
 *         () -> HqPlans.forRoster(currentRoster())));   // List<dev.t3craft.build.BuildPlan>
 * }</pre>
 * If the HQ generator produces its own plan type, convert it here in the supplier (each placement
 * to {@code dev.t3craft.build.Placement}, then {@code BuildPlan.of(list).layered()}). Bump the
 * version whenever the generator output changes, so existing demo worlds get the new HQ.
 *
 * <p>Priority {@link #PRIORITY} beats the shed, so with {@code demo.structure = "auto"} (default) the
 * HQ is built as soon as it is registered. A supplier that returns an empty list or throws makes
 * the source unavailable and the demo falls back to the shed.
 */
public final class HqStructureSource implements DemoStructureSource {
	public static final String ID = "hq";
	public static final int PRIORITY = 100;

	private final int version;
	private final Supplier<List<BuildPlan>> plans;
	private @Nullable List<BuildPlan> cached;

	public HqStructureSource(int version, Supplier<List<BuildPlan>> plans) {
		this.version = version;
		this.plans = Objects.requireNonNull(plans, "plans");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int version() {
		return version;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	@Override
	public boolean available() {
		return !plans().isEmpty();
	}

	/** Computed once per instance (the generator can be expensive); empty when the supplier fails. */
	@Override
	public synchronized List<BuildPlan> plans() {
		if (cached == null) {
			List<BuildPlan> p;
			try {
				p = plans.get();
			} catch (RuntimeException e) {
				p = null;
			}
			cached = p == null ? List.of() : List.copyOf(p);
		}
		return cached;
	}
}
