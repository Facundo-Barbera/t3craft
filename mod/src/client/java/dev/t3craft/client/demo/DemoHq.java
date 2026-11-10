package dev.t3craft.client.demo;

import com.google.gson.JsonObject;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.Placement;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.hq.HqBuild;
import dev.t3craft.hq.HqDiff;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.plan.Box;
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
	/**
	 * Bump when anything here changes the built result (plinth, lift rule): hashes change, the HQ is
	 * rebuilt. 3: vacant desks are no longer built (fix/hq-stale-stations); the rebuild clears them.
	 * 4: HQ redesign (HqStyle, cherry campus by default; standing desks, archive terminal nook,
	 * reserved screens); the fixtures marker also carries the kit and the style ({@code HqPlans.buildKey}).
	 */
	public static final int PLAN_VERSION = 4;

	private DemoHq() {
	}

	/** The roster the demo builds: the live threads, or {@link #sample()} when none is connected. */
	public record Roster(HqInput input, boolean sample) {
	}

	/**
	 * What to build for a roster against what the marker says is already there.
	 *
	 * @param island     feat/hq-void-world: the HQ island's core area after this step (void worlds), else null
	 * @param islandPart that island as a builders' part (already the first of {@code parts} when it changed), else null
	 */
	public record Step(HqLayout layout, dev.t3craft.hq.plan.BuildPlan plan, String hash, int lift, List<BuildPlan> parts, boolean full, String fixtures,
			VoidIsland.@Nullable Area island, @Nullable BuildPlan islandPart) {
		public Step {
			parts = List.copyOf(parts);
		}

		public Step(HqLayout layout, dev.t3craft.hq.plan.BuildPlan plan, String hash, int lift, List<BuildPlan> parts, boolean full, String fixtures) {
			this(layout, plan, hash, lift, parts, full, fixtures, null, null);
		}

		/** Everything this step's HQ is made of, from scratch: the island (void worlds), then {@link DemoHq#full}. */
		public List<BuildPlan> everything() {
			List<BuildPlan> out = new ArrayList<>();
			if (islandPart != null && !islandPart.isEmpty()) {
				out.add(islandPart);
			}
			out.addAll(DemoHq.full(plan, lift)); // qualified: the record component full() hides the outer method
			return out;
		}

		/** Nothing to build: this exact layout is already in the world. */
		public boolean upToDate() {
			return parts.isEmpty();
		}
	}

	/**
	 * feat/hq-void-world: the HQ stands on a floating island (void world).
	 *
	 * @param previous the island's core area already built (marker state), or null
	 * @param starter  the starter island (demo-relative), or null when none was placed
	 */
	public record Island(VoidIsland.@Nullable Area previous, VoidIsland.@Nullable Starter starter) {
	}

	/** Marker key of the island's core area (void worlds). */
	public static final String ISLAND = "island";

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
			// feat/lead-office: a lead works from its own office (a module of its own project, lead:<npcId>)
			String[] p = dev.t3craft.client.hq.lead.LeadOfficeClient.project(n.id(), g, n.groupLabel(), n.settled());
			b.thread(p[0], p[1], env, n.envLabel(), n.id(), n.settled());
		}
		return b.build();
	}

	/** Fixed example: three projects in two environments, four threads each, one of them settled (on the "Archived" shelf). */
	public static HqInput sample() {
		String[][] projects = {{"vps", "t3craft"}, {"laptop", "forja"}, {"vps", "telar"}};
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

	/** Marker key of the layout pack hash the HQ was built with (feat/hq-layout-packs; absent or "" = none). */
	public static final String PACK = "pack";

	/** Marker key of the fixtures the HQ was built with ({@link HqFixtures#fingerprint()}). */
	public static final String FIXTURES = "fixtures";

	/**
	 * Stable hash of what the demo puts in the world: plan version, lift, the allocated layout and the
	 * active {@link HqFixtures} (so turning the blocks module on or off rebuilds the fixtures).
	 */
	public static String hash(HqLayout layout, int lift) {
		return hash(layout, lift, dev.t3craft.hq.HqPlans.buildKey());
	}

	static String hash(HqLayout layout, int lift, String fixtures) {
		// feat/hq-layout-packs: the active layout's pack is part of what is built (absent without a pack: old hashes stay valid)
		String pack = dev.t3craft.hq.pack.HqLayoutPacks.hash(dev.t3craft.hq.layout.HqLayouts.active().id());
		String text = PLAN_VERSION + "|" + lift + "|" + fixtures + "|" + layout.toJson() + (pack.isEmpty() ? "" : "|pack=" + pack);
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
		// feat/hq-layouts: the layout this HQ is built with (HqLayoutState); never mixed with another one
		o.addProperty(dev.t3craft.client.hq.layout.HqLayoutState.KEY, dev.t3craft.hq.layout.HqLayouts.active().id());
		// feat/hq-layout-packs: the pack this HQ was built with, to regenerate the old plan for the next diff
		o.addProperty(PACK, step.plan().packHash());
		o.add("layout", step.layout().toJson());
		VoidIsland.Area a = step.island();
		if (a != null) {
			com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
			for (int v : a.toArray()) {
				arr.add(v);
			}
			o.add(ISLAND, arr);
		}
		return o;
	}

	/** The island area stored in a marker state, or null (flat world, older marker, unreadable). */
	public static VoidIsland.@Nullable Area island(@Nullable JsonObject state) {
		try {
			if (state == null || !state.has(ISLAND) || !state.get(ISLAND).isJsonArray()) {
				return null;
			}
			com.google.gson.JsonArray arr = state.getAsJsonArray(ISLAND);
			int[] v = new int[arr.size()];
			for (int i = 0; i < v.length; i++) {
				v[i] = arr.get(i).getAsInt();
			}
			return VoidIsland.Area.fromArray(v);
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ---- void island ----------------------------------------------------------------------------

	/** The plan's footprint in demo-relative columns (baked with {@link #bakeSite}). */
	public static VoidIsland.Area demoArea(dev.t3craft.hq.plan.BuildPlan plan, int lift) {
		Box f = plan.footprint();
		HqSite site = bakeSite(lift);
		BlockPos a = site.cell(new BlockPos(f.minX(), f.minY(), f.minZ()));
		BlockPos b = site.cell(new BlockPos(f.maxX(), f.maxY(), f.maxZ()));
		return new VoidIsland.Area(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
				Math.max(a.getZ(), b.getZ()));
	}

	/** Every cell the plan (and its plinth) takes, demo-relative. */
	static VoidIsland.Taken taken(dev.t3craft.hq.plan.BuildPlan plan, int lift) {
		HqSite site = bakeSite(lift);
		List<BlockPos> cells = new ArrayList<>(plan.size());
		for (dev.t3craft.hq.plan.Placement p : plan.placements()) {
			cells.add(site.cell(p.rel()));
		}
		return VoidIsland.Taken.of(cells);
	}

	/** The island under {@code plan} grown from {@code island.previous()}: its new area and the part that builds it. */
	static Step withIsland(Step step, Island island) {
		VoidIsland.Area area = demoArea(step.plan(), step.lift()).union(island.previous());
		BuildPlan part = VoidIsland.island(area, taken(step.plan(), step.lift()), island.starter(), island.previous()).named("island");
		List<BuildPlan> parts = new ArrayList<>();
		boolean grows = island.previous() == null || !island.previous().equals(area);
		if (!step.parts().isEmpty() && (grows || step.full()) && !part.isEmpty()) {
			parts.add(part); // the ground first: nothing of the HQ is ever placed over the void
		}
		parts.addAll(step.parts());
		return new Step(step.layout(), step.plan(), step.hash(), step.lift(), parts, step.full(), step.fixtures(), area, part);
	}

	/** Blocks to raise the HQ so its lowest cell ({@code planMinY}, the floor layer) sits above the bedrock layer. */
	public static int lift(int originY, int worldMinY, int planMinY) {
		return Math.max(0, worldMinY + 1 - (originY + planMinY));
	}

	// ---- step ---------------------------------------------------------------------------------

	/** {@link #step(HqInput, HqLayout, String, int, boolean, String)} for a world built with the current fixtures. */
	public static Step step(HqInput input, @Nullable HqLayout previous, @Nullable String previousHash, int lift, boolean diffable) {
		return step(input, previous, previousHash, lift, diffable, dev.t3craft.hq.HqPlans.buildKey());
	}

	/** {@link #step(HqInput, HqLayout, String, int, boolean, String, boolean)} without clearing a non-diffable previous HQ. */
	public static Step step(HqInput input, @Nullable HqLayout previous, @Nullable String previousHash, int lift, boolean diffable,
			String previousFixtures) {
		return step(input, previous, previousHash, lift, diffable, previousFixtures, false);
	}

	/**
	 * @param previous         layout already built (null = nothing); also reused for stable desk allocation
	 * @param previousHash     hash stored with it
	 * @param diffable         the world holds {@code previous} built with this plan version and lift, so a diff is valid
	 * @param previousFixtures {@link HqFixtures#fingerprint()} it was built with ({@code "vanilla"} for markers
	 *                         written before fixtures were recorded: the demo never applied them then)
	 * @param clearPrevious    when the world holds {@code previous} at the same lift but cannot be diffed
	 *                         (older plan version, unknown fixtures), the full rebuild also clears the cells
	 *                         of {@code previous} the new HQ no longer uses, so no module or desk of an old
	 *                         session is left standing (fix/hq-stale-stations)
	 *
	 * <p>The diff goes through {@link HqDiff}: removals ({@code remove:<group>} parts: modules and desks
	 * that were released, restored to air or ground, plus the plinth under a shrunken footprint) come
	 * first, then the plinth, then the builds.
	 */
	public static Step step(HqInput input, @Nullable HqLayout previous, @Nullable String previousHash, int lift, boolean diffable,
			String previousFixtures, boolean clearPrevious) {
		return step(input, previous, previousHash, lift, diffable, previousFixtures, clearPrevious, null);
	}

	/**
	 * {@link #step(HqInput, HqLayout, String, int, boolean, String, boolean)} for a world built with layout
	 * pack {@code previousPack} (feat/hq-layout-packs; null = the active one): the old plan is regenerated
	 * with that pack so the diff holds exactly what the pack change moved; a pack not seen this session
	 * cannot be regenerated, so the HQ is rebuilt whole.
	 */
	public static Step step(HqInput input, @Nullable HqLayout previous, @Nullable String previousHash, int lift, boolean diffable,
			String previousFixtures, boolean clearPrevious, @Nullable String previousPack) {
		return step(input, previous, previousHash, lift, diffable, previousFixtures, clearPrevious, previousPack, null);
	}

	/**
	 * feat/hq-void-world: {@link #step(HqInput, HqLayout, String, int, boolean, String, boolean, String)} in a
	 * void world ({@code island} non-null): the island under the footprint goes first whenever it grows
	 * (and with every full build), so the builders lay the ground before anything stands on it. Void
	 * hashes carry a {@code -void} suffix, so a void HQ is never mistaken for a flat one.
	 */
	public static Step step(HqInput input, @Nullable HqLayout previous, @Nullable String previousHash, int lift, boolean diffable,
			String previousFixtures, boolean clearPrevious, @Nullable String previousPack, @Nullable Island island) {
		Step s = stepFlat(input, previous, previousHash, lift, diffable, previousFixtures, clearPrevious, previousPack, island != null);
		return island == null ? s : withIsland(s, island);
	}

	private static Step stepFlat(HqInput input, @Nullable HqLayout previous, @Nullable String previousHash, int lift, boolean diffable,
			String previousFixtures, boolean clearPrevious, @Nullable String previousPack, boolean voidWorld) {
		String fixtures = dev.t3craft.hq.HqPlans.buildKey();
		HqGenerator.Result r = HqPlans.plan(input, previous);
		String h = hash(r.layout(), lift, fixtures) + (voidWorld ? "-void" : "");
		if (diffable && previous != null && h.equals(previousHash)) {
			return new Step(r.layout(), r.plan(), h, lift, List.of(), false, fixtures);
		}
		dev.t3craft.hq.plan.BuildPlan before = null;
		if (diffable && previous != null) {
			String layoutId = dev.t3craft.hq.layout.HqLayouts.active().id();
			String pack = previousPack == null ? dev.t3craft.hq.pack.HqLayoutPacks.hash(layoutId) : previousPack;
			HqLayout prev = previous;
			if (previousFixtures.equals(fixtures)) {
				before = dev.t3craft.hq.pack.HqLayoutPacks.withPack(layoutId, pack, () -> HqPlans.generate(prev));
			} else if (previousFixtures.equals(dev.t3craft.hq.HqPlans.buildKey(HqFixtures.VANILLA))) {
				before = dev.t3craft.hq.pack.HqLayoutPacks.withPack(layoutId, pack, () -> HqPlans.generate(prev, HqFixtures.VANILLA));
			}
			// else: built with fixtures that are not installed now, so the old plan is unknown: rebuild all
		}
		if (before == null) {
			List<BuildPlan> parts = new ArrayList<>();
			if (previous != null && clearPrevious) {
				// Only cell positions matter for the removals, so the fixtures the old HQ used do not.
				dev.t3craft.hq.plan.BuildPlan old = HqPlans.generate(previous);
				addIfAny(parts, plinthRemoval(old, r.plan(), lift));
				for (List<dev.t3craft.hq.plan.Placement> ps : HqDiff.between(old, r.plan(), vacated(lift, r.plan().footprint())).removals().values()) {
					addIfAny(parts, bake(HqBuild.toBuildPlan(ps, HqBuild.Order.MODULES), lift));
				}
			}
			parts.addAll(full(r.plan(), lift));
			return new Step(r.layout(), r.plan(), h, lift, parts, true, fixtures);
		}
		HqDiff diff = HqDiff.between(before, r.plan(), vacated(lift, r.plan().footprint()));
		boolean reshaped = !before.footprint().equals(r.plan().footprint());
		List<BuildPlan> parts = new ArrayList<>();
		Map<String, BuildPlan> diffParts = diff.parts();
		// 1. removals (released modules and desks), 2. the plinth follows the footprint, 3. builds.
		diffParts.forEach((g, p) -> {
			if (g.startsWith(HqDiff.REMOVE_PREFIX)) {
				addIfAny(parts, bake(p, lift));
			}
		});
		if (reshaped) {
			addIfAny(parts, plinthRemoval(before, r.plan(), lift));
			addIfAny(parts, plinth(r.plan(), lift).named("plinth"));
		}
		diffParts.forEach((g, p) -> {
			if (!g.startsWith(HqDiff.REMOVE_PREFIX)) {
				addIfAny(parts, bake(p, lift).named(g));
			}
		});
		return new Step(r.layout(), r.plan(), h, lift, parts, false, fixtures);
	}

	/** Plinth first, then every group of the HQ in build order, all baked into demo coordinates. */
	public static List<BuildPlan> full(dev.t3craft.hq.plan.BuildPlan plan, int lift) {
		List<BuildPlan> out = new ArrayList<>();
		// feat/builder-sites: every part keeps the name of its group, so the merged plan can tell the building sites apart
		addIfAny(out, plinth(plan, lift).named("plinth"));
		for (Map.Entry<String, BuildPlan> e : HqPlans.byGroup(plan).entrySet()) {
			addIfAny(out, bake(e.getValue(), lift).named(e.getKey()));
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

	/** HQ-relative cells to world cells, for {@link dev.t3craft.client.hq.HqAnchorSource}. */
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
		BuildPlan baked = BuildPlan.of(out);
		// Removal parts keep their top-down order (decorations before what holds them); the rest is layered.
		return isRemoval(hqRelative) ? baked : baked.layered();
	}

	/** A removal part: every placement is air (see {@link HqDiff#parts()}); kept in its own order. */
	private static boolean isRemoval(BuildPlan p) {
		for (Placement pl : p.placements()) {
			if (!pl.state().isAir()) {
				return false;
			}
		}
		return !p.isEmpty();
	}

	/** Not a constant: touching {@code Blocks} must wait for the vanilla bootstrap. */
	private static BlockState plinthBlock() {
		return Blocks.TUFF.defaultBlockState();
	}

	/** Tuff under the HQ's footprint between the ground and the hub floor, where the plan has no cell. */
	static BuildPlan plinth(dev.t3craft.hq.plan.BuildPlan plan, int lift) {
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
	static dev.t3craft.hq.plan.BuildPlan.Vacated vacated(int lift) {
		return rel -> {
			int y = rel.getY() + lift; // relative to the demo origin; the ground is y = -1
			if (y >= 0) {
				return y < lift ? plinthBlock() : Blocks.AIR.defaultBlockState();
			}
			return y == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		};
	}

	/** {@link #vacated(int)}, but plinth cells outside the new HQ's {@code footprint} become air (the plinth shrinks too). */
	static dev.t3craft.hq.plan.BuildPlan.Vacated vacated(int lift, Box footprint) {
		dev.t3craft.hq.plan.BuildPlan.Vacated inner = vacated(lift);
		return rel -> {
			int y = rel.getY() + lift;
			boolean under = rel.getX() >= footprint.minX() && rel.getX() <= footprint.maxX() && rel.getZ() >= footprint.minZ()
					&& rel.getZ() <= footprint.maxZ();
			if (y >= 0 && y < lift && !under) {
				return Blocks.AIR.defaultBlockState();
			}
			return inner.at(rel);
		};
	}

	/** Air where the old plinth stood outside the new footprint (cells the old plan had no block in). */
	static BuildPlan plinthRemoval(dev.t3craft.hq.plan.BuildPlan before, dev.t3craft.hq.plan.BuildPlan after, int lift) {
		List<Placement> out = new ArrayList<>();
		Box f = before.footprint();
		Box g = after.footprint();
		HqSite site = bakeSite(lift);
		for (int y = -1; y >= -lift; y--) {
			for (int x = f.minX(); x <= f.maxX(); x++) {
				for (int z = f.minZ(); z <= f.maxZ(); z++) {
					boolean under = x >= g.minX() && x <= g.maxX() && z >= g.minZ() && z <= g.maxZ();
					BlockPos rel = new BlockPos(x, y, z);
					if (!under && before.at(rel) == null) {
						out.add(new Placement(site.cell(rel), Blocks.AIR.defaultBlockState()));
					}
				}
			}
		}
		return BuildPlan.of(out);
	}
}
