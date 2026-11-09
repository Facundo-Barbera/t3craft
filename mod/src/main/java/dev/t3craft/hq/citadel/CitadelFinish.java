package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FH;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.SOLID;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.colX;
import static dev.t3craft.hq.citadel.CitadelCanvas.colZ;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.Box;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqLightMap;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.PlanConnect;
import dev.t3craft.hq.PlanShapes;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.Workstation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The finish of the citadel: the last steps that turn the painted {@link CitadelCanvas} into a
 * {@code BuildPlan}. It paints nothing of its own design except the invisible lights; every other part
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
 * <li>{@link #light}: the light pass, with the invisible vanilla block {@code minecraft:light}
 *     ({@link CitadelCanvas#invisibleLight}, moderate levels 10 to {@value #FILL_MAX}: the visible design lamps,
 *     all 14 or 15, stay the light one sees; see docs/feat-hq-lighting.md). It is <b>fill only</b>: it never
 *     places a visible block. It walks the cells an NPC can reach
 *     ({@link #walkable}, from {@link #SPAWN} in one-block steps, tested by {@link #standable}/{@link #noCollision})
 *     and lights each group <b>on its own</b>: a {@code HqLightMap} sees the group's cells as they are, the
 *     air over its floors as air and everything else opaque (so a neighbour that comes or goes never moves a
 *     light). The lights replace plain air cells of the same group ({@link #freeAir}: never a fixture, an
 *     anchor or a desk's cell), in the walking cell (or at its head), as DECOR (after the solids). Two steps: a regular
 *     lattice ({@code (x + 5z) mod 13}, a light only where it lights {@value #LATTICE_MIN_GAIN} dark cells or
 *     more; with level {@value #FILL_MAX} in the walking cell every open cell is within 2 steps of one) and then
 *     {@link #lightSpot} for each cell still under {@link #MIN_LIGHT} (the best free air within {@value #REACH}
 *     blocks). Each light takes the weakest level of 10..{@value #FILL_MAX} that does what {@value #FILL_MAX} would
 *     ({@link #weakest}). Lamps that are design
 *     (froglights in posts, lanterns, shoji) stay and count as light. {@code minecraft:light} has no
 *     collision and is replaceable: NPCs and builders walk through it. The survival bill does not charge it
 *     ({@code BlockCosts}, {@code HqCost}) and {@code HqLighting} counts it as a source (no duplicated lamps).</li>
 * <li>{@link #placementOrder}: the placement order (group by {@link #GROUPS}, then layer, then y, z, x;
 *     hanging cells top-down) with copies of the block-entity data.</li>
 * <li>{@link #modules(List)}: per group, per x, the runs of consecutive z as {@code BuildPlan.Module}s.</li>
 * </ol>
 *
 * <h2>Roles and blocks</h2>
 *
 * Layers: {@code SOLID} (0), {@code DECOR} (1, after the solids), {@code HANGING} (2, top-down).
 * Blocks: {@code AIR} to clear, {@code minecraft:light} (invisible) for the floor lights. Zone kinds decide the
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
	static List<BlockPos> walkable(CitadelCanvas c) {
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

	/** Block light the map gives a walkable cell (its feet cell: what the report measures). */
	private static int lightAt(HqLightMap map, BlockPos feet) {
		return map.at(feet);
	}

	/** The plan as one dense block: state and group id of every cell, so each group can be seen on its own. */
	private static final class Dense {
		final int minX;
		final int minY;
		final int minZ;
		final int sx;
		final int sy;
		final int sz;
		final BlockState[] st;
		final int[] gid;
		final List<String> names = new ArrayList<>();
		/** Per group id: minX, minY, minZ, maxX, maxY, maxZ of its cells. */
		final List<int[]> boxes = new ArrayList<>();

		Dense(CitadelCanvas c, Map<Long, String> groups) {
			int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			for (long k : c.cells.keySet()) {
				b[0] = Math.min(b[0], BlockPos.getX(k));
				b[1] = Math.min(b[1], BlockPos.getY(k));
				b[2] = Math.min(b[2], BlockPos.getZ(k));
				b[3] = Math.max(b[3], BlockPos.getX(k));
				b[4] = Math.max(b[4], BlockPos.getY(k));
				b[5] = Math.max(b[5], BlockPos.getZ(k));
			}
			minX = b[0] - 1;
			minY = b[1] - 1;
			minZ = b[2] - 1;
			sx = b[3] - b[0] + 3;
			sy = b[4] - b[1] + 3;
			sz = b[5] - b[2] + 3;
			st = new BlockState[sx * sy * sz];
			gid = new int[st.length];
			Arrays.fill(gid, -1);
			Map<String, Integer> ids = new HashMap<>();
			for (Map.Entry<Long, BlockState> e : c.cells.entrySet()) {
				long k = e.getKey();
				int x = BlockPos.getX(k);
				int y = BlockPos.getY(k);
				int z = BlockPos.getZ(k);
				String g = groups.get(k);
				Integer id = ids.get(g);
				if (id == null) {
					id = names.size();
					ids.put(g, id);
					names.add(g);
					boxes.add(new int[] {x, y, z, x, y, z});
				}
				int[] bx = boxes.get(id);
				bx[0] = Math.min(bx[0], x);
				bx[1] = Math.min(bx[1], y);
				bx[2] = Math.min(bx[2], z);
				bx[3] = Math.max(bx[3], x);
				bx[4] = Math.max(bx[4], y);
				bx[5] = Math.max(bx[5], z);
				int i = index(x, y, z);
				st[i] = e.getValue();
				gid[i] = id;
			}
		}

		int index(int x, int y, int z) {
			int ix = x - minX;
			int iy = y - minY;
			int iz = z - minZ;
			if (ix < 0 || iy < 0 || iz < 0 || ix >= sx || iy >= sy || iz >= sz) {
				return -1;
			}
			return (iy * sz + iz) * sx + ix;
		}

		/**
		 * A cell as group {@code me}'s light sees it, from the group alone (so a neighbour that comes or goes
		 * changes nothing): its own cells as they are; the air over its own floors (up to 3
		 * cells above it: where its people walk) as air; every other cell opaque.
		 */
		@Nullable BlockState view(int me, int x, int y, int z) {
			int i = index(x, y, z);
			if (i >= 0 && gid[i] == me) {
				return st[i];
			}
			BlockState s = i < 0 ? null : st[i];
			if (s == null || s.isAir() && s.getLightEmission() == 0) {
				for (int h = 1; h <= AIRSPACE; h++) {
					int b = index(x, y - h, z);
					if (b >= 0 && gid[b] == me && st[b] != null && !st[b].isAir()) {
						return null;
					}
				}
			}
			return OPAQUE;
		}
	}

	/** How far above a group's own blocks its light sees air (feet, head and one more). */
	private static final int AIRSPACE = 3;
	/** What a cell of another group is to a group's light: opaque. */
	private static final BlockState OPAQUE = Blocks.STONE.defaultBlockState();
	/**
	 * Strongest invisible fill (citadel/light-aesthetic): moderate, under the visible design lamps (lanterns,
	 * shroomlights, campfires: 15), so the eye still reads where the light comes from.
	 */
	static final int FILL_MAX = 12;
	/** Levels of the invisible lights, weakest first: the weakest that lights what {@link #FILL_MAX} would is used. */
	private static final int[] LEVELS = {10, 11, FILL_MAX};
	/** Period of the preferred lattice of lights: {@code (x + 5z) mod 13} tiles the plane with radius-2 diamonds (a level-12 light in the cell itself reaches 10 at 2). */
	private static final int LATTICE = 13;
	/** A lattice light is placed only where it lights at least this many dark cells (the stragglers are lit one by one). */
	private static final int LATTICE_MIN_GAIN = 4;
	/** How far from a dark cell (blocks on the plane) a light may stand to light it. */
	private static final int REACH = 4;

	/** The reserved cells no light may take: fixtures, and the cells an NPC works or waits in. */
	static Set<Long> reserved(CitadelCanvas c) {
		Set<Long> out = new HashSet<>(c.fixtures);
		for (Anchor a : c.anchors.values()) {
			for (int h = 0; h <= 2; h++) {
				out.add(a.block().above(h).asLong());
			}
		}
		for (Workstation w : c.workstations) {
			for (int h = 0; h <= 2; h++) {
				out.add(w.deskAnchor().block().above(h).asLong());
			}
		}
		return out;
	}

	/** A cell a light may take: plain air of the group itself, not reserved. */
	private static boolean freeAir(CitadelCanvas c, long k, String group, Map<Long, String> groups, Set<Long> reserved) {
		BlockState s = c.cells.get(k);
		return s != null && s.getBlock() == Blocks.AIR && group.equals(groups.get(k)) && !reserved.contains(k);
	}

	/** The chosen place and level of one light. */
	private record Spot(BlockPos pos, int level, int gain) {
	}

	/** The weakest level in {@link #LEVELS} that does what {@link #FILL_MAX} does at {@code pos} (gain and, when given, the cell {@code must}). */
	private static Spot weakest(HqLightMap map, BlockPos pos, int gain15, java.util.function.LongPredicate counts, @Nullable BlockPos must) {
		for (int level : LEVELS) {
			int g = must == null ? map.gainOf(pos, CitadelCanvas.invisibleLight(level), counts)
					: map.gainOf(pos, CitadelCanvas.invisibleLight(level), counts, must, MIN_LIGHT);
			if (g >= gain15) {
				return new Spot(pos, level, g);
			}
		}
		return new Spot(pos, FILL_MAX, gain15);
	}

	/**
	 * Where to hang a light for a dark walkable cell {@code feet}: the air of the cell itself first, then its head, then the other
	 * air within {@value #REACH} blocks that lights it and the most other dark cells. Null when no free air does.
	 */
	private static @Nullable Spot lightSpot(CitadelCanvas c, HqLightMap map, BlockPos feet, String group, Map<Long, String> groups,
			Set<Long> reserved, java.util.function.LongPredicate counts) {
		BlockState top = CitadelCanvas.invisibleLight(FILL_MAX);
		for (int r = 0; r <= REACH; r++) {
			BlockPos best = null;
			int bestGain = 0;
			for (int dy : new int[] {0, 1, 2}) {
				for (int dz = -r; dz <= r; dz++) {
					int dx = r - Math.abs(dz);
					for (int sgn = -1; sgn <= 1; sgn += 2) {
						if (dx == 0 && sgn == 1) {
							continue;
						}
						BlockPos k = feet.offset(sgn * dx, dy, dz);
						if (!freeAir(c, k.asLong(), group, groups, reserved)) {
							continue;
						}
						int g = map.gainOf(k, top, counts, feet, MIN_LIGHT);
						if (g > bestGain) {
							best = k;
							bestGain = g;
						}
					}
				}
			}
			if (best != null) {
				return weakest(map, best, bestGain, counts, feet);
			}
		}
		return null;
	}

	private static void hang(CitadelCanvas c, HqLightMap map, Dense d, Spot s) {
		BlockState light = CitadelCanvas.invisibleLight(s.level());
		c.put(s.pos().getX(), s.pos().getY(), s.pos().getZ(), light, DECOR);
		d.st[d.index(s.pos().getX(), s.pos().getY(), s.pos().getZ())] = light;
		map.addLamp(s.pos(), light);
	}

	/**
	 * Lights every walkable cell (see the class comment) from its own group's lights, with invisible
	 * {@code minecraft:light} blocks hung in the air of the group: first on a regular lattice (where it
	 * lights {@value #LATTICE_MIN_GAIN} dark cells or more), then one by one for what is still dark. This is
	 * the fill under the design lamps ({@link CitadelAmbience}, the halls, the hub): it never places a visible
	 * block and never goes above {@link #FILL_MAX}.
	 */
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
		Dense d = new Dense(c, groups);
		Set<Long> reserved = reserved(c);
		BlockState top = CitadelCanvas.invisibleLight(FILL_MAX);
		for (Map.Entry<String, List<BlockPos>> e : byGroup.entrySet()) {
			String g = e.getKey();
			List<BlockPos> walk = e.getValue();
			int me = d.names.indexOf(g);
			int[] b = d.boxes.get(me);
			HqLightMap map = HqLightMap.ofCells(new Box(b[0], b[1], b[2], b[3], b[4] + AIRSPACE, b[5]), 1, (x, y, z) -> d.view(me, x, y, z));
			Set<Long> keys = new HashSet<>();
			for (BlockPos p : walk) {
				keys.add(p.asLong());
			}
			java.util.function.LongPredicate counts = keys::contains;
			for (BlockPos p : walk) {
				if (Math.floorMod(p.getX() + 5 * p.getZ(), LATTICE) != 0) {
					continue;
				}
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos k = p.above(dy);
					if (!freeAir(c, k.asLong(), g, groups, reserved)) {
						continue;
					}
					int gain = map.gainOf(k, top, counts);
					if (gain >= LATTICE_MIN_GAIN) {
						hang(c, map, d, weakest(map, k, gain, counts, null));
					}
					break;
				}
			}
			for (BlockPos p : walk) {
				if (lightAt(map, p) >= MIN_LIGHT) {
					continue;
				}
				Spot s = lightSpot(c, map, p, g, groups, reserved, counts);
				if (s != null) {
					hang(c, map, d, s);
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
		CitadelAmbience.lights(c);
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
