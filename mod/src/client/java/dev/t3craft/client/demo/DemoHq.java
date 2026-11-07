package dev.t3craft.client.demo;

import dev.t3craft.T3Craft;
import dev.t3craft.build.BuildService;
import dev.t3craft.build.Builders;
import dev.t3craft.client.hq.HqPresence;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqBuild;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.construct.HqConstruction;
import dev.t3craft.hq.construct.HqStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * The modular HQ in the demo world (fixed origin, superflat). Registers {@link HqStructureSource}
 * (so the demo's builders put up the HQ of the current roster instead of the shed), then keeps it in
 * step with the roster with the animated builders ({@link HqPlans#expansion}: only what changed) and
 * puts NPCs and workstations into it ({@link HqPresence}).
 *
 * <p>Placement: entrance facing north, portico front on the demo origin's {@code z}, growth towards
 * {@code +z}; the hub floor stands {@link #LIFT} blocks above the demo origin so the archive basement
 * stays above bedrock in the default superflat world (grass at -61, bedrock at -64).
 */
public final class DemoHq {
	/** Bump when the demo HQ output changes. */
	public static final int VERSION = 1;
	public static final int LIFT = 2;
	private static final int CHECK_TICKS = 60;

	private static @Nullable Supplier<@Nullable HqInput> roster;
	private static int ticks;
	private static @Nullable Path loadedFor;
	private static HqLayout layout = HqLayout.EMPTY;
	private static @Nullable BuildPlan plan;

	private DemoHq() {
	}

	/** Once, after {@link DemoClient} initialised. {@code live} = the live roster or null when not ready. */
	public static void register(Supplier<@Nullable HqInput> live) {
		roster = live;
		DemoStructures s = DemoClient.structures();
		if (s != null) {
			s.register(new HqStructureSource(VERSION, DemoHq::firstBuild));
		}
	}

	/** Plan-relative placement (relative to the demo origin, rotation NONE). */
	public static HqSite relativeSite() {
		return new HqSite(new BlockPos(0, LIFT, HqPlans.demoSite().origin().getZ()), Rotation.CLOCKWISE_180);
	}

	static HqSite worldSite(DemoWorldSpec spec) {
		HqSite r = relativeSite();
		return new HqSite(r.origin().offset(spec.originX(), spec.originY(), spec.originZ()), r.rotation());
	}

	private static HqInput input() {
		Supplier<@Nullable HqInput> r = roster;
		HqInput in = r == null ? null : r.get();
		return in == null || in.projects().isEmpty() ? demoRoster() : in;
	}

	/** A small made-up roster for a demo opened before any T3 environment is connected. */
	static HqInput demoRoster() {
		HqInput.Builder b = HqInput.builder();
		String[][] p = {{"vps:t3craft", "t3craft", "vps"}, {"acmesite:forja", "forja", "acmesite"}};
		for (String[] x : p) {
			for (int t = 0; t < 4; t++) {
				b.thread(x[0], x[1], x[2], x[2], x[0] + "-t" + t, t == 3);
			}
		}
		return b.build();
	}

	/** The whole HQ for the demo's first build; remembers its layout in the demo world. */
	private static List<dev.t3craft.build.BuildPlan> firstBuild() {
		Path root = DemoClient.activeRoot();
		load(root);
		HqGenerator.Result r = HqGenerator.plan(input(), layout);
		save(root, r.layout(), r.plan());
		List<dev.t3craft.build.BuildPlan> out = new ArrayList<>();
		for (dev.t3craft.build.BuildPlan p : HqPlans.byGroup(r.plan()).values()) {
			out.add(bake(p.placements()));
		}
		return out;
	}

	private static dev.t3craft.build.BuildPlan bake(List<dev.t3craft.build.Placement> rel) {
		HqSite s = relativeSite();
		List<dev.t3craft.build.Placement> out = new ArrayList<>(rel.size());
		for (dev.t3craft.build.Placement p : rel) {
			out.add(new dev.t3craft.build.Placement(s.cell(p.pos()), p.state().rotate(s.rotation()), p.blockEntity()));
		}
		return dev.t3craft.build.BuildPlan.of(out).layered();
	}

	private static void load(@Nullable Path root) {
		if (root == null || root.equals(loadedFor)) {
			return;
		}
		loadedFor = root;
		layout = new HqStore(root, "demo.json").load().layout();
		plan = null;
	}

	private static void save(@Nullable Path root, HqLayout l, BuildPlan p) {
		layout = l;
		plan = p;
		if (root == null) {
			return;
		}
		try {
			new HqStore(root, "demo.json").save(new HqConstruction.Saved("demo", l, false));
		} catch (IOException e) {
			T3Craft.LOGGER.warn("[demo] could not save the HQ layout: {}", e.getMessage());
		}
	}

	/** Client thread, every tick. */
	public static void tick(Minecraft mc) {
		if (++ticks % CHECK_TICKS != 0) {
			return;
		}
		DemoWorldSpec spec = DemoClient.activeSpec();
		Path root = DemoClient.activeRoot();
		if (spec == null || root == null || mc.player == null || mc.level == null) {
			loadedFor = null;
			HqPresence.demo(null);
			return;
		}
		load(root);
		if (!HqStructureSource.ID.equals(DemoMarkerFile.read(root).structure()) && plan == null) {
			HqPresence.demo(null);
			return;
		}
		if (plan == null) {
			plan = new HqGenerator().generate(layout);
		}
		HqSite site = worldSite(spec);
		HqPresence.demo(new HqPresence.Standing(plan, site, null));
		BuildService svc = Builders.service();
		Supplier<@Nullable HqInput> r = roster;
		HqInput live = r == null ? null : r.get();
		if (live == null || svc == null || !svc.available() || !svc.active().isEmpty()) {
			return;
		}
		HqGenerator.Result next = HqGenerator.plan(live, layout);
		if (next.plan().fingerprint().equals(plan.fingerprint())) {
			return;
		}
		List<Placement> diff = BuildPlan.diff(plan, next.plan(), HqBuild.AIR_ABOVE_GROUND);
		List<dev.t3craft.build.Placement> rel = new ArrayList<>(diff.size());
		for (Placement p : diff) {
			rel.add(new dev.t3craft.build.Placement(p.rel(), p.state(), p.be()));
		}
		try {
			svc.build("HQ", mc.level.dimension(), new BlockPos(spec.originX(), spec.originY(), spec.originZ()), Rotation.NONE, bake(rel), 8, 3);
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("[demo] HQ update not started: {}", e.getMessage());
			return;
		}
		save(root, next.layout(), next.plan());
		HqPresence.demo(new HqPresence.Standing(next.plan(), site, null));
	}
}
