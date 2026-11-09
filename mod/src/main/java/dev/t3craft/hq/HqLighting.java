package dev.t3craft.hq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * fix/hq-lanterns, citadel/light-aesthetic: the built-in <b>fill</b> lighting of every HQ plan. After a layout
 * is generated, every cell people can walk to from the spawn should read at least {@link HqLightMap#MIN_LEVEL}
 * of block light at night. The light one <i>sees</i> is the layout's own design (lanterns on posts, under
 * eaves and porches, pendants, braziers, lit stained glass: see {@code docs/features/hq-lighting.md}); this pass
 * only covers what that design leaves dark, and only with the invisible vanilla block {@code minecraft:light}
 * at moderate levels (10 to {@value #FILL_MAX}), so the visible lamps (14, 15) still dominate the scene. It
 * never lets a visible lamp (froglight, shoji panel) into a floor, wall or ceiling.
 * <ol>
 *   <li>a lattice {@code (x + 5z) mod }{@value #LATTICE13} of invisible lights in the walking cells (plan
 *   coordinates), only where some walkable cell nearby is dark;</li>
 *   <li>then, for each cell still dark, the free air cell nearby (the cell itself, its head, the air around)
 *   that lights it and the most other dark cells (exact flood fill), at the weakest level that does.</li>
 * </ol>
 *
 * <p><b>Local to each part.</b> The HQ grows and shrinks by groups ({@link BuildPlan#groups()}) and its
 * diffs must touch only the parts that changed, so every group is lit <b>on its own</b>: it only counts
 * its own lights, cells of other groups read as opaque (their light never helps, so the real HQ is at
 * least as bright as the estimate), and lights only go into the group's own air. Desk cells (the
 * desk modules above their floor, every screen and desk top) read as air whether the desk is taken or
 * vacant, so taking or freeing a desk never moves a light; no light ever goes into a desk cell, a
 * screen, an anchor's cell or the head room over it.
 *
 * <p>A light takes a plain air placement of the group, or a cell nobody placed that a module of the group
 * owns (it is then added as a placement of that group). {@code minecraft:light} has no collision and is
 * replaceable, so walkability, anchors and module ownership stay exactly as generated. Pure and
 * deterministic.
 */
final class HqLighting {
	/** Legacy spacing of the old floor-lamp grid (the {@code lighting.grid} pack parameter); no longer drives placement. */
	static final int GRID = 5;
	/** Farthest a fallback light may be from the dark cell it is for (blocks, each axis). */
	private static final int REACH = 3;
	/** Legacy period of the old floor-lamp lattice (the {@code lighting.lattice} pack parameter); no longer drives placement. */
	static final int LATTICE = 9;
	/** Strongest invisible fill: moderate, under the design lamps (lanterns 15, torches 14). */
	static final int FILL_MAX = 12;
	/** Levels of the invisible fill, weakest first. */
	private static final int[] LEVELS = {HqLightMap.MIN_LEVEL, HqLightMap.MIN_LEVEL + 1, FILL_MAX};
	/** Period of the fill lattice {@code (x + 5z) mod 13}: radius-2 diamonds tile the plane (a level-12 light reaches 10 two steps away). */
	static final int LATTICE13 = 13;
	/** How far above a group's own blocks its light sees air (feet, head and one more). */
	private static final int AIRSPACE = 3;
	/** What a cell of another group is to a group's light: opaque. */
	private static final BlockState OTHER = Blocks.STONE.defaultBlockState();

	private final BuildPlan plan;
	private final HqStyle style;
	private final Map<BlockPos, String> groups;
	/** Cells of the classic desk modules above their floor: air to the walk estimate (taken or vacant alike). */
	private final Set<BlockPos> desks = new HashSet<>();
	/** The desk rows among {@link #desks}: opaque to the light estimate (a taken desk shades), taken or vacant alike. */
	private final Set<BlockPos> deskSolid = new HashSet<>();
	/** Dense copy of the view over the plan's bounds (+1): group index (-1: nobody) and state (desks as air, lamps in). */
	private final int minX;
	private final int minY;
	private final int minZ;
	private final int sx;
	private final int sy;
	private final int sz;
	private final int[] gid;
	private final BlockState[] cur;
	private final List<String> groupNames = new ArrayList<>();
	private final Set<BlockPos> keep = new HashSet<>();
	/** The invisible lights placed, by position (a new one where nobody had placed anything is also in {@link #added}). */
	private final Map<BlockPos, BlockState> lamps = new HashMap<>();
	/** Lights in cells the plan had no placement for, with the group that owns them. */
	private final Map<BlockPos, String> added = new HashMap<>();
	private final Map<BlockPos, Double> floors = new HashMap<>();
	/** Walkable cells that need more than {@link HqLightMap#MIN_LEVEL} (standing rows in front of free desks). */
	private final Map<BlockPos, Integer> need = new HashMap<>();

	private HqLighting(BuildPlan plan, HqStyle style) {
		this.plan = plan;
		this.style = style;
		this.groups = plan.groupsByPos();
		Box fp = plan.footprint();
		for (Placement p : plan.placements()) {
			BlockPos r = p.rel();
			if (!fp.contains(r)) {
				fp = fp.union(new Box(r.getX(), r.getY(), r.getZ(), r.getX(), r.getY(), r.getZ()));
			}
		}
		this.minX = fp.minX() - 1;
		this.minY = fp.minY() - 1;
		this.minZ = fp.minZ() - 1;
		this.sx = fp.sizeX() + 2;
		this.sy = fp.sizeY() + 2;
		this.sz = fp.sizeZ() + 2;
		this.gid = new int[sx * sy * sz];
		java.util.Arrays.fill(gid, -1);
		this.cur = new BlockState[gid.length];
		Map<String, Integer> ids = new HashMap<>();
		for (Map.Entry<BlockPos, String> e : groups.entrySet()) {
			Integer id = ids.get(e.getValue());
			if (id == null) {
				id = groupNames.size();
				groupNames.add(e.getValue());
				ids.put(e.getValue(), id);
			}
			gid[index(e.getKey())] = id;
		}
		// classic desk modules (wing_<i>_desk_<slot>): above their floor, the desk row (drawer, top, screen) is
		// opaque to the light and the standing row is air, taken or vacant alike
		java.util.regex.Pattern deskId = java.util.regex.Pattern.compile("wing_(\\d+)_desk_(\\d+)");
		for (BuildPlan.Module m : plan.modules()) {
			java.util.regex.Matcher mt = deskId.matcher(m.id());
			if (!mt.matches() || m.sparse()) {
				continue;
			}
			int wing = Integer.parseInt(mt.group(1));
			int slot = Integer.parseInt(mt.group(2));
			int deskZ = HqGeometry.wz(wing, HqGeometry.deskV(slot));
			Box b = m.box();
			for (int y = b.minY() + 1; y <= b.maxY(); y++) {
				for (int z = b.minZ(); z <= b.maxZ(); z++) {
					for (int x = b.minX(); x <= b.maxX(); x++) {
						if (m.owns(x, y, z)) {
							BlockPos p = new BlockPos(x, y, z);
							desks.add(p);
							if (z == deskZ) {
								deskSolid.add(p);
							}
						}
					}
				}
			}
		}
		for (Workstation w : plan.workstations()) {
			keep.addAll(w.screenCells());
			keep.add(w.computerPos());
			keep.add(w.computerPos().below());
		}
		for (Anchor a : plan.anchors().values()) {
			keep.add(a.block());
			keep.add(a.block().above());
		}
		Anchor wall = plan.anchor(AnchorNames.THREAD_WALL);
		Anchor end = plan.anchor(AnchorNames.THREAD_WALL_END);
		if (wall != null && end != null) {
			Box b = Box.of(wall.block().getX(), wall.block().getY(), wall.block().getZ(), end.block().getX(), end.block().getY(), end.block().getZ());
			for (int x = b.minX(); x <= b.maxX(); x++) {
				for (int y = b.minY(); y <= b.maxY(); y++) {
					for (int z = b.minZ(); z <= b.maxZ(); z++) {
						keep.add(new BlockPos(x, y, z));
					}
				}
			}
		}
		for (Placement p : plan.placements()) {
			// an invisible minecraft:light block (the citadel's own lighting) is a source even if its state calls itself air
			if (!desks.contains(p.rel()) && (!p.state().isAir() || p.state().getLightEmission() > 0)) {
				cur[index(p.rel())] = p.state();
			}
		}
	}

	private int index(BlockPos p) {
		return index(p.getX(), p.getY(), p.getZ());
	}

	private int index(int x, int y, int z) {
		int ix = x - minX;
		int iy = y - minY;
		int iz = z - minZ;
		if (ix < 0 || iy < 0 || iz < 0 || ix >= sx || iy >= sy || iz >= sz) {
			return -1;
		}
		return (iy * sz + iz) * sx + ix;
	}

	/** {@code plan} with its dark walkable cells lit by invisible {@code minecraft:light} blocks in their air. */
	static BuildPlan light(BuildPlan plan, HqStyle style) {
		Anchor spawn = plan.anchor(AnchorNames.SPAWN);
		if (spawn == null) {
			return plan;
		}
		HqLighting l = new HqLighting(plan, style);
		l.run(spawn.block());
		if (l.lamps.isEmpty()) {
			return plan;
		}
		if (l.added.isEmpty()) {
			return plan.replacing(p -> {
				BlockState s = l.lamps.get(p.rel());
				return s == null ? p : new Placement(p.rel(), s);
			});
		}
		List<Placement> out = new ArrayList<>(plan.placements().size() + l.added.size());
		for (Placement p : plan.placements()) {
			BlockState s = l.lamps.get(p.rel());
			out.add(s == null ? p : new Placement(p.rel(), s));
		}
		// new cells in a fixed order (y, z, x), so the plan stays deterministic
		List<BlockPos> extra = new ArrayList<>(l.added.keySet());
		extra.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
		for (BlockPos p : extra) {
			out.add(new Placement(p, l.lamps.get(p)));
		}
		return plan.rebuilt(out, List.of(), l.added);
	}

	/** The walkable cells as the lighting pass estimates them (diagnostics and tests). */
	static Set<BlockPos> walkable(BuildPlan plan, HqStyle style) {
		Anchor spawn = plan.anchor(AnchorNames.SPAWN);
		return spawn == null ? Set.of() : new HqLighting(plan, style).reachable(spawn.block());
	}

	// ------------------------------------------------------------------ the pass

	/** The plan as the pass sees it: desk cells are air (taken or vacant alike), lamps placed so far are in. */
	private BlockState view(BlockPos p) {
		int i = index(p);
		BlockState s = i < 0 ? null : cur[i];
		return s == null ? Blocks.AIR.defaultBlockState() : s;
	}

	/**
	 * The group a walkable cell belongs to: its floor's (so the lamp under it is the group's own), or its
	 * feet cell's when the feet cell holds the walking surface (stairs, slabs, carpets) or nobody owns the floor.
	 */
	private String owner(BlockPos c) {
		String feet = groups.get(c);
		String floor = groups.get(c.below());
		if (floor == null || feet != null && !view(c).isAir()) {
			return feet != null ? feet : floor;
		}
		return floor;
	}

	private void run(BlockPos spawn) {
		Map<String, List<BlockPos>> byGroup = new TreeMap<>();
		for (BlockPos c : reachable(spawn)) {
			if (deskSolid.contains(c)) {
				// a free desk row is opaque to the estimate (a taken one shades): it is lit from the
				// standing row in front of it, which then needs one level more
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos n = c.relative(d);
					if (desks.contains(n) && !deskSolid.contains(n)) {
						need.merge(n, HqLightMap.MIN_LEVEL + 1, Math::max);
					}
				}
				continue;
			}
			String g = owner(c);
			if (g != null) {
				byGroup.computeIfAbsent(g, k -> new ArrayList<>()).add(c);
			}
		}
		Map<String, Box> bounds = new HashMap<>();
		for (Map.Entry<BlockPos, String> e : groups.entrySet()) {
			BlockPos p = e.getKey();
			Box one = new Box(p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ());
			bounds.merge(e.getValue(), one, Box::union);
		}
		// sparse groups (campus grounds: paths and gardens that grow with the roster) get a fixed lattice
		Set<String> sparse = new java.util.TreeSet<>();
		for (BuildPlan.Module m : plan.modules()) {
			if (m.sparse()) {
				sparse.add(m.group());
			}
		}
		for (String g : sparse) {
			latticeGroup(g);
			byGroup.put(g, standable(g));
		}
		for (Map.Entry<String, List<BlockPos>> e : byGroup.entrySet()) {
			Box b = bounds.get(e.getKey());
			for (BlockPos c : e.getValue()) {
				b = b.union(new Box(c.getX(), c.getY(), c.getZ(), c.getX(), c.getY(), c.getZ()));
			}
			lightGroup(e.getKey(), b, e.getValue(), !sparse.contains(e.getKey()));
		}
	}

	/**
	 * Lights of a sparse group, purely by position: an invisible light (level {@value #FILL_MAX}) in the air over
	 * every open floor block of the group on the lattice {@code (x + 5z) mod }{@value #LATTICE13}{@code  == 0}.
	 * Nothing else is looked at, so a path tile keeps its light, or its lack of one, however the grounds around
	 * it grow.
	 */
	private void latticeGroup(String g) {
		BlockState top = invisible(FILL_MAX);
		for (Map.Entry<BlockPos, String> e : groups.entrySet()) {
			BlockPos f = e.getKey();
			if (!g.equals(e.getValue()) || Math.floorMod(f.getX() + 5 * f.getZ(), LATTICE13) != 0 || view(f).isAir()) {
				continue;
			}
			BlockPos k = f.above();
			if (view(k).isAir() && view(k.above()).isAir() && freeAir(g, k)) {
				put(k, top);
			}
		}
	}

	/**
	 * Every cell people could stand on over group {@code g}'s own blocks, reachable or not: what a sparse
	 * group lights, so that a new path elsewhere that makes one of its cells reachable changes nothing.
	 */
	private List<BlockPos> standable(String g) {
		List<BlockPos> out = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (Map.Entry<BlockPos, String> e : groups.entrySet()) {
			if (!g.equals(e.getValue()) || view(e.getKey()).isAir()) {
				continue;
			}
			for (BlockPos c : new BlockPos[] {e.getKey(), e.getKey().above()}) {
				if (!deskSolid.contains(c) && g.equals(owner(c)) && seen.add(c) && !Double.isNaN(floor(c))) {
					out.add(c);
				}
			}
		}
		return out;
	}

	/** Lights the walkable cells {@code walk} of group {@code g} with invisible lights in its own air only. */
	private void lightGroup(String g, Box bounds, List<BlockPos> walk, boolean lattice) {
		int me = groupNames.indexOf(g);
		HqLightMap light = HqLightMap.ofView(bounds, 1, (x, y, z) -> lightView(me, x, y, z));
		walk.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
		Set<BlockPos> walkSet = new HashSet<>(walk);
		Set<Long> walkKeys = new HashSet<>();
		for (BlockPos c : walk) {
			walkKeys.add(c.asLong());
		}
		java.util.function.LongPredicate counts = walkKeys::contains;
		BlockState top = invisible(FILL_MAX);
		// 1. the fill lattice, in the walking cell (or its head), only where something nearby is dark
		if (lattice) {
			for (BlockPos c : walk) {
				if (Math.floorMod(c.getX() + 5 * c.getZ(), LATTICE13) != 0 || !darkNear(light, walkSet, c)) {
					continue;
				}
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos k = c.above(dy);
					if (!freeAir(g, k)) {
						continue;
					}
					int gain = light.gain(k, top, counts, null, 0);
					if (gain > 0) {
						place(light, k, weakest(light, k, gain, counts, null, 0));
					}
					break;
				}
			}
		}
		// 2. what the lattice misses: the free air nearby that lights this cell and the most other dark ones
		for (BlockPos c : walk) {
			int want = need.getOrDefault(c, HqLightMap.MIN_LEVEL);
			if (light.at(c) >= want) {
				continue;
			}
			BlockPos best = null;
			int bestScore = -1;
			for (BlockPos k : candidates(g, walkSet, c)) {
				int score = light.gain(k, top, counts, c, want);
				if (score > bestScore) {
					best = k;
					bestScore = score;
				}
			}
			if (best == null) {
				// nothing of the group's own air reaches it (a stair climbing through another part's floor):
				// the air of a neighbouring part that lights it best
				for (BlockPos k : candidates(null, walkSet, c)) {
					int score = light.gain(k, top, counts, c, want);
					if (score > bestScore) {
						best = k;
						bestScore = score;
					}
				}
			}
			if (best != null) {
				place(light, best, weakest(light, best, bestScore, counts, c, want));
			}
		}
	}

	/** The invisible vanilla light block of {@code level}. */
	private static BlockState invisible(int level) {
		return Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, level);
	}

	/** The weakest level of {@link #LEVELS} that lights at {@code k} what {@link #FILL_MAX} does ({@code must} up to {@code want} when given). */
	private static BlockState weakest(HqLightMap light, BlockPos k, int gainTop, java.util.function.LongPredicate counts,
			@org.jspecify.annotations.Nullable BlockPos must, int want) {
		for (int level : LEVELS) {
			BlockState s = invisible(level);
			if (light.gain(k, s, counts, must, want) >= gainTop) {
				return s;
			}
		}
		return invisible(FILL_MAX);
	}

	/**
	 * A cell as group {@code me}'s light sees it, from the group alone (so a neighbour that comes or goes
	 * changes nothing): its own cells as they are (desk rows opaque, the rest of a desk air), the air over
	 * its own floors (up to {@value #AIRSPACE} cells: where its people walk) as air, everything else opaque.
	 */
	private BlockState lightView(int me, int x, int y, int z) {
		int i = index(x, y, z);
		if (i >= 0 && gid[i] == me) {
			BlockPos p = new BlockPos(x, y, z);
			return deskSolid.contains(p) ? OTHER : cur[i];
		}
		for (int h = 1; h <= AIRSPACE; h++) {
			int b = index(x, y - h, z);
			if (b >= 0 && gid[b] == me && cur[b] != null) {
				return null;
			}
		}
		return OTHER;
	}

	private void place(HqLightMap light, BlockPos p, BlockState lamp) {
		put(p, lamp);
		light.addSource(p, lamp);
	}

	/** Records an invisible light at {@code p} (a new placement, with its owner's group, when nobody had placed anything there). */
	private void put(BlockPos p, BlockState lamp) {
		lamps.put(p, lamp);
		int i = index(p);
		cur[i] = lamp;
		if (plan.at(p) == null) {
			String owner = plan.groupAt(p);
			added.put(p, owner);
			gid[i] = groupNames.indexOf(owner);
		}
	}

	/** True when a walkable cell within 2 steps of {@code c} is still dark (what one lattice light would reach). */
	private static boolean darkNear(HqLightMap light, Set<BlockPos> walk, BlockPos c) {
		int r = 2;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r + Math.abs(dx); dz <= r - Math.abs(dx); dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = c.offset(dx, dy, dz);
					if (walk.contains(n) && light.at(n) < HqLightMap.MIN_LEVEL) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The free air (walking cells and their heads) around a dark cell, within {@value #REACH} blocks, nearest first. */
	private List<BlockPos> candidates(String g, Set<BlockPos> walk, BlockPos c) {
		java.util.LinkedHashSet<BlockPos> out = new java.util.LinkedHashSet<>();
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos n = c.offset(dx, dy, dz);
					if (!walk.contains(n)) {
						continue;
					}
					for (int h = 0; h <= 1; h++) {
						BlockPos k = n.above(h);
						if (freeAir(g, k)) {
							out.add(k);
						}
					}
				}
			}
		}
		List<BlockPos> list = new ArrayList<>(out);
		list.sort(Comparator.comparingInt(k -> k.distManhattan(c)));
		return list;
	}

	/**
	 * A cell an invisible light may take for group {@code g} (any group when null): a plain air placement of
	 * the group, or a cell nobody placed that a module of the group owns and the view sees as open. Never a
	 * desk cell, a screen or computer, an anchor's cell or its head room, nor a cell already lit.
	 */
	private boolean freeAir(@org.jspecify.annotations.Nullable String g, BlockPos p) {
		if (keep.contains(p) || desks.contains(p) || lamps.containsKey(p)) {
			return false;
		}
		int i = index(p);
		if (i < 0) {
			return false;
		}
		Placement pl = plan.at(p);
		if (pl != null) {
			String owner = groups.get(p);
			if (owner == null || g != null && !g.equals(owner)) {
				return false;
			}
			return pl.be() == null && pl.state().getBlock() == Blocks.AIR;
		}
		String owner = plan.groupAt(p);
		if (owner == null || g != null && !g.equals(owner) || groupNames.indexOf(owner) < 0) {
			return false;
		}
		return cur[i] == null;
	}

	// ------------------------------------------------------------------ where people walk (estimate)

	private static final double EPS = 1e-6;

	private VoxelShape shape(int x, int y, int z) {
		BlockPos p = new BlockPos(x, y, z);
		BlockState s = view(p);
		if (s.isAir() || s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock) {
			return net.minecraft.world.phys.shapes.Shapes.empty();
		}
		return s.getCollisionShape(EmptyBlockGetter.INSTANCE, p);
	}

	/** Feet height when standing in {@code c}, or NaN: a floor under the body and two free cells for it. */
	private double floor(BlockPos c) {
		Double f = floors.get(c);
		if (f == null) {
			f = computeFloor(c);
			floors.put(c, f);
		}
		return f;
	}

	private double computeFloor(BlockPos c) {
		int x = c.getX();
		int y = c.getY();
		int z = c.getZ();
		VoxelShape feet = shape(x, y, z);
		double base;
		if (!feet.isEmpty()) {
			double top = feet.max(Direction.Axis.Y);
			if (top > 0.5 + EPS) {
				return Double.NaN;
			}
			base = y + top;
		} else {
			VoxelShape below = shape(x, y - 1, z);
			if (below.isEmpty() || below.max(Direction.Axis.Y) < 0.5 + EPS) {
				return Double.NaN;
			}
			base = y - 1 + Math.min(1, below.max(Direction.Axis.Y));
		}
		VoxelShape head = shape(x, y + 1, z);
		if (!head.isEmpty() && head.min(Direction.Axis.Y) < base - y + 0.8 - EPS) {
			return Double.NaN;
		}
		VoxelShape over = shape(x, y + 2, z);
		if (base > y + EPS && !over.isEmpty() && over.min(Direction.Axis.Y) < base - y - 0.2 - EPS) {
			return Double.NaN;
		}
		return base;
	}

	/**
	 * Cells reachable on foot from {@code start} (steps of at most one block, one block up or down), in
	 * the view where desks are air: the same set whether desks are taken or vacant.
	 */
	private Set<BlockPos> reachable(BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		if (Double.isNaN(floor(start))) {
			return seen;
		}
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		seen.add(start);
		todo.add(start);
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			double fa = floor(p);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = p.relative(d).above(dy);
					if (seen.contains(n) || plan.at(n) == null && plan.at(n.below()) == null) {
						continue;
					}
					double fb = floor(n);
					if (Double.isNaN(fb) || Math.abs(fb - fa) > 1 + EPS) {
						continue;
					}
					// stepping up: the head must clear the cell above the start
					if (fb > fa + 0.5 + EPS && !shape(p.getX(), p.getY() + 2, p.getZ()).isEmpty()) {
						continue;
					}
					seen.add(n);
					todo.add(n);
				}
			}
		}
		return seen;
	}
}
