package dev.t3craft.client.demo;

import com.google.gson.JsonObject;
import dev.t3craft.T3Craft;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.hq.HqAnchorSource;
import dev.t3craft.hq.HqFixtures;
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
	/** The configured layout ({@code hqLayout}); used only while the HQ world holds no HQ. */
	private final Supplier<String> configuredLayout;
	/** feat/hq-layouts: the layout of the last prepare and the configured one when they differ (else null). */
	private volatile String layoutId = dev.t3craft.hq.layout.HqLayouts.DEFAULT_ID;
	private volatile @Nullable String pendingLayout;
	private DemoHq.@Nullable Roster roster;
	private DemoHq.@Nullable Step step;
	private @Nullable HqInput preparedInput;
	/** feat/hq-layout-packs: {@link dev.t3craft.hq.pack.HqLayoutPacks#generation()} of the last prepare. */
	private int preparedPacks = -1;
	private boolean failed;

	public DemoHqSource(Supplier<? extends Collection<NpcInfo>> live) {
		this(live, dev.t3craft.client.hq.layout.HqLayoutConfig::selectedId);
	}

	/** {@code configuredLayout}: the {@code hqLayout} config value (tests pass their own). */
	public DemoHqSource(Supplier<? extends Collection<NpcInfo>> live, Supplier<String> configuredLayout) {
		this.live = Objects.requireNonNull(live, "live");
		this.configuredLayout = Objects.requireNonNull(configuredLayout, "configuredLayout");
	}

	/**
	 * The layout to plan with (feat/hq-layouts): the one the world's HQ is built with when there is
	 * one (a different configured layout is never built over it, it waits for an explicit reset +
	 * rebuild), else the configured one. Pure.
	 */
	public static String effectiveLayout(@Nullable String builtLayoutId, @Nullable String configuredLayoutId) {
		return dev.t3craft.hq.layout.HqLayouts.resolve(builtLayoutId != null ? builtLayoutId : configuredLayoutId).id();
	}

	/** Layout of the last prepare. */
	public String layoutId() {
		return layoutId;
	}

	/** The configured layout when it differs from the one the HQ is built with (it waits for a reset), else null. */
	public @Nullable String pendingLayout() {
		return pendingLayout;
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

	/**
	 * fix/new-thread-hitch: everything {@link #prepare} computes, built off the client thread by
	 * {@link #prepareLater} and installed by {@link #adopt}.
	 */
	public record Prepared(String layoutId, @Nullable String pendingLayout, DemoHq.Roster roster, DemoHq.Step step, int packs) {
	}

	@Override
	public void prepare(DemoMarker built, DemoWorldSpec spec, int worldMinY) {
		try {
			adopt(prepareLater(built, spec, worldMinY).get());
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	/**
	 * fix/new-thread-hitch: the client-thread half of {@link #prepare}: reads the live roster and the
	 * configured layout, selects the layout, and returns the heavy half (layout plan, old plan, diff,
	 * baked parts, plan fingerprint) as a pure supplier for a background worker. Install its result with
	 * {@link #adopt} on the client thread ({@link #fail} if it threw).
	 */
	public Supplier<Prepared> prepareLater(DemoMarker built, DemoWorldSpec spec, int worldMinY) {
		// feat/hq-layouts: plan with the layout the HQ is built with, or the configured one on an empty world
		String builtLayout = dev.t3craft.client.hq.layout.HqLayoutState.fromMarker(built.structure(), built.state());
		String configured = dev.t3craft.hq.layout.HqLayouts.resolve(configuredLayout.get()).id();
		String effective = effectiveLayout(builtLayout, configured);
		dev.t3craft.hq.layout.HqLayouts.use(effective);
		String pending = builtLayout != null && !builtLayout.equals(configured) ? configured : null;
		DemoHq.Roster r = DemoHq.chooseRoster(live.get());
		int packs = dev.t3craft.hq.pack.HqLayoutPacks.generation();
		boolean builtWithThis = built.builtWith(this);
		return () -> {
			boolean ours = ID.equals(built.structure());
			HqLayout previous = null;
			int previousLift = -1;
			String previousFixtures = dev.t3craft.hq.HqPlans.buildKey(HqFixtures.VANILLA);
			String previousPack = "";
			JsonObject st = built.state();
			if (ours && st != null && st.has("layout")) {
				HqLayout l = HqLayout.fromJson(st.get("layout"));
				previous = l == HqLayout.EMPTY ? null : l;
				previousLift = st.has("lift") ? st.get("lift").getAsInt() : -1;
				if (st.has(DemoHq.FIXTURES)) {
					previousFixtures = st.get(DemoHq.FIXTURES).getAsString();
				}
				if (st.has(DemoHq.PACK)) {
					previousPack = st.get(DemoHq.PACK).getAsString();
				}
			}
			// The floor layer is the lowest HQ cell whatever the roster: use the empty HQ to size the lift.
			int planMinY = HqPlans.generate(HqLayout.EMPTY).footprint().minY();
			int lift = DemoHq.lift(spec.originY(), worldMinY, planMinY);
			boolean diffable = builtWithThis && previousLift == lift;
			// feat/hq-void-world: in a void world the HQ stands on its island (grown from the one already built)
			DemoHq.Island island = spec.voidWorld() ? new DemoHq.Island(ours ? DemoHq.island(st) : null, starter(built, spec)) : null;
			DemoHq.Step step = DemoHq.step(r.input(), previous, built.hash(), lift, diffable, previousFixtures,
					previous != null && previousLift == lift, previousPack, island);
			step.plan().fingerprint(); // lazy SHA-256 over every placement: here, not in HqPresence on the client thread
			return new Prepared(effective, pending, r, step, packs);
		};
	}

	/** Client thread: installs a {@link #prepareLater} result. */
	public void adopt(Prepared p) {
		layoutId = p.layoutId();
		pendingLayout = p.pendingLayout();
		roster = p.roster();
		step = p.step();
		preparedInput = p.roster().input();
		preparedPacks = p.packs();
		failed = false;
	}

	/** Client thread: a prepare failed: the demo falls back to the next source. */
	public void fail(Throwable e) {
		T3Craft.LOGGER.error("[demo] HQ plan failed, falling back", e);
		roster = null;
		step = null;
		preparedInput = null;
		failed = true;
	}

	/** The starter island recorded in the marker, demo-relative (null when none was placed). */
	static VoidIsland.@Nullable Starter starter(DemoMarker built, DemoWorldSpec spec) {
		DemoMarker.Cell c = built.starter();
		return c == null ? null : new VoidIsland.Starter(c.x() - spec.originX(), c.y() - spec.originY(), c.z() - spec.originZ());
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

	/**
	 * feat/hq-layout-packs: true when a layout pack was reloaded since the last {@link #prepare} (the demo
	 * reconciles the HQ again, as a diff). False before any prepare and after a failed one.
	 */
	public boolean packsChanged() {
		return preparedInput != null && !failed && preparedPacks != dev.t3craft.hq.pack.HqLayoutPacks.generation();
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
		return s == null ? List.of() : s.everything();
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

	/** Automatic fixture bindings for the prepared HQ at the demo origin, or null if not prepared. */
	public @Nullable DemoHqBindings bindings(BlockPos demoOrigin) {
		DemoHq.Step s = step;
		return s == null ? null : new DemoHqBindings(s.layout(), DemoHq.worldSite(demoOrigin, s.lift()));
	}

	/** The prepared HQ standing at the demo origin (for NPCs and workstations), or null if not prepared. */
	public dev.t3craft.client.hq.HqPresence.@Nullable Standing standing(BlockPos demoOrigin) {
		DemoHq.Step s = step;
		return s == null ? null : new dev.t3craft.client.hq.HqPresence.Standing(s.plan(), DemoHq.worldSite(demoOrigin, s.lift()), null);
	}

	String describe() {
		DemoHq.Step s = step;
		if (s == null) {
			return failed ? "failed" : "not prepared";
		}
		String pending = pendingLayout;
		return String.format(java.util.Locale.ROOT, "layout %s%s, %s roster, %d module(s), hash %s, lift %d, fixtures %s%s", layoutId,
				pending == null ? "" : " (configured " + pending + ": applies after a reset)", sample() ? "example" : "live",
				s.layout().modules().size(), s.hash(), s.lift(), s.fixtures(), s.island() == null ? "" : ", void island " + s.island());
	}
}
