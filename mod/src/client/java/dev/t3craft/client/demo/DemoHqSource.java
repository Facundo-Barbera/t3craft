package dev.t3craft.client.demo;

import com.google.gson.JsonObject;
import dev.t3craft.T3Craft;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.hq.HqAnchorSource;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The real modular HQ in the demo: the live roster (every environment, or {@link DemoHq#sample()}
 * when none is connected), idempotent by layout hash, built as a diff on worlds that already hold an
 * older layout. Priority {@link HqStructureSource#PRIORITY}, so it beats the shed; if the generator
 * throws, {@link #available()} is false and the demo falls back to {@link ShedStructureSource}.
 * Client thread only.
 */
public final class DemoHqSource implements DemoStructureSource {
	public static final String ID = HqStructureSource.ID;

	private final Supplier<? extends Collection<NpcInfo>> live;
	private DemoHq.@Nullable Roster roster;
	private DemoHq.@Nullable Step step;
	private @Nullable HqInput preparedInput;
	private boolean failed;

	public DemoHqSource(Supplier<? extends Collection<NpcInfo>> live) {
		this.live = Objects.requireNonNull(live, "live");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int version() {
		return DemoHq.PLAN_VERSION;
	}

	@Override
	public int priority() {
		return HqStructureSource.PRIORITY;
	}

	/** True until a {@link #prepare} failed (status before the first prepare is optimistic). */
	@Override
	public boolean available() {
		return !failed;
	}

	@Override
	public void prepare(DemoMarker built, DemoWorldSpec spec, int worldMinY) {
		try {
			DemoHq.Roster r = DemoHq.chooseRoster(live.get());
			boolean ours = ID.equals(built.structure());
			HqLayout previous = null;
			int previousLift = -1;
			JsonObject st = built.state();
			if (ours && st != null && st.has("layout")) {
				HqLayout l = HqLayout.fromJson(st.get("layout"));
				previous = l == HqLayout.EMPTY ? null : l;
				previousLift = st.has("lift") ? st.get("lift").getAsInt() : -1;
			}
			// The basement floor is the lowest HQ cell whatever the roster: use the empty HQ to size the lift.
			int planMinY = HqPlans.generate(HqLayout.EMPTY).footprint().minY();
			int lift = DemoHq.lift(spec.originY(), worldMinY, planMinY);
			boolean diffable = built.builtWith(this) && previousLift == lift;
			roster = r;
			step = DemoHq.step(r.input(), previous, built.hash(), lift, diffable, previous != null && previousLift == lift);
			preparedInput = r.input();
			failed = false;
		} catch (RuntimeException e) {
			T3Craft.LOGGER.error("[demo] HQ plan failed, falling back", e);
			roster = null;
			step = null;
			preparedInput = null;
			failed = true;
		}
	}

	/**
	 * True when the live roster no longer matches the one the last {@link #prepare} built from (a
	 * thread took or released a station): the demo reconciles the HQ again. False before any prepare
	 * and after a failed one (nothing to compare against).
	 */
	public boolean rosterChanged() {
		HqInput in = preparedInput;
		if (in == null || failed) {
			return false;
		}
		try {
			return !DemoHq.chooseRoster(live.get()).input().equals(in);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Whether the last prepare used the example roster (no threads connected). */
	public boolean sample() {
		DemoHq.Roster r = roster;
		return r != null && r.sample();
	}

	@Override
	public @Nullable String revision() {
		DemoHq.Step s = step;
		return s == null ? null : s.hash();
	}

	@Override
	public @Nullable JsonObject state() {
		DemoHq.Step s = step;
		return s == null ? null : DemoHq.state(s);
	}

	@Override
	public List<BuildPlan> plans() {
		DemoHq.Step s = step;
		return s == null ? List.of() : DemoHq.full(s.plan(), s.lift());
	}

	@Override
	public List<BuildPlan> plansSince(DemoMarker built) {
		// prepare() already computed it against the same marker: everything, the diff, or nothing.
		DemoHq.Step s = step;
		return s == null ? List.of() : s.parts();
	}

	/** NPC anchors (desks, board, benches) of the prepared HQ at the demo origin, or null if not prepared. */
	public @Nullable HqAnchorSource anchors(BlockPos demoOrigin) {
		DemoHq.Step s = step;
		return s == null ? null : new HqAnchorSource(s.plan(), DemoHq.worldSite(demoOrigin, s.lift()));
	}

	String describe() {
		DemoHq.Step s = step;
		if (s == null) {
			return failed ? "failed" : "not prepared";
		}
		return String.format(java.util.Locale.ROOT, "%s roster, %d module(s), hash %s, lift %d", sample() ? "example" : "live",
				s.layout().modules().size(), s.hash(), s.lift());
	}
}
