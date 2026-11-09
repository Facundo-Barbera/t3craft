package dev.t3craft.tools.hqrender;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.t3craft.build.BuildCursor;
import dev.t3craft.build.CrewCursor;
import dev.t3craft.build.CrewSchedule;
import dev.t3craft.build.FrontCursor;
import dev.t3craft.build.JobState;
import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.Box;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqBuild;
import dev.t3craft.hq.HqLightMap;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.PlanProbe;
import dev.t3craft.hq.Workstation;
import dev.t3craft.hq.citadel.CitadelRelief;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Headless review of a {@link BuildPlan}, for whoever designs a piece of it: {@code report.txt} (to
 * read) and {@code report.json} (to parse) next to the PNGs. It is information, not a test: nothing
 * here fails a build. All of it runs without a world.
 *
 * <ul>
 *   <li><b>signature</b>: SHA-256 of every cell (position and block state, air included, sorted by
 *   position, so the order the generator wrote them in does not matter) for the whole plan, the scope
 *   and every group, plus the order-sensitive {@link BuildPlan#fingerprint()}. Equal signatures before
 *   and after a refactor mean the plan did not change, block by block.</li>
 *   <li><b>blocks</b>: count per block type, and the light sources (how many, how many are seen, how
 *   many are lamps in the floor under a walkable cell, and how many per 100 walkable cells).</li>
 *   <li><b>light</b>: walkable cells ({@link PlanProbe#reachable} from the spawn) whose block light
 *   ({@link HqLightMap}) is under {@link Options#lowLight}, with coordinates and clusters.</li>
 *   <li><b>anchors</b>: whether every standing anchor and every desk is reachable on foot from the spawn.</li>
 *   <li><b>shapes</b>: fences, walls, panes, bars and stairs whose state differs from what
 *   {@code PlanConnect} gives them among their real neighbours (a fence that should make a 2- or 3-sided corner and does
 *   not shows as {@code cornersMissing}).</li>
 *   <li><b>connect</b>: fences, walls and panes with no connection although a connectable neighbour is
 *   beside them (must be 0).</li>
 *   <li><b>floating</b>: groups of blocks touching nothing that touches the ground (y &lt;= 0), and
 *   <b>unsupported</b>: blocks vanilla's {@code canSurvive} refuses where they stand.</li>
 *   <li><b>build</b>: ticks the crew would take, simulated with {@link CrewCursor} (no world).</li>
 * </ul>
 *
 * With a {@code scope} box only the blocks inside it are counted and listed (the signature of the
 * whole plan is kept, plus one of the scope); reachability and the shape/floating checks still look
 * at the whole plan, so a piece is judged among its neighbours.
 */
public final class PlanReport {
	/** Knobs of {@link #analyze}. */
	public static final class Options {
		/** Walkable cells under this block light are listed. */
		public int lowLight = HqLightMap.MIN_LEVEL;
		/** Simulate the build (the slowest part of a big plan). */
		public boolean ticks = true;
		/** Builders in the crew. */
		public int crew = 4;
		/** Blocks per tick the crew may place (the game's global budget, {@code build.maxBlocksPerTick}). */
		public double blocksPerTick = 48;
		/** Plans with more entries than this skip the build simulation. */
		public int ticksLimit = 600_000;
		/** Lines per list in the text report (the JSON keeps more). */
		public int maxList = 40;
		/** Shown in the header. */
		public String label = "";

		/** A copy, so a caller can keep one set of options for many reports. */
		public Options copy() {
			Options c = new Options();
			c.lowLight = lowLight;
			c.ticks = ticks;
			c.crew = crew;
			c.blocksPerTick = blocksPerTick;
			c.ticksLimit = ticksLimit;
			c.maxList = maxList;
			c.label = label;
			return c;
		}
	}

	private static final int JSON_LIST = 2000;

	private final @org.jspecify.annotations.Nullable Box scope;
	private final JsonObject json = new JsonObject();
	private final List<String> sections = new ArrayList<>();
	private final Map<String, String> summary = new LinkedHashMap<>();
	private String header = "";
	private String cellsSignature = "";
	private String scopeSignature = "";

	private PlanReport(@org.jspecify.annotations.Nullable Box scope) {
		this.scope = scope;
	}

	/** Signature of every cell of the whole plan (hex SHA-256). */
	public String cellsSignature() {
		return cellsSignature;
	}

	/** Signature of the cells inside the scope ("" when the report covers the whole plan). */
	public String scopeSignature() {
		return scopeSignature;
	}

	public JsonObject toJson() {
		return json;
	}

	public String toText() {
		StringBuilder sb = new StringBuilder(header);
		sb.append("\nSUMMARY\n");
		for (Map.Entry<String, String> e : summary.entrySet()) {
			sb.append(String.format(Locale.ROOT, "  %-26s %s%n", e.getKey(), e.getValue()));
		}
		for (String s : sections) {
			sb.append('\n').append(s);
		}
		return sb.toString();
	}

	/** Writes {@code <base>.txt} and {@code <base>.json} into {@code dir}. */
	public List<Path> write(Path dir, String base) throws IOException {
		Files.createDirectories(dir);
		Path t = dir.resolve(base + ".txt");
		Path j = dir.resolve(base + ".json");
		Files.writeString(t, toText(), StandardCharsets.UTF_8);
		Files.writeString(j, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) + "\n", StandardCharsets.UTF_8);
		return List.of(t, j);
	}

	/** Analyses {@code plan}; {@code scope} null = the whole plan. */
	public static PlanReport analyze(BuildPlan plan, @org.jspecify.annotations.Nullable Box scope, Options o) {
		PlanReport r = new PlanReport(scope);
		r.run(plan, o);
		return r;
	}

	// ------------------------------------------------------------------ the run

	private interface Step {
		void run() throws Exception;
	}

	private void guarded(String name, Step s) {
		long t0 = System.nanoTime();
		try {
			s.run();
		} catch (Exception | StackOverflowError | OutOfMemoryError e) {
			String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
			sections.add(name.toUpperCase(Locale.ROOT) + "\n  could not be computed (" + msg + ")\n");
			json.addProperty(name + "Error", msg);
			summary.put(name, "error (" + msg + ")");
		}
		System.out.printf(Locale.ROOT, "  report %-10s %.1fs%n", name, (System.nanoTime() - t0) / 1e9);
	}

	private boolean in(BlockPos p) {
		return scope == null || scope.contains(p);
	}

	private void run(BuildPlan plan, Options o) {
		int inScope = 0;
		for (Placement p : plan.placements()) {
			if (in(p.rel())) {
				inScope++;
			}
		}
		StringBuilder h = new StringBuilder("HQ PLAN REPORT\n");
		if (!o.label.isEmpty()) {
			h.append("plan:       ").append(o.label).append('\n');
		}
		h.append("scope:      ").append(scope == null ? "whole plan" : scope.toString()).append('\n');
		h.append("placements: ").append(plan.size()).append(" in the plan, ").append(inScope).append(" in scope\n");
		h.append("modules:    ").append(plan.modules().size()).append(", anchors ").append(plan.anchors().size()).append(", desks ")
				.append(plan.workstations().size()).append('\n');
		header = h.toString();
		json.addProperty("label", o.label);
		json.addProperty("scope", scope == null ? "whole" : scope.toString());
		if (scope != null) {
			JsonArray b = new JsonArray();
			for (int v : new int[] {scope.minX(), scope.minY(), scope.minZ(), scope.maxX(), scope.maxY(), scope.maxZ()}) {
				b.add(v);
			}
			json.add("scopeBox", b);
		}
		json.addProperty("placements", plan.size());
		json.addProperty("placementsInScope", inScope);

		guarded("signature", () -> signature(plan));
		guarded("blocks", () -> blocks(plan, o));
		Set<BlockPos>[] reach = newReach();
		guarded("walk", () -> reach[0] = walk(plan));
		guarded("light", () -> light(plan, reach[0], o));
		guarded("anchors", () -> anchors(plan, reach[0], o));
		guarded("lamps", () -> lamps(plan, reach[0]));
		guarded("flights", () -> flights(plan, reach[0], o));
		guarded("shapes", () -> shapes(plan, o));
		guarded("connect", () -> connect(plan, o));
		guarded("floating", () -> floating(plan, o));
		guarded("unsupported", () -> unsupported(plan, o));
		guarded("split", () -> split(plan, o));
		if (o.ticks) {
			guarded("build", () -> build(plan, o));
		}
	}

	@SuppressWarnings("unchecked")
	private static Set<BlockPos>[] newReach() {
		Set<BlockPos>[] a = new Set[1];
		a[0] = Set.of();
		return a;
	}

	// ------------------------------------------------------------------ signature

	private static String line(Placement p) {
		BlockPos r = p.rel();
		return r.getX() + "," + r.getY() + "," + r.getZ() + "=" + p.state() + (p.be() == null ? "" : "+" + p.be()) + "\n";
	}

	private static MessageDigest sha() throws Exception {
		return MessageDigest.getInstance("SHA-256");
	}

	private void signature(BuildPlan plan) throws Exception {
		long[] keys = new long[plan.size()];
		int i = 0;
		for (Placement p : plan.placements()) {
			keys[i++] = p.rel().asLong();
		}
		Arrays.sort(keys);
		Map<BlockPos, String> groups = PlanProbe.groups(plan);
		MessageDigest all = sha();
		MessageDigest sc = scope == null ? null : sha();
		Map<String, MessageDigest> per = new TreeMap<>();
		Map<String, Integer> perCount = new TreeMap<>();
		for (long k : keys) {
			BlockPos pos = BlockPos.of(k);
			Placement p = plan.at(pos);
			byte[] b = line(p).getBytes(StandardCharsets.UTF_8);
			all.update(b);
			if (sc != null && scope.contains(pos)) {
				sc.update(b);
			}
			String g = groups.getOrDefault(pos, "(none)");
			MessageDigest gd = per.get(g);
			if (gd == null) {
				gd = sha();
				per.put(g, gd);
			}
			gd.update(b);
			perCount.merge(g, 1, Integer::sum);
		}
		HexFormat hex = HexFormat.of();
		cellsSignature = hex.formatHex(all.digest());
		scopeSignature = sc == null ? "" : hex.formatHex(sc.digest());
		JsonObject sig = new JsonObject();
		sig.addProperty("cells", cellsSignature);
		sig.addProperty("fingerprint", plan.fingerprint());
		sig.addProperty("cellCount", keys.length);
		if (sc != null) {
			sig.addProperty("scope", scopeSignature);
		}
		JsonObject gj = new JsonObject();
		StringBuilder sb = new StringBuilder("SIGNATURE (equal before/after a refactor = the plan did not change)\n");
		sb.append("  cells:       ").append(cellsSignature).append("  (").append(keys.length).append(" cells, order independent)\n");
		sb.append("  fingerprint: ").append(plan.fingerprint()).append("  (order, anchors and pack too)\n");
		if (sc != null) {
			sb.append("  scope:       ").append(scopeSignature).append('\n');
		}
		sb.append("  per group (first 16 hex):\n");
		for (Map.Entry<String, MessageDigest> e : per.entrySet()) {
			String d = hex.formatHex(e.getValue().digest()).substring(0, 16);
			gj.addProperty(e.getKey(), d);
			sb.append(String.format(Locale.ROOT, "    %-28s %s  %d cells%n", e.getKey(), d, perCount.get(e.getKey())));
		}
		sig.add("groups", gj);
		json.add("signature", sig);
		sections.add(sb.toString());
		summary.put("cells signature", cellsSignature.substring(0, 16) + "…");
	}

	// ------------------------------------------------------------------ blocks

	static String id(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
	}

	private void blocks(BuildPlan plan, Options o) {
		Map<String, Integer> count = new HashMap<>();
		int air = 0;
		int solid = 0;
		for (Placement p : plan.placements()) {
			if (!in(p.rel())) {
				continue;
			}
			if (p.state().isAir()) {
				air++;
				continue;
			}
			solid++;
			count.merge(id(p.state()), 1, Integer::sum);
		}
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(count.entrySet());
		sorted.sort((a, b) -> b.getValue() != a.getValue().intValue() ? Integer.compare(b.getValue(), a.getValue()) : a.getKey().compareTo(b.getKey()));
		JsonObject bj = new JsonObject();
		StringBuilder sb = new StringBuilder("BLOCKS (in scope)\n");
		sb.append("  non-air ").append(solid).append(", air cells ").append(air).append(", ").append(sorted.size()).append(" block types\n");
		int shown = 0;
		for (Map.Entry<String, Integer> e : sorted) {
			bj.addProperty(e.getKey(), e.getValue());
			if (shown++ < Math.max(o.maxList, 20)) {
				sb.append(String.format(Locale.ROOT, "    %8d  %s%n", e.getValue(), e.getKey()));
			}
		}
		if (sorted.size() > shown) {
			sb.append("    … ").append(sorted.size() - Math.max(o.maxList, 20)).append(" more types (report.json has them all)\n");
		}
		JsonObject j = new JsonObject();
		j.addProperty("nonAir", solid);
		j.addProperty("air", air);
		j.add("byType", bj);
		json.add("blocks", j);
		sections.add(sb.toString());
		summary.put("blocks", solid + " non-air, " + sorted.size() + " types");
	}

	// ------------------------------------------------------------------ walkable cells

	private BlockPos walkStart;

	private Set<BlockPos> walk(BuildPlan plan) {
		Anchor spawn = plan.anchor(AnchorNames.SPAWN);
		if (spawn == null) {
			sections.add("WALKABLE\n  no spawn anchor: reachability, light and lamps-in-the-floor are not computed\n");
			summary.put("walkable", "no spawn anchor");
			return Set.of();
		}
		for (int dy : new int[] {0, 1, -1, 2, -2}) {
			BlockPos s = spawn.block().above(dy);
			Set<BlockPos> r = PlanProbe.reachable(plan, s);
			if (!r.isEmpty()) {
				walkStart = s;
				int n = 0;
				for (BlockPos p : r) {
					if (in(p)) {
						n++;
					}
				}
				json.addProperty("walkableCells", r.size());
				json.addProperty("walkableCellsInScope", n);
				sections.add("WALKABLE\n  " + r.size() + " cells reachable on foot from the spawn " + c(s) + ", " + n + " in scope\n");
				summary.put("walkable cells", n + " in scope (" + r.size() + " in the plan)");
				return r;
			}
		}
		sections.add("WALKABLE\n  the spawn " + c(spawn.block()) + " is not standable: reachability, light and lamps-in-the-floor are not computed\n");
		summary.put("walkable", "spawn not standable");
		return Set.of();
	}

	private static String c(BlockPos p) {
		return "(" + p.getX() + "," + p.getY() + "," + p.getZ() + ")";
	}

	// ------------------------------------------------------------------ stair flights

	/**
	 * Flights of bottom-half stairs (3 or more steps of one facing, each one up and one along): how many are
	 * climbed on foot from the spawn, how many are only partly reached (a step that is a jump, or a head that hits
	 * a slab), and the ones nobody reaches although a roof is over them (an interior flight cut off). Roofs
	 * are in the open and are not listed. A stair is stood on from the cell above it ({@code PlanNav}).
	 */
	private void flights(BuildPlan plan, Set<BlockPos> reach, Options o) {
		if (reach == null || reach.isEmpty()) {
			return;
		}
		Map<Long, BlockState> stairs = new HashMap<>();
		for (Placement p : plan.placements()) {
			BlockState s = p.state();
			if (s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM) {
				stairs.put(p.rel().asLong(), s);
			}
		}
		Set<Long> seen = new HashSet<>();
		int total = 0;
		int climbed = 0;
		List<String> partial = new ArrayList<>();
		List<String> cut = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : stairs.entrySet()) {
			if (seen.contains(e.getKey())) {
				continue;
			}
			BlockPos start = BlockPos.of(e.getKey());
			Direction f = e.getValue().getValue(StairBlock.FACING);
			BlockState back = stairs.get(start.relative(f.getOpposite()).below().asLong());
			if (back != null && back.getValue(StairBlock.FACING) == f) {
				continue; // not the foot of the flight
			}
			List<BlockPos> chain = new ArrayList<>();
			BlockPos q = start;
			while (true) {
				chain.add(q);
				seen.add(q.asLong());
				BlockPos nx = q.relative(f).above();
				BlockState ns = stairs.get(nx.asLong());
				if (ns == null || ns.getValue(StairBlock.FACING) != f) {
					break;
				}
				q = nx;
			}
			if (chain.size() < 3) {
				continue;
			}
			int r = 0;
			int standable = 0;
			int covered = 0;
			for (BlockPos s : chain) {
				BlockPos stand = s.above();
				if (reach.contains(stand)) {
					r++;
				}
				if (PlanProbe.standable(plan, stand)) {
					standable++;
				}
				for (int dy = 2; dy <= 8; dy++) {
					Placement above = plan.at(stand.above(dy));
					if (above != null && !above.state().isAir() && !above.state().getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
						covered++;
						break;
					}
				}
			}
			String line = chain.size() + " steps " + f.getSerializedName() + " from " + c(chain.get(0)) + " to " + c(chain.get(chain.size() - 1)) + ", reached " + r + ", standable "
					+ standable;
			if (r == chain.size()) {
				total++;
				climbed++;
			} else if (r > 0) {
				total++;
				// what is in the way of the first step not reached: every block over the two stand cells
				StringBuilder why = new StringBuilder(" | in the way:");
				for (int k = 1; k < chain.size(); k++) {
					if (!reach.contains(chain.get(k).above())) {
						for (int kk = k - 1; kk <= k; kk++) {
							BlockPos stand = chain.get(kk).above();
							for (int dy = -1; dy <= 3; dy++) {
								Placement pl = plan.at(stand.above(dy));
								if (pl != null && !pl.state().isAir()
										&& (dy != -1 || kk == k)) {
									why.append(' ').append(c(stand.above(dy))).append('=')
											.append(BuiltInRegistries.BLOCK.getKey(pl.state().getBlock()).getPath());
								}
							}
						}
						break;
					}
				}
				partial.add(line + why);
			} else if (covered * 2 >= chain.size() && standable > 0) {
				total++;
				cut.add(line);
			}
		}
		StringBuilder sb = new StringBuilder("STAIR FLIGHTS (3+ steps; roofs left out)\n");
		sb.append("  ").append(climbed).append(" of ").append(total).append(" flights climbed on foot from the spawn\n");
		listLines(sb, partial, o.maxList, "partly reached");
		listLines(sb, cut, o.maxList, "under a roof, not reached");
		sections.add(sb.toString());
		summary.put("stair flights", climbed + " of " + total + " climbed; " + partial.size() + " partial, " + cut.size() + " cut off");
		JsonObject j = new JsonObject();
		j.addProperty("total", total);
		j.addProperty("climbed", climbed);
		JsonArray pj = new JsonArray();
		partial.forEach(pj::add);
		j.add("partial", pj);
		JsonArray cj = new JsonArray();
		cut.forEach(cj::add);
		j.add("cutOff", cj);
		json.add("stairFlights", j);
	}

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + "," + b[2] + " .. " + b[3] + "," + b[4] + "," + b[5] + "]";
	}

	private static JsonArray arr(int... v) {
		JsonArray a = new JsonArray();
		for (int x : v) {
			a.add(x);
		}
		return a;
	}

	// ------------------------------------------------------------------ light

	private void light(BuildPlan plan, Set<BlockPos> reach, Options o) {
		if (reach.isEmpty()) {
			return;
		}
		HqLightMap map = HqLightMap.of(plan);
		int[] hist = new int[16];
		List<BlockPos> dark = new ArrayList<>();
		Map<BlockPos, Integer> level = new HashMap<>();
		int walkable = 0;
		for (BlockPos p : reach) {
			if (!in(p)) {
				continue;
			}
			walkable++;
			int v = Math.max(0, Math.min(15, map.at(p)));
			hist[v]++;
			if (v < o.lowLight) {
				dark.add(p);
				level.put(p, v);
			}
		}
		dark.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY())
				: a.getZ() != b.getZ() ? Integer.compare(a.getZ(), b.getZ()) : Integer.compare(a.getX(), b.getX()));
		// clusters: dark cells touching (8 neighbours on the plane, one block up or down)
		Set<BlockPos> left = new HashSet<>(dark);
		List<int[]> clusters = new ArrayList<>(); // size, minLevel, bbox(6), sample(3)
		for (BlockPos start : dark) {
			if (!left.remove(start)) {
				continue;
			}
			ArrayDeque<BlockPos> q = new ArrayDeque<>();
			q.add(start);
			int size = 0;
			int min = 99;
			int[] bb = {start.getX(), start.getY(), start.getZ(), start.getX(), start.getY(), start.getZ()};
			while (!q.isEmpty()) {
				BlockPos p = q.poll();
				size++;
				min = Math.min(min, level.get(p));
				bb[0] = Math.min(bb[0], p.getX());
				bb[1] = Math.min(bb[1], p.getY());
				bb[2] = Math.min(bb[2], p.getZ());
				bb[3] = Math.max(bb[3], p.getX());
				bb[4] = Math.max(bb[4], p.getY());
				bb[5] = Math.max(bb[5], p.getZ());
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						for (int dy = -1; dy <= 1; dy++) {
							BlockPos n = p.offset(dx, dy, dz);
							if (left.remove(n)) {
								q.add(n);
							}
						}
					}
				}
			}
			clusters.add(new int[] {size, min, bb[0], bb[1], bb[2], bb[3], bb[4], bb[5], start.getX(), start.getY(), start.getZ()});
		}
		clusters.sort((a, b) -> Integer.compare(b[0], a[0]));
		StringBuilder sb = new StringBuilder("LIGHT (walkable cells with block light < " + o.lowLight + ")\n");
		sb.append("  ").append(dark.size()).append(" of ").append(walkable).append(" walkable cells in scope are dark, in ").append(clusters.size())
				.append(" clusters\n  light histogram of walkable cells (level: cells):");
		for (int l = 0; l < 16; l++) {
			if (hist[l] > 0) {
				sb.append(' ').append(l).append(':').append(hist[l]);
			}
		}
		sb.append('\n');
		JsonArray cj = new JsonArray();
		int shown = 0;
		for (int[] cl : clusters) {
			JsonObject e = new JsonObject();
			e.addProperty("cells", cl[0]);
			e.addProperty("minLight", cl[1]);
			e.add("box", arr(cl[2], cl[3], cl[4], cl[5], cl[6], cl[7]));
			e.add("first", arr(cl[8], cl[9], cl[10]));
			if (cj.size() < JSON_LIST) {
				cj.add(e);
			}
			if (shown++ < o.maxList) {
				sb.append(String.format(Locale.ROOT, "    %5d cells  min light %2d  box %s  first (%d,%d,%d)%n", cl[0], cl[1],
						box(new int[] {cl[2], cl[3], cl[4], cl[5], cl[6], cl[7]}), cl[8], cl[9], cl[10]));
			}
		}
		if (clusters.size() > shown) {
			sb.append("    … ").append(clusters.size() - shown).append(" more clusters\n");
		}
		JsonArray dj = new JsonArray();
		for (BlockPos p : dark) {
			if (dj.size() >= JSON_LIST) {
				break;
			}
			dj.add(arr(p.getX(), p.getY(), p.getZ(), level.get(p)));
		}
		JsonArray hj = new JsonArray();
		for (int v : hist) {
			hj.add(v);
		}
		JsonObject j = new JsonObject();
		j.addProperty("threshold", o.lowLight);
		j.addProperty("walkable", walkable);
		j.addProperty("darkCells", dark.size());
		j.add("histogram", hj);
		j.add("clusters", cj);
		j.add("cells", dj);
		json.add("light", j);
		sections.add(sb.toString());
		summary.put("dark walkable cells", dark.size() + " (< " + o.lowLight + "), " + clusters.size() + " clusters");
	}

	// ------------------------------------------------------------------ anchors

	/** "direct" (the cell is walkable), "adjacent" (a walkable cell touches it: seats, benches) or "no". */
	private static String reach(Set<BlockPos> reach, BlockPos cell) {
		if (reach.contains(cell)) {
			return "direct";
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int dy = -1; dy <= 1; dy++) {
				if (reach.contains(cell.relative(d).above(dy))) {
					return "adjacent";
				}
			}
		}
		return reach.contains(cell.above()) || reach.contains(cell.below()) ? "adjacent" : "no";
	}

	private void anchors(BuildPlan plan, Set<BlockPos> reach, Options o) {
		if (reach.isEmpty()) {
			return;
		}
		int standing = 0;
		int ok = 0;
		List<String> bad = new ArrayList<>();
		JsonArray badJ = new JsonArray();
		for (Map.Entry<String, Anchor> e : plan.anchors().entrySet()) {
			if (!AnchorNames.standing(e.getKey()) || !in(e.getValue().block())) {
				continue;
			}
			standing++;
			if (reach(reach, e.getValue().block()).equals("no")) {
				bad.add(e.getKey() + " " + c(e.getValue().block()));
				JsonObject b = new JsonObject();
				b.addProperty("anchor", e.getKey());
				b.add("cell", arr(e.getValue().block().getX(), e.getValue().block().getY(), e.getValue().block().getZ()));
				badJ.add(b);
			} else {
				ok++;
			}
		}
		int desks = 0;
		int desksOk = 0;
		List<String> badDesks = new ArrayList<>();
		JsonArray badDeskJ = new JsonArray();
		for (Workstation w : plan.workstations()) {
			BlockPos cell = w.deskAnchor().block();
			if (!in(cell)) {
				continue;
			}
			desks++;
			if (reach(reach, cell).equals("no")) {
				badDesks.add("module " + w.module() + " slot " + w.slot() + " (" + w.threadId() + ") " + c(cell));
				JsonObject b = new JsonObject();
				b.addProperty("module", w.module());
				b.addProperty("slot", w.slot());
				b.addProperty("thread", String.valueOf(w.threadId()));
				b.add("cell", arr(cell.getX(), cell.getY(), cell.getZ()));
				badDeskJ.add(b);
			} else {
				desksOk++;
			}
		}
		StringBuilder sb = new StringBuilder("ANCHORS (reachable on foot from the spawn)\n");
		sb.append("  standing anchors: ").append(ok).append(" of ").append(standing).append(" reachable\n");
		listLines(sb, bad, o.maxList, "unreachable");
		sb.append("  desks: ").append(desksOk).append(" of ").append(desks).append(" reachable\n");
		listLines(sb, badDesks, o.maxList, "unreachable desk");
		JsonObject j = new JsonObject();
		j.addProperty("standingAnchors", standing);
		j.addProperty("standingReachable", ok);
		j.add("unreachableAnchors", badJ);
		j.addProperty("desks", desks);
		j.addProperty("desksReachable", desksOk);
		j.add("unreachableDesks", badDeskJ);
		json.add("anchors", j);
		sections.add(sb.toString());
		summary.put("unreachable anchors", bad.size() + " of " + standing + "; desks " + badDesks.size() + " of " + desks);
	}

	private static void listLines(StringBuilder sb, List<String> lines, int max, String what) {
		int n = 0;
		for (String s : lines) {
			if (n++ >= max) {
				sb.append("    … ").append(lines.size() - max).append(" more ").append(what).append('\n');
				break;
			}
			sb.append("    ").append(what).append(": ").append(s).append('\n');
		}
	}

	// ------------------------------------------------------------------ lamps

	private static boolean open(BuildPlan plan, BlockPos p) {
		Placement q = plan.at(p);
		return q == null || q.state().isAir() || !q.state().canOcclude();
	}

	private void lamps(BuildPlan plan, Set<BlockPos> reach) {
		Map<String, int[]> byType = new TreeMap<>(); // total, seen, floor
		int total = 0;
		int seen = 0;
		int floor = 0;
		JsonArray floorJ = new JsonArray();
		for (Placement p : plan.placements()) {
			if (!in(p.rel()) || p.state().getLightEmission() <= 0) {
				continue;
			}
			BlockPos pos = p.rel();
			// minecraft:light is invisible: counted as an emitter, never as a seen lamp or a lamp in the floor
			boolean invisible = p.state().is(net.minecraft.world.level.block.Blocks.LIGHT);
			boolean visible = false;
			for (Direction d : Direction.values()) {
				if (!invisible && open(plan, pos.relative(d))) {
					visible = true;
					break;
				}
			}
			boolean inFloor = !invisible && open(plan, pos.above()) && reach.contains(pos.above());
			int[] t = byType.computeIfAbsent(id(p.state()), k -> new int[3]);
			t[0]++;
			total++;
			if (visible) {
				t[1]++;
				seen++;
			}
			if (inFloor) {
				t[2]++;
				floor++;
				if (floorJ.size() < JSON_LIST) {
					floorJ.add(arr(pos.getX(), pos.getY(), pos.getZ()));
				}
			}
		}
		int walkable = 0;
		for (BlockPos p : reach) {
			if (in(p)) {
				walkable++;
			}
		}
		double density = walkable == 0 ? 0 : floor * 100.0 / walkable;
		StringBuilder sb = new StringBuilder("LIGHT SOURCES (in scope)\n");
		sb.append("  ").append(total).append(" emitters, ").append(seen).append(" with an exposed face (seen), ").append(floor)
				.append(" lamps in the floor under a walkable cell");
		if (!reach.isEmpty()) {
			sb.append(String.format(Locale.ROOT, " = %.2f per 100 walkable cells (%d walkable)", density, walkable));
		}
		sb.append('\n');
		JsonObject tj = new JsonObject();
		for (Map.Entry<String, int[]> e : byType.entrySet()) {
			sb.append(String.format(Locale.ROOT, "    %-34s total %5d  seen %5d  floor %5d%n", e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
			JsonObject t = new JsonObject();
			t.addProperty("total", e.getValue()[0]);
			t.addProperty("seen", e.getValue()[1]);
			t.addProperty("floor", e.getValue()[2]);
			tj.add(e.getKey(), t);
		}
		JsonObject j = new JsonObject();
		j.addProperty("emitters", total);
		j.addProperty("seen", seen);
		j.addProperty("floorLamps", floor);
		j.addProperty("floorLampsPer100Walkable", Math.round(density * 100) / 100.0);
		j.add("byType", tj);
		j.add("floorLampCells", floorJ);
		json.add("lamps", j);
		sections.add(sb.toString());
		summary.put("light sources", total + " (" + seen + " seen, " + floor + " in the floor, " + String.format(Locale.ROOT, "%.2f", density) + "/100 walkable)");
	}

	// ------------------------------------------------------------------ shapes

	static String kind(BlockState s) {
		Block b = s.getBlock();
		if (b instanceof FenceBlock) {
			return "fence";
		}
		if (b instanceof WallBlock) {
			return "wall";
		}
		if (b instanceof IronBarsBlock) {
			return "pane/bars";
		}
		if (b instanceof StairBlock) {
			return "stairs";
		}
		return "other";
	}

	/** Sides a fence, wall, pane or bars connect on (north/east/south/west properties that are not false/none). */
	static int sides(BlockState s) {
		int n = 0;
		for (Property<?> pr : s.getProperties()) {
			String nm = pr.getName();
			if (nm.equals("north") || nm.equals("east") || nm.equals("south") || nm.equals("west")) {
				String v = String.valueOf(s.getValue(pr));
				if (!v.equals("false") && !v.equals("none")) {
					n++;
				}
			}
		}
		return n;
	}

	private static String diff(BlockState a, BlockState b) {
		StringBuilder sb = new StringBuilder();
		for (Property<?> pr : a.getProperties()) {
			Object x = a.getValue(pr);
			Object y = b.getValue(pr);
			if (!Objects.equals(x, y)) {
				sb.append(sb.length() == 0 ? "" : ", ").append(pr.getName()).append(' ').append(x).append("→").append(y);
			}
		}
		return sb.toString();
	}

	private void shapes(BuildPlan plan, Options o) {
		List<PlanProbe.ShapeMismatch> all = new ArrayList<>();
		for (PlanProbe.ShapeMismatch m : PlanProbe.shapeMismatches(plan)) {
			if (in(m.pos())) {
				all.add(m);
			}
		}
		Map<String, Integer> byKind = new TreeMap<>();
		int corners = 0;
		JsonArray lj = new JsonArray();
		StringBuilder sb = new StringBuilder("SHAPES (fences, walls, panes, bars, stairs whose state differs from PlanConnect's among their real neighbours)\n");
		for (PlanProbe.ShapeMismatch m : all) {
			byKind.merge(kind(m.actual()), 1, Integer::sum);
			boolean cornerMissing = kind(m.actual()).equals("stairs") ? false : sides(m.expected()) >= 2 && sides(m.actual()) < sides(m.expected());
			if (cornerMissing) {
				corners++;
			}
			if (lj.size() < JSON_LIST) {
				JsonObject e = new JsonObject();
				e.add("pos", arr(m.pos().getX(), m.pos().getY(), m.pos().getZ()));
				e.addProperty("block", id(m.actual()));
				e.addProperty("kind", kind(m.actual()));
				e.addProperty("diff", diff(m.actual(), m.expected()));
				e.addProperty("cornerMissing", cornerMissing);
				lj.add(e);
			}
		}
		sb.append("  ").append(all.size()).append(" mismatches");
		for (Map.Entry<String, Integer> e : byKind.entrySet()) {
			sb.append(", ").append(e.getKey()).append(' ').append(e.getValue());
		}
		sb.append("; ").append(corners).append(" of them miss a 2- or 3-sided corner (cornersMissing)\n");
		all.sort((a, b) -> Boolean.compare(sides(b.expected()) >= 2, sides(a.expected()) >= 2));
		int n = 0;
		for (PlanProbe.ShapeMismatch m : all) {
			if (n++ >= o.maxList) {
				sb.append("    … ").append(all.size() - o.maxList).append(" more (report.json)\n");
				break;
			}
			sb.append("    ").append(c(m.pos())).append(' ').append(id(m.actual())).append(": ").append(diff(m.actual(), m.expected())).append('\n');
		}
		JsonObject j = new JsonObject();
		j.addProperty("mismatches", all.size());
		j.addProperty("cornersMissing", corners);
		JsonObject kj = new JsonObject();
		for (Map.Entry<String, Integer> e : byKind.entrySet()) {
			kj.addProperty(e.getKey(), e.getValue());
		}
		j.add("byKind", kj);
		j.add("list", lj);
		json.add("shapes", j);
		sections.add(sb.toString());
		summary.put("shape mismatches", all.size() + " (" + corners + " missing corners)");
		System.out.println("shapes: " + all.size() + " mismatches, " + corners + " missing corners");
	}

	// ------------------------------------------------------------------ connect (fix/plan-connect)

	/**
	 * Fences, walls and panes connected on no side although a neighbour they connect to
	 * ({@link dev.t3craft.hq.PlanConnect#connectsTo}) stands beside them. Must be 0. No per-cell catch:
	 * an exception fails the whole section, visibly.
	 */
	private void connect(BuildPlan plan, Options o) {
		List<dev.t3craft.hq.PlanConnect.Isolated> all = new ArrayList<>();
		for (dev.t3craft.hq.PlanConnect.Isolated i : dev.t3craft.hq.PlanConnect.isolated(plan)) {
			if (in(i.pos())) {
				all.add(i);
			}
		}
		int connecting = 0;
		for (Placement p : plan.placements()) {
			if (in(p.rel()) && dev.t3craft.hq.PlanConnect.connecting(p.state())) {
				connecting++;
			}
		}
		StringBuilder sb = new StringBuilder("CONNECT (fences/walls/panes with no connection although a connectable neighbour is beside them)\n");
		sb.append("  ").append(all.size()).append(" unconnected of ").append(connecting).append(" fences/walls/panes\n");
		JsonArray lj = new JsonArray();
		int n = 0;
		for (dev.t3craft.hq.PlanConnect.Isolated i : all) {
			if (n++ < o.maxList) {
				sb.append("    ").append(c(i.pos())).append(' ').append(id(i.state())).append(" -> ").append(i.towards().getName()).append(' ')
						.append(id(i.neighbour())).append('\n');
			}
			if (lj.size() < JSON_LIST) {
				JsonObject e = new JsonObject();
				e.add("pos", arr(i.pos().getX(), i.pos().getY(), i.pos().getZ()));
				e.addProperty("block", id(i.state()));
				e.addProperty("towards", i.towards().getName());
				e.addProperty("neighbour", id(i.neighbour()));
				lj.add(e);
			}
		}
		if (all.size() > o.maxList) {
			sb.append("    … ").append(all.size() - o.maxList).append(" more (report.json)\n");
		}
		JsonObject j = new JsonObject();
		j.addProperty("unconnected", all.size());
		j.addProperty("connecting", connecting);
		j.add("list", lj);
		json.add("connect", j);
		sections.add(sb.toString());
		summary.put("unconnected fences/walls/panes", Integer.toString(all.size()));
		// also on stdout, so the render service's report.txt (which wraps the run output) shows it
		System.out.println("connect: " + all.size() + " unconnected fences/walls/panes beside a connectable neighbour (of " + connecting + ")");
	}

	// ------------------------------------------------------------------ floating

	private void floating(BuildPlan plan, Options o) {
		Map<Long, BlockState> cells = new HashMap<>(plan.size() * 2);
		for (Placement p : plan.placements()) {
			// minecraft:light is an invisible, weightless marker that hangs in the air by design: never "floating"
			if (!p.state().isAir() && !p.state().is(net.minecraft.world.level.block.Blocks.LIGHT)) {
				cells.put(p.rel().asLong(), p.state());
			}
		}
		Set<Long> seen = new HashSet<>(cells.size() * 2);
		List<Object[]> floating = new ArrayList<>(); // size, bbox(int[6]), sample key, top ids
		for (long start : cells.keySet()) {
			if (!seen.add(start)) {
				continue;
			}
			ArrayDeque<Long> q = new ArrayDeque<>();
			q.add(start);
			boolean grounded = false;
			boolean touchesScope = false;
			int size = 0;
			int[] bb = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			Map<String, Integer> ids = new HashMap<>();
			while (!q.isEmpty()) {
				long k = q.poll();
				int x = BlockPos.getX(k);
				int y = BlockPos.getY(k);
				int z = BlockPos.getZ(k);
				size++;
				grounded |= y <= 0;
				touchesScope |= scope == null || scope.contains(x, y, z);
				bb[0] = Math.min(bb[0], x);
				bb[1] = Math.min(bb[1], y);
				bb[2] = Math.min(bb[2], z);
				bb[3] = Math.max(bb[3], x);
				bb[4] = Math.max(bb[4], y);
				bb[5] = Math.max(bb[5], z);
				if (ids.size() < 8) {
					ids.merge(id(cells.get(k)), 1, Integer::sum);
				}
				for (Direction d : Direction.values()) {
					long n = BlockPos.offset(k, d);
					if (cells.containsKey(n) && seen.add(n)) {
						q.add(n);
					}
				}
			}
			if (!grounded && touchesScope) {
				floating.add(new Object[] {size, bb, start, ids});
			}
		}
		floating.sort((a, b) -> Integer.compare((Integer) b[0], (Integer) a[0]));
		int total = 0;
		for (Object[] f : floating) {
			total += (Integer) f[0];
		}
		StringBuilder sb = new StringBuilder("FLOATING (blocks connected to nothing that reaches the ground, y <= 0)\n");
		sb.append("  ").append(floating.size()).append(" floating groups, ").append(total).append(" blocks\n");
		JsonArray fj = new JsonArray();
		int n = 0;
		for (Object[] f : floating) {
			@SuppressWarnings("unchecked")
			Map<String, Integer> ids = (Map<String, Integer>) f[3];
			int[] bb = (int[]) f[1];
			BlockPos s = BlockPos.of((Long) f[2]);
			if (fj.size() < JSON_LIST) {
				JsonObject e = new JsonObject();
				e.addProperty("blocks", (Integer) f[0]);
				e.add("box", arr(bb));
				e.add("sample", arr(s.getX(), s.getY(), s.getZ()));
				e.addProperty("ids", String.join(",", new java.util.TreeSet<>(ids.keySet())));
				fj.add(e);
			}
			if (n++ < o.maxList) {
				sb.append(String.format(Locale.ROOT, "    %5d blocks  box %s  e.g. %s %s%n", (Integer) f[0], box(bb), c(s), String.join(",", new java.util.TreeSet<>(ids.keySet()))));
			}
		}
		if (floating.size() > o.maxList) {
			sb.append("    … ").append(floating.size() - o.maxList).append(" more groups (report.json)\n");
		}
		JsonObject j = new JsonObject();
		j.addProperty("groups", floating.size());
		j.addProperty("blocks", total);
		j.add("list", fj);
		json.add("floating", j);
		sections.add(sb.toString());
		summary.put("floating", floating.size() + " groups, " + total + " blocks");
	}

	private void unsupported(BuildPlan plan, Options o) {
		List<PlanProbe.Unsupported> all = new ArrayList<>();
		for (PlanProbe.Unsupported u : PlanProbe.unsupported(plan)) {
			if (in(u.pos())) {
				all.add(u);
			}
		}
		Map<String, Integer> byId = new TreeMap<>();
		Map<String, BlockPos> first = new HashMap<>();
		JsonArray lj = new JsonArray();
		for (PlanProbe.Unsupported u : all) {
			String id = id(u.state());
			byId.merge(id, 1, Integer::sum);
			first.putIfAbsent(id, u.pos());
			if (lj.size() < JSON_LIST) {
				JsonObject e = new JsonObject();
				e.addProperty("block", id);
				e.add("pos", arr(u.pos().getX(), u.pos().getY(), u.pos().getZ()));
				lj.add(e);
			}
		}
		StringBuilder sb = new StringBuilder("UNSUPPORTED (vanilla canSurvive says no)\n");
		sb.append("  ").append(all.size()).append(" blocks\n");
		int n = 0;
		for (Map.Entry<String, Integer> e : byId.entrySet()) {
			if (n++ >= o.maxList) {
				sb.append("    … more types\n");
				break;
			}
			sb.append(String.format(Locale.ROOT, "    %5d  %s  e.g. %s%n", e.getValue(), e.getKey(), c(first.get(e.getKey()))));
		}
		JsonObject j = new JsonObject();
		j.addProperty("blocks", all.size());
		j.add("list", lj);
		json.add("unsupported", j);
		sections.add(sb.toString());
		summary.put("unsupported", all.size() + " blocks");
	}

	// ------------------------------------------------------------------ split pieces

	private static boolean solidCell(@org.jspecify.annotations.Nullable BlockState s) {
		return s != null && !s.isAir() && !s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** The axis of a horizontal log (a beam: log, stripped log, wood), or null for anything else. */
	private static Direction.@org.jspecify.annotations.Nullable Axis beamAxis(BlockState s) {
		if (!s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) {
			return null;
		}
		// no tags in a headless run (they are not bound): go by the id
		String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		if (!(path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae") || path.endsWith("bamboo_block"))) {
			return null;
		}
		Direction.Axis a = s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS);
		return a == Direction.Axis.Y ? null : a;
	}

	/**
	 * fix/relief-floating: pieces torn by a difference of lift. Two views of it. (1) What the citadel's relief found
	 * while lifting the plan ({@code CitadelRelief.lastSplit}): groups of cells hanging over a step that the column lift
	 * would have cut in two (now lifted with their support), so a number above 0 there is a piece that needed the fix
	 * and "ambiguous" ones are held from two terraces. (2) On the final plan, whatever the generator: horizontal
	 * beams (logs lying on x or z) with nothing solid under any of their blocks and nothing at either end (the pier
	 * or wall that holds them); a torii whose pillars stand a step lower than its beam shows up here. Beams with
	 * nothing around them at all are also in FLOATING.
	 */
	private void split(BuildPlan plan, Options o) {
		CitadelRelief.Split rs = CitadelRelief.lastSplit;
		Map<Long, BlockState> cells = new HashMap<>(plan.size() * 2);
		for (Placement p : plan.placements()) {
			if (!p.state().isAir() && !p.state().is(net.minecraft.world.level.block.Blocks.LIGHT)) {
				cells.put(p.rel().asLong(), p.state());
			}
		}
		List<Object[]> bad = new ArrayList<>(); // length, bbox, start, ids, held (sideways or from above)
		int runs = 0;
		for (Map.Entry<Long, BlockState> e : cells.entrySet()) {
			Direction.Axis axis = beamAxis(e.getValue());
			if (axis == null) {
				continue;
			}
			Direction fwd = Direction.get(Direction.AxisDirection.POSITIVE, axis);
			long start = e.getKey();
			BlockState before = cells.get(BlockPos.offset(start, fwd.getOpposite()));
			if (before != null && beamAxis(before) == axis) {
				continue; // not the first block of its run
			}
			runs++;
			List<Long> run = new ArrayList<>();
			long k = start;
			while (cells.get(k) != null && beamAxis(cells.get(k)) == axis) {
				run.add(k);
				k = BlockPos.offset(k, fwd);
			}
			long last = run.get(run.size() - 1);
			boolean scoped = false;
			boolean held = false;
			boolean below = false;
			Map<String, Integer> ids = new HashMap<>();
			int[] bb = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			for (long r : run) {
				scoped |= scope == null || scope.contains(BlockPos.getX(r), BlockPos.getY(r), BlockPos.getZ(r));
				below |= solidCell(cells.get(BlockPos.offset(r, Direction.DOWN)));
				held |= solidCell(cells.get(BlockPos.offset(r, Direction.UP)));
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (d.getAxis() != axis) {
						held |= solidCell(cells.get(BlockPos.offset(r, d)));
					}
				}
				bb[0] = Math.min(bb[0], BlockPos.getX(r));
				bb[1] = Math.min(bb[1], BlockPos.getY(r));
				bb[2] = Math.min(bb[2], BlockPos.getZ(r));
				bb[3] = Math.max(bb[3], BlockPos.getX(r));
				bb[4] = Math.max(bb[4], BlockPos.getY(r));
				bb[5] = Math.max(bb[5], BlockPos.getZ(r));
				ids.merge(id(cells.get(r)), 1, Integer::sum);
			}
			if (!scoped || below) {
				continue;
			}
			boolean ends = solidCell(cells.get(BlockPos.offset(start, fwd.getOpposite()))) || solidCell(cells.get(BlockPos.offset(last, fwd)));
			if (ends) {
				continue;
			}
			bad.add(new Object[] {run.size(), bb, start, ids, held});
		}
		bad.sort((a, b) -> Integer.compare((Integer) b[0], (Integer) a[0]));
		int heldOnly = 0;
		int lonely = 0;
		for (Object[] b : bad) {
			if ((Boolean) b[4]) {
				heldOnly++;
			} else {
				lonely++;
			}
		}
		StringBuilder sb = new StringBuilder("SPLIT PIECES (cells of one piece lifted differently, or held by nothing)\n");
		sb.append("  relief: ").append(rs.hanging()).append(" groups of hanging cells near a step, ").append(rs.mixed()).append(" of them would have been torn by the column lift (")
				.append(rs.moved()).append(" cells lifted with their support instead), ").append(rs.ambiguous()).append(" held from two terraces\n");
		for (String s : rs.samples().subList(0, Math.min(o.maxList, rs.samples().size()))) {
			sb.append("    ").append(s).append('\n');
		}
		sb.append("  settle: ").append(dev.t3craft.hq.citadel.CitadelSettle.lastSummary()).append('\n');
		// fix/settle-sources: who the settled groups came from (canvas group / zone kind), most first
		JsonArray srcJ = new JsonArray();
		{
			int shown = 0;
			for (var e : dev.t3craft.hq.citadel.CitadelSettle.lastByOrigin().entrySet()) {
				int[] a = e.getValue();
				double mean = a[1] == 0 ? 0 : (double) a[3] / a[1];
				if (shown++ < o.maxList) {
					sb.append(String.format(Locale.ROOT, "    origin %-34s %3d groups (%d blocks): %d fell (mean %.1f), %d post, %d removed, %d left%n", e.getKey(), a[0], a[2], a[1], mean, a[4], a[5], a[6]));
				}
				if (srcJ.size() < JSON_LIST) {
					JsonObject sj2 = new JsonObject();
					sj2.addProperty("origin", e.getKey());
					sj2.addProperty("groups", a[0]);
					sj2.addProperty("blocks", a[2]);
					sj2.addProperty("fell", a[1]);
					sj2.addProperty("meanDrop", mean);
					sj2.addProperty("post", a[4]);
					sj2.addProperty("removed", a[5]);
					sj2.addProperty("left", a[6]);
					srcJ.add(sj2);
				}
			}
			int listed = 0;
			for (var s2 : dev.t3craft.hq.citadel.CitadelSettle.lastSources()) {
				if (listed++ < o.maxList) {
					sb.append(String.format(Locale.ROOT, "      %-7s %2d blocks drop %d at %d,%d,%d  %s / %s  %s%n", s2.outcome(), s2.blocks(), s2.drop(), s2.x(), s2.y(), s2.z(), s2.group(), s2.zone(), s2.blocksOf()));
				}
			}
		}
		sb.append("  beams: ").append(runs).append(" runs of horizontal logs, ").append(bad.size()).append(" with nothing solid under them or at their ends (")
				.append(lonely).append(" touch nothing, ").append(heldOnly).append(" only hang from a wall or ceiling)\n");
		JsonArray bj = new JsonArray();
		int n = 0;
		for (Object[] b : bad) {
			@SuppressWarnings("unchecked")
			Map<String, Integer> ids = (Map<String, Integer>) b[3];
			int[] bb = (int[]) b[1];
			BlockPos s = BlockPos.of((Long) b[2]);
			if (bj.size() < JSON_LIST) {
				JsonObject je = new JsonObject();
				je.addProperty("length", (Integer) b[0]);
				je.add("box", arr(bb));
				je.addProperty("held", (Boolean) b[4]);
				je.addProperty("ids", String.join(",", new java.util.TreeSet<>(ids.keySet())));
				bj.add(je);
			}
			if (n++ < o.maxList) {
				sb.append(String.format(Locale.ROOT, "    %3d long  box %s  e.g. %s %s%s%n", (Integer) b[0], box(bb), c(s), String.join(",", new java.util.TreeSet<>(ids.keySet())),
						(Boolean) b[4] ? "  (hangs from the side or above)" : ""));
			}
		}
		if (bad.size() > o.maxList) {
			sb.append("    … ").append(bad.size() - o.maxList).append(" more (report.json)\n");
		}
		JsonObject j = new JsonObject();
		j.addProperty("reliefGroups", rs.hanging());
		j.addProperty("reliefTorn", rs.mixed());
		j.addProperty("reliefMovedCells", rs.moved());
		j.addProperty("reliefAmbiguous", rs.ambiguous());
		JsonArray sj = new JsonArray();
		rs.samples().forEach(sj::add);
		j.add("reliefSamples", sj);
		j.addProperty("beamRuns", runs);
		j.addProperty("beamsUnsupported", bad.size());
		j.addProperty("beamsTouchNothing", lonely);
		j.addProperty("beamsHeldSideways", heldOnly);
		j.add("beams", bj);
		int[] st = dev.t3craft.hq.citadel.CitadelSettle.lastCounts();
		j.addProperty("settleFell", st[0]);
		j.addProperty("settleFellBlocks", st[1]);
		j.addProperty("settlePropped", st[2]);
		j.addProperty("settleProppedBlocks", st[3]);
		j.addProperty("settleRemoved", st[4]);
		j.addProperty("settleRemovedBlocks", st[5]);
		j.addProperty("settleLeft", st[6]);
		j.addProperty("settleLeftBlocks", st[7]);
		j.add("settleOrigins", srcJ);
		JsonArray allJ = new JsonArray();
		for (var s2 : dev.t3craft.hq.citadel.CitadelSettle.lastSources()) {
			JsonObject so = new JsonObject();
			so.addProperty("group", s2.group());
			so.addProperty("zone", s2.zone());
			so.addProperty("outcome", s2.outcome());
			so.addProperty("drop", s2.drop());
			so.addProperty("blocks", s2.blocks());
			so.addProperty("at", s2.x() + "," + s2.y() + "," + s2.z());
			so.addProperty("made", s2.blocksOf());
			allJ.add(so);
		}
		j.add("settleGroups", allJ);
		json.add("split", j);
		sections.add(sb.toString());
		summary.put("settled floating", dev.t3craft.hq.citadel.CitadelSettle.lastSummary());
		summary.put("split by lift", rs.mixed() + " groups torn by the relief (" + rs.moved() + " cells lifted with their support), " + rs.ambiguous() + " ambiguous");
		summary.put("beams unsupported", bad.size() + " of " + runs + " (" + lonely + " touch nothing, " + heldOnly + " hang from a wall)");
	}

	// ------------------------------------------------------------------ build ticks

	private void build(BuildPlan plan, Options o) {
		List<Placement> sub = new ArrayList<>();
		for (Placement p : plan.placements()) {
			if (in(p.rel())) {
				sub.add(p);
			}
		}
		JsonObject j = new JsonObject();
		StringBuilder sb = new StringBuilder("BUILD (crew simulated without a world: " + o.crew + " builders, " + o.blocksPerTick + " blocks per tick)\n");
		if (sub.size() > o.ticksLimit) {
			sb.append("  skipped: ").append(sub.size()).append(" entries > limit ").append(o.ticksLimit).append(" (--ticks-limit)\n");
			j.addProperty("skipped", true);
			json.add("build", j);
			sections.add(sb.toString());
			summary.put("build ticks", "skipped (" + sub.size() + " entries)");
			return;
		}
		CrewSchedule sch = CrewSchedule.of(HqBuild.toBuildPlan(sub), Math.max(1, o.crew));
		CrewCursor cur = new CrewCursor(sch);
		FrontCursor.Step step = (front, index) -> BuildCursor.Outcome.PLACED;
		long ticks = 0;
		int idle = 0;
		boolean stalled = false;
		while (cur.state() != JobState.COMPLETED && cur.state() != JobState.CANCELLED && ticks < 20_000_000L) {
			int applied = cur.tick(o.blocksPerTick, step);
			ticks++;
			idle = applied == 0 ? idle + 1 : 0;
			if (idle > 5000) {
				stalled = true;
				break;
			}
		}
		double secs = ticks / 20.0;
		sb.append(String.format(Locale.ROOT, "  %d entries (%d placed, %d skipped) in %d ticks = %.1f s = %.1f min; crew walks %.0f blocks%n", sub.size(),
				cur.placed(), cur.skipped(), ticks, secs, secs / 60.0, sch.walked()));
		if (stalled) {
			sb.append("  WARNING: the simulation stalled (no entry placed for 5000 ticks); the figure is a lower bound\n");
		}
		j.addProperty("entries", sub.size());
		j.addProperty("ticks", ticks);
		j.addProperty("seconds", Math.round(secs * 10) / 10.0);
		j.addProperty("crew", o.crew);
		j.addProperty("blocksPerTick", o.blocksPerTick);
		j.addProperty("walkedBlocks", Math.round(sch.walked()));
		j.addProperty("stalled", stalled);
		json.add("build", j);
		sections.add(sb.toString());
		summary.put("build ticks", ticks + " (" + String.format(Locale.ROOT, "%.1f", secs / 60.0) + " min) with " + o.crew + " builders");
	}
}
