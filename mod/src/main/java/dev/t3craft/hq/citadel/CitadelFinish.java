package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FH;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.SOLID;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.colX;
import static dev.t3craft.hq.citadel.CitadelCanvas.colZ;

import dev.t3craft.hq.Box;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.PlanConnect;
import dev.t3craft.hq.PlanLight;
import dev.t3craft.hq.PlanShapes;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.Workstation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The finish of the citadel: the last steps that turn the painted {@link CitadelCanvas} into a
 * {@code BuildPlan}. It paints nothing of its own design except the embedded lights; every other part
 * only reads the canvas. Pure and deterministic: same canvas in, same plan out, in the same order.
 *
 * <h2>Pieces (called by {@code RingCampusGenerator.generate} as {@link #build}, in this order)</h2>
 *
 * <ol>
 * <li>{@link #fillAir(CitadelCanvas)}: puts air in every column from its lowest cell up to its highest
 *     (and at least 5 cells above the ground, 3 on the lawn). Parameters: the canvas. Reads
 *     {@code cells}, {@code zones}; writes {@code cells}/{@code layers} with {@code AIR} on layer
 *     {@code SOLID}.</li>
 * <li>{@code PlanShapes.resolve}, then {@code PlanConnect.apply} (fix/plan-connect): connects stairs, fences,
 *     walls and panes from the final cells, with explicit rules.</li>
 * <li>{@link #groupOf}: the construction group of each cell (see below), via {@link #lotGroup} for the
 *     cells of a lot.</li>
 * <li>{@link #light}: the light pass. It walks the cells an NPC can reach ({@link #walkable}, from
 *     {@link #SPAWN} in one-block steps, tested by {@link #standable}/{@link #noCollision}), measures
 *     each with {@link #lightAt} against {@link #MIN_LIGHT}, and where it is dark embeds a
 *     {@link CitadelCanvas#LIGHT} block ({@code PEARLESCENT_FROGLIGHT}) found by {@link #lightSpot}:
 *     the floor, under a slab, the floor beside it, then the ceiling. A cell is {@link #replaceable}
 *     only when it is a full, opaque, non-emitting block of the same group that is not a fixture and
 *     has no block-entity data.</li>
 * <li>{@link #placementOrder}: the placement order (group by {@link #GROUPS}, then layer, then y, z, x;
 *     hanging cells top-down) with copies of the block-entity data.</li>
 * <li>{@link #modules(List)}: per group, per x, the runs of consecutive z as {@code BuildPlan.Module}s.</li>
 * </ol>
 *
 * <h2>Roles and blocks</h2>
 *
 * Layers: {@code SOLID} (0), {@code DECOR} (1, after the solids), {@code HANGING} (2, top-down).
 * Blocks: {@code AIR} to clear, {@code LIGHT} (froglight) for the embedded lights. Zone kinds decide the
 * group; a lot's hall column (from {@code hallCols}) and floor decide the module.
 *
 * <h2>Groups produced (the construction units)</h2>
 *
 * {@code hub} (hub, hub wall), {@code archive} ({@code HqGenerator.GROUP_ARCHIVE}: node 4),
 * {@code cloister} (bridge, patio, gallery, ramp, terrace, inner street), {@code node_<k>},
 * {@code ring_<m>} (avenues, street, and empty lots of ring m), {@code lot_<id>} (a barrio's courtyard,
 * paths, gardens and gatehouse), {@code module_<index>} (one floor of a hall: {@code HqGenerator.group})
 * and {@code grounds} (the lawn). {@link #groupRank} orders them (hub, archive, cloister, node, ring,
 * lot, module, grounds) and {@link #GROUPS} breaks ties (modules numerically, the rest by name).
 *
 * <h2>Anchors</h2>
 *
 * None are produced here; the {@code spawn} anchor is placed by the generator at {@link #SPAWN} before
 * {@link #build}, and the anchors and sorted workstations of the canvas go into the plan unchanged.
 */
public final class CitadelFinish {
	/** Block light every walkable cell must reach (see {@link #light}). */
	public static final int MIN_LIGHT = 10;

	/** Where NPCs appear: the inner street, south, in front of the main ramp. */
	public static final BlockPos SPAWN = new BlockPos(0, 1, 38);

	private CitadelFinish() {
	}

	// ------------------------------------------------------------------ groups

	/** The construction group of a cell (see the class comment). */
	public static String groupOf(CitadelCanvas c, int x, int y, int z) {
		Zone zn = c.zoneAt(x, z);
		return switch (zn.kind()) {
			case HUB, HUB_WALL -> "hub";
			case BRIDGE, PATIO, GALLERY, RAMP, TERRACE, INNER_STREET -> "cloister";
			case NODE -> zn.node() == 4 ? HqGenerator.GROUP_ARCHIVE : "node_" + zn.node();
			case AVENUE, STREET -> "ring_" + zn.ring();
			case LOT -> lotGroup(c, zn, x, y, z);
			case LAWN -> "grounds";
			default -> throw new IllegalStateException("no zone at " + x + "," + z);
		};
	}

	/**
	 * The group of a cell in a lot: {@code ring_<m>} when the lot is empty, {@code lot_<id>} outside the
	 * halls, and {@code module_<index>} of the hall floor (the other floor's module when this one is
	 * missing) inside them.
	 */
	private static String lotGroup(CitadelCanvas c, Zone zn, int x, int y, int z) {
		TreeMap<Integer, HqLayout.Module> mods = c.lots.get(zn.lot());
		if (mods == null) {
			return "ring_" + zn.ring();
		}
		int[] hall = c.hallCols.get(col(x, z));
		if (hall == null) {
			return "lot_" + zn.lot();
		}
		int f = y >= FH ? 1 : 0;
		HqLayout.Module m = mods.get(f * RingCampusGeometry.HALLS + hall[1]);
		if (m == null) {
			m = mods.get((1 - f) * RingCampusGeometry.HALLS + hall[1]);
		}
		return HqGenerator.group(m.index());
	}

	private static int groupRank(String g) {
		if (g.equals("hub")) {
			return 0;
		}
		if (g.equals(HqGenerator.GROUP_ARCHIVE)) {
			return 1;
		}
		if (g.equals("cloister")) {
			return 2;
		}
		if (g.startsWith("node_")) {
			return 3;
		}
		if (g.startsWith("ring_")) {
			return 4;
		}
		if (g.startsWith("lot_")) {
			return 5;
		}
		if (g.startsWith("module_")) {
			return 6;
		}
		return 7;
	}

	private static final Comparator<String> GROUPS = Comparator.comparingInt(CitadelFinish::groupRank).thenComparing((a, b) -> {
		if (a.startsWith("module_") && b.startsWith("module_")) {
			return Integer.compare(Integer.parseInt(a.substring(7)), Integer.parseInt(b.substring(7)));
		}
		return a.compareTo(b);
	});

	// ------------------------------------------------------------------ light pass

	private static boolean noCollision(CitadelCanvas c, long k) {
		BlockState s = c.cells.get(k);
		return s == null || s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.of(k)).isEmpty();
	}

	private static boolean standable(CitadelCanvas c, int x, int y, int z) {
		if (c.zoneAt(x, z).kind() == Kind.NONE) {
			return false;
		}
		return noCollision(c, BlockPos.asLong(x, y, z)) && noCollision(c, BlockPos.asLong(x, y + 1, z)) && !noCollision(c, BlockPos.asLong(x, y - 1, z));
	}

	/**
	 * Cells an NPC may stand in, walking from the spawn by one-block steps up or down: a superset of
	 * what the client's navigation reaches (it also lets an NPC "stand" on railings), so lighting
	 * these lights every cell it really walks.
	 */
	private static List<BlockPos> walkable(CitadelCanvas c) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		if (standable(c, SPAWN.getX(), SPAWN.getY(), SPAWN.getZ())) {
			seen.add(SPAWN.asLong());
			todo.add(SPAWN);
		}
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = p.relative(d).above(dy);
					if (n.getY() < c.style.bottom() || n.getY() > c.style.top() || seen.contains(n.asLong())) {
						continue;
					}
					if (standable(c, n.getX(), n.getY(), n.getZ())) {
						seen.add(n.asLong());
						todo.add(n);
					}
				}
			}
		}
		List<BlockPos> out = new ArrayList<>();
		for (long k : seen) {
			out.add(BlockPos.of(k));
		}
		out.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getZ()).thenComparingInt(p -> p.getX()));
		return out;
	}

	private static int lightAt(CitadelCanvas c, PlanLight l, BlockPos feet) {
		int v = l.get(feet);
		BlockState below = c.cells.get(feet.below().asLong());
		if (below != null && below.getLightDampening() < 15) {
			v = Math.min(v, l.get(feet.below()));
		}
		return v;
	}

	private static boolean replaceable(CitadelCanvas c, long k, String group, Map<Long, String> groups) {
		BlockState s = c.cells.get(k);
		return s != null && !s.isAir() && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.of(k)) && s.getLightDampening() >= 15
				&& s.getLightEmission() == 0 && !c.fixtures.contains(k) && !c.data.containsKey(k)
				&& group.equals(groups.get(k));
	}

	/** Where to embed a light for a dark walkable cell: the floor, under a slab, the floor beside it, the ceiling. */
	private static @Nullable Long lightSpot(CitadelCanvas c, BlockPos feet, String group, Map<Long, String> groups) {
		List<BlockPos> tries = new ArrayList<>();
		tries.add(feet.below());
		tries.add(feet.below(2));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			tries.add(feet.below().relative(d));
		}
		for (BlockPos t : tries) {
			if (replaceable(c, t.asLong(), group, groups)) {
				return t.asLong();
			}
		}
		for (int y = feet.getY() + 2; y <= feet.getY() + 7; y++) {
			long k = BlockPos.asLong(feet.getX(), y, feet.getZ());
			BlockState s = c.cells.get(k);
			if (s != null && !s.isAir()) {
				return replaceable(c, k, group, groups) ? k : null;
			}
		}
		return null;
	}

	/** Lights every walkable cell (see the class comment) from its own group's lights. */
	private static void light(CitadelCanvas c, Map<Long, String> groups) {
		TreeMap<String, List<BlockPos>> byGroup = new TreeMap<>();
		for (BlockPos p : walkable(c)) {
			Kind k = c.zoneAt(p.getX(), p.getZ()).kind();
			String g = groups.get(p.below().asLong());
			if (k == Kind.LAWN || g == null) {
				continue;
			}
			byGroup.computeIfAbsent(g, x -> new ArrayList<>()).add(p);
		}
		Map<String, List<Long>> emitters = new HashMap<>();
		for (Map.Entry<Long, BlockState> e : c.cells.entrySet()) {
			if (e.getValue().getLightEmission() > 0) {
				emitters.computeIfAbsent(groups.get(e.getKey()), x -> new ArrayList<>()).add(e.getKey());
			}
		}
		for (Map.Entry<String, List<BlockPos>> e : byGroup.entrySet()) {
			String g = e.getKey();
			PlanLight l = new PlanLight(c.cells::get);
			for (long k : emitters.getOrDefault(g, List.of())) {
				l.add(k, c.cells.get(k).getLightEmission());
			}
			l.run();
			for (BlockPos p : e.getValue()) {
				if (lightAt(c, l, p) >= MIN_LIGHT) {
					continue;
				}
				Long spot = lightSpot(c, p, g, groups);
				if (spot != null) {
					c.cells.put(spot, LIGHT);
					l.add(spot, LIGHT.getLightEmission());
					l.run();
				}
			}
		}
	}

	// ------------------------------------------------------------------ build

	private record Cell(BlockPos pos, String group, int layer) {
	}

	/** Clears the air in every column: from its lowest cell up to its highest (and at least 5 above the ground). */
	private static void fillAir(CitadelCanvas c) {
		Map<Long, int[]> span = new HashMap<>();
		for (long k : c.cells.keySet()) {
			BlockPos p = BlockPos.of(k);
			int[] sp = span.computeIfAbsent(col(p.getX(), p.getZ()), cc -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
			sp[0] = Math.min(sp[0], p.getY());
			sp[1] = Math.max(sp[1], p.getY());
		}
		for (Map.Entry<Long, int[]> e : span.entrySet()) {
			int x = colX(e.getKey());
			int z = colZ(e.getKey());
			Kind kind = c.zoneAt(x, z).kind();
			int top = Math.max(e.getValue()[1], kind == Kind.LAWN ? 3 : 5);
			for (int y = e.getValue()[0]; y <= top; y++) {
				long k = BlockPos.asLong(x, y, z);
				if (!c.cells.containsKey(k)) {
					c.cells.put(k, AIR);
					c.layers.put(k, SOLID);
				}
			}
		}
	}

	/** The placement order: group, layer, then y (top-down for hanging cells), z, x. */
	private static List<Cell> placementOrder(CitadelCanvas c, Map<Long, String> groups) {
		List<Cell> all = new ArrayList<>(c.cells.size());
		for (long k : c.cells.keySet()) {
			all.add(new Cell(BlockPos.of(k), groups.get(k), c.layers.getOrDefault(k, SOLID)));
		}
		all.sort(Comparator.<Cell, String>comparing(Cell::group, GROUPS)
				.thenComparingInt(Cell::layer)
				.thenComparingInt(cc -> cc.layer() == HANGING ? -cc.pos().getY() : cc.pos().getY())
				.thenComparingInt(cc -> cc.pos().getZ())
				.thenComparingInt(cc -> cc.pos().getX()));
		return all;
	}

	/** Modules: per group, per x, runs of consecutive z. */
	private static List<BuildPlan.Module> modules(List<Cell> all) {
		TreeMap<String, TreeMap<Integer, TreeMap<Integer, int[]>>> byGroup = new TreeMap<>(GROUPS);
		for (Cell cell : all) {
			int[] yr = byGroup.computeIfAbsent(cell.group(), g -> new TreeMap<>()).computeIfAbsent(cell.pos().getX(), x -> new TreeMap<>())
					.computeIfAbsent(cell.pos().getZ(), z -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
			yr[0] = Math.min(yr[0], cell.pos().getY());
			yr[1] = Math.max(yr[1], cell.pos().getY());
		}
		List<BuildPlan.Module> modules = new ArrayList<>();
		for (var g : byGroup.entrySet()) {
			for (var xe : g.getValue().entrySet()) {
				int x = xe.getKey();
				Integer z0 = null;
				int prev = 0;
				int y1 = 0;
				int y2 = 0;
				for (var ze : xe.getValue().entrySet()) {
					int z = ze.getKey();
					int[] yr = ze.getValue();
					if (z0 != null && z == prev + 1) {
						y1 = Math.min(y1, yr[0]);
						y2 = Math.max(y2, yr[1]);
					} else {
						if (z0 != null) {
							modules.add(new BuildPlan.Module(g.getKey() + "@" + x + "," + z0, Box.of(x, y1, z0, x, y2, prev), List.of(), g.getKey()));
						}
						z0 = z;
						y1 = yr[0];
						y2 = yr[1];
					}
					prev = z;
				}
				if (z0 != null) {
					modules.add(new BuildPlan.Module(g.getKey() + "@" + x + "," + z0, Box.of(x, y1, z0, x, y2, prev), List.of(), g.getKey()));
				}
			}
		}
		return modules;
	}

	/** Turns the painted canvas into the plan: air, shapes, groups, light pass, placements, modules. */
	public static BuildPlan build(CitadelCanvas c) {
		fillAir(c);
		PlanShapes.resolve(c.cells);
		// fix/plan-connect: the explicit, world-free connection pass on the final cells (what resolve misses)
		PlanConnect.apply(c.cells);
		Map<Long, String> groups = new HashMap<>(c.cells.size() * 2);
		for (long k : c.cells.keySet()) {
			BlockPos p = BlockPos.of(k);
			groups.put(k, groupOf(c, p.getX(), p.getY(), p.getZ()));
		}
		light(c, groups);
		Map<BlockPos, String> index = new HashMap<>(c.cells.size() * 2);
		for (long k : c.cells.keySet()) {
			index.put(BlockPos.of(k), groups.get(k));
		}
		List<Cell> all = placementOrder(c, groups);
		List<Placement> out = new ArrayList<>(all.size());
		for (Cell cell : all) {
			long k = cell.pos().asLong();
			CompoundTag be = c.data.get(k);
			out.add(new Placement(cell.pos(), c.cells.get(k), be == null ? null : be.copy()));
		}
		List<BuildPlan.Module> modules = modules(all);
		c.workstations.sort(Comparator.comparingInt(Workstation::module).thenComparingInt(Workstation::slot));
		return new BuildPlan(out, c.anchors, modules, c.workstations, Collections.unmodifiableMap(index));
	}
}
