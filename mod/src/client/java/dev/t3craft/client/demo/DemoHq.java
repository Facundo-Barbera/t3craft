package dev.t3craft.client.demo;

import com.google.gson.JsonObject;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.Placement;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.hq.Box;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqSite;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Pure logic of the demo's real HQ (feat/hq-design): which roster, the layout hash that makes the
 * build idempotent, and what to build (everything, only the diff, or nothing). No Minecraft client
 * state here, so it is unit tested; {@link DemoHqSource} plugs it into the demo.
 *
 * <p>Coordinates: the HQ plans are relative to the HQ origin (hub floor centre, {@code y = 0} the
 * lowest layer). The demo bakes {@link HqPlans#demoSite()} into them (entrance north, portico front
 * on {@code z = 0}, growth to {@code +z}), lifted by {@code lift} blocks so the floor stays above
 * bedrock in a shallow superflat world, plus a tuff plinth that fills the gap between the ground
 * ({@code y = -1} relative to the demo origin) and the hub floor. Built at the demo origin with
 * rotation {@code NONE}.
 */
public final class DemoHq {
	/** Bump when anything here changes the built result (plinth, lift rule): hashes change, the HQ is rebuilt. */
	public static final int PLAN_VERSION = 2;

	private DemoHq() {
	}

	/** The roster the demo builds: the live threads, or {@link #sample()} when none is connected. */
	public record Roster(HqInput input, boolean sample) {
	}

	/** What to build for a roster against what the marker says is already there. */
	public record Step(HqLayout layout, dev.t3craft.hq.BuildPlan plan, String hash, int lift, List<BuildPlan> parts, boolean full, String fixtures) {
		public Step {
			parts = List.copyOf(parts);
		}

		/** Nothing to build: this exact layout is already in the world. */
		public boolean upToDate() {
			return parts.isEmpty();
		}
	}

	// ---- roster -------------------------------------------------------------------------------

	public static Roster chooseRoster(Collection<NpcInfo> live) {
		return chooseRoster(live, dev.t3craft.client.hq.HqRooms.current(), System.currentTimeMillis());
	}

	/**
	 * fix/hq-rooms-real: only projects with real activity within the window get a room
	 * ({@link dev.t3craft.client.hq.HqRooms}), and the example roster is used <b>only</b> when no
	 * environment is paired. With environments paired an empty roster is an empty HQ (hub only),
	 * never the fixed example (its "telar (vps)" room was showing up in real HQs).
	 */
	public static Roster chooseRoster(Collection<NpcInfo> live, dev.t3craft.client.hq.HqRooms.Context rooms, long nowMs) {
		HqInput in = fromNpcs(dev.t3craft.client.hq.HqRooms.filter(live, rooms, nowMs));
		if (in.projects().isEmpty() && !rooms.paired()) {
			return new Roster(sample(), true);
		}
		return new Roster(in, false);
	}

	/** Every NPC shown (all environments), grouped by neighbourhood ({@code groupId} = env:project). */
	public static HqInput fromNpcs(Collection<NpcInfo> npcs) {
		HqInput.Builder b = HqInput.builder();
		for (NpcInfo n : npcs) {
			String g = n.groupId();
			int colon = g.indexOf(':');
			String env = colon > 0 ? g.substring(0, colon) : g;
			b.thread(g, n.groupLabel(), env, n.envLabel(), n.id(), n.settled());
		}
		return b.build();
	}

	/** Fixed example: three projects in two environments, four threads each, one of them settled (on the "Archived" shelf). */
	public static HqInput sample() {
		String[][] projects = {{"vps", "t3craft"}, {"acmesite", "forja"}, {"vps", "telar"}};
		HqInput.Builder b = HqInput.builder();
		for (String[] p : projects) {
			String key = p[0] + ":" + p[1];
			b.project(key, p[1], p[0], p[0]);
			for (int t = 0; t < 4; t++) {
				b.thread(key, p[1], p[0], p[0], "demo-" + p[1] + "-" + t, t == 3 && p[1].equals("t3craft"));
			}
		}
		return b.build();
	}

	// ---- hash / state -------------------------------------------------------------------------

	/** Marker key of the fixtures the HQ was built with ({@link HqFixtures#fingerprint()}). */
	public static final String FIXTURES = "fixtures";

	/**
	 * Stable hash of what the demo puts in the world: plan version, lift, the allocated layout and the
	 * active {@link HqFixtures} (so turning the blocks module on or off rebuilds the fixtures).
	 */
	public static String hash(HqLayout layout, int lift) {
		return hash(layout, lift, HqFixtures.fingerprint());
	}

	static String hash(HqLayout layout, int lift, String fixtures) {
		String text = PLAN_VERSION + "|" + lift + "|" + fixtures + "|" + layout.toJson();
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(d, 0, 12);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Marker state: what is needed to compute the next diff ({@code lift} + the layout). */
	public static JsonObject state(Step step) {
		JsonObject o = new JsonObject();
		o.addProperty("lift", step.lift());
		o.addProperty(FIXTURES, step.fixtures());
		o.add("layout", step.layout().toJson());
		return o;
	}

	/** Blocks to raise the HQ so its lowest cell ({@code planMinY}, the floor layer) sits above the bedrock layer. */
	public static int lift(int originY, int worldMinY, int planMinY) {
		return Math.max(0, worldMinY + 1 - (originY + planMinY));
	}

	// ---- step ---------------------------------------------------------------------------------

	/** {@link #step(HqInput, HqLayout, String, int, boolean, String)} for a world built with the current fixtures. */
	public static Step step(HqInput input, @Nullable HqLayout previous, @Nullable String previousHash, int lift, boolean diffable) {
		return step(input, previous, previousHash, lift, diffable, HqFixtures.fingerprint());
	}

	/**
	 * @param previous         layout already built (null = nothing); also reused for stable desk allocation
	 * @param previousHash     hash stored with it
	 * @param diffable         the world holds {@code previous} built with this plan version and lift, so a diff is valid
	 * @param previousFixtures {@link HqFixtures#fingerprint()} it was built with ({@code "vanilla"} for markers
	 *                         written before fixtures were recorded: the demo never applied them then)
	 */
	public static Step step(HqInput input, @Nullable HqLayout previous, @Nullable String previousHash, int lift, boolean diffable,
			String previousFixtures) {
		String fixtures = HqFixtures.fingerprint();
		HqGenerator.Result r = HqPlans.plan(input, previous);
		String h = hash(r.layout(), lift, fixtures);
		if (diffable && previous != null && h.equals(previousHash)) {
			return new Step(r.layout(), r.plan(), h, lift, List.of(), false, fixtures);
		}
		dev.t3craft.hq.BuildPlan before = null;
		if (diffable && previous != null) {
			if (previousFixtures.equals(fixtures)) {
				before = HqPlans.generate(previous);
			} else if (previousFixtures.equals(HqFixtures.fingerprint(HqFixtures.VANILLA))) {
				before = HqPlans.generate(previous, HqFixtures.VANILLA);
			}
			// else: built with fixtures that are not installed now, so the old plan is unknown: rebuild all
		}
		if (before == null) {
			return new Step(r.layout(), r.plan(), h, lift, full(r.plan(), lift), true, fixtures);
		}
		List<BuildPlan> parts = new ArrayList<>();
		if (!before.footprint().equals(r.plan().footprint())) {
			addIfAny(parts, plinth(r.plan(), lift));
		}
		for (BuildPlan p : HqPlans.expansion(before, r.plan(), vacated(lift)).values()) {
			addIfAny(parts, bake(p, lift));
		}
		return new Step(r.layout(), r.plan(), h, lift, parts, false, fixtures);
	}

	/** Plinth first, then every group of the HQ in build order, all baked into demo coordinates. */
	public static List<BuildPlan> full(dev.t3craft.hq.BuildPlan plan, int lift) {
		List<BuildPlan> out = new ArrayList<>();
		addIfAny(out, plinth(plan, lift));
		for (Map.Entry<String, BuildPlan> e : HqPlans.byGroup(plan).entrySet()) {
			addIfAny(out, bake(e.getValue(), lift));
		}
		return out;
	}

	private static void addIfAny(List<BuildPlan> out, BuildPlan p) {
		if (!p.isEmpty()) {
			out.add(p);
		}
	}

	// ---- coordinates --------------------------------------------------------------------------

	/** HQ-relative cells to demo-relative cells (relative to the demo origin). */
	public static HqSite bakeSite(int lift) {
		HqSite d = HqPlans.demoSite();
		return new HqSite(d.origin().above(lift), d.rotation());
	}

	/** HQ-relative cells to world cells, for {@link dev.t3craft.hq.HqAnchorSource}. */
	public static HqSite worldSite(BlockPos demoOrigin, int lift) {
		HqSite b = bakeSite(lift);
		return new HqSite(demoOrigin.offset(b.origin()), b.rotation());
	}

	/** An HQ-relative builders' plan with {@link #bakeSite} applied (positions moved, states rotated), layered. */
	static BuildPlan bake(BuildPlan hqRelative, int lift) {
		HqSite site = bakeSite(lift);
		List<Placement> out = new ArrayList<>(hqRelative.size());
		for (Placement p : hqRelative.placements()) {
			out.add(new Placement(site.cell(p.pos()), p.state().rotate(site.rotation()), p.blockEntity()));
		}
		return BuildPlan.of(out).layered();
	}

	/** Not a constant: touching {@code Blocks} must wait for the vanilla bootstrap. */
	private static BlockState plinthBlock() {
		return Blocks.TUFF.defaultBlockState();
	}

	/** Tuff under the HQ's footprint between the ground and the hub floor, where the plan has no cell. */
	static BuildPlan plinth(dev.t3craft.hq.BuildPlan plan, int lift) {
		List<Placement> out = new ArrayList<>();
		Box f = plan.footprint();
		HqSite site = bakeSite(lift);
		for (int y = -lift; y < 0; y++) {
			for (int x = f.minX(); x <= f.maxX(); x++) {
				for (int z = f.minZ(); z <= f.maxZ(); z++) {
					BlockPos rel = new BlockPos(x, y, z);
					if (plan.at(rel) == null) {
						out.add(new Placement(site.cell(rel), plinthBlock()));
					}
				}
			}
		}
		return BuildPlan.of(out).layered();
	}

	/** What a cell the HQ no longer uses becomes (HQ-relative): air above the plinth, tuff in it, grass/dirt below. */
	static dev.t3craft.hq.BuildPlan.Vacated vacated(int lift) {
		return rel -> {
			int y = rel.getY() + lift; // relative to the demo origin; the ground is y = -1
			if (y >= 0) {
				return y < lift ? plinthBlock() : Blocks.AIR.defaultBlockState();
			}
			return y == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		};
	}
}
