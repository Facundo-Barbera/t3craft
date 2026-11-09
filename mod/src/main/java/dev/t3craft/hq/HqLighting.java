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
 * fix/hq-lanterns: built-in lighting of every HQ plan. After a layout is generated, every cell people
 * can walk to from the spawn should read at least {@link HqLightMap#MIN_LEVEL} of block light at
 * night. Where it does not, lamps are let into the building itself, never added as loose decoration:
 * <ol>
 *   <li>a grid of floor lamps every {@value #GRID} blocks (plan coordinates, so it is symmetric about
 *   the hub), only where some walkable cell nearby is dark;</li>
 *   <li>then, for each cell still dark, the floor, wall or ceiling block nearby that lights it and the
 *   most other dark cells (exact flood fill).</li>
 * </ol>
 *
 * <p><b>Local to each part.</b> The HQ grows and shrinks by groups ({@link BuildPlan#groups()}) and its
 * diffs must touch only the parts that changed, so every group is lit <b>on its own</b>: it only counts
 * its own lamps, cells of other groups read as opaque (their light never helps, so the real HQ is at
 * least as bright as the estimate), and lamps only go into the group's own blocks. Desk cells (the
 * desk modules above their floor, every screen and desk top) read as air whether the desk is taken or
 * vacant, so taking or freeing a desk never moves a lamp.
 *
 * <p>Only plain opaque full blocks are swapped (plus glass floor tiles and leaves of canopies people
 * can climb onto), never screens, desks, the board, signs or anything an anchor names, so walkability,
 * anchors and module ownership stay exactly as generated. Pure and deterministic.
 */
final class HqLighting {
	/** Spacing of the floor-lamp grid: every cell of a grid square is within 4 steps of a lamp. */
	static final int GRID = 5;
	/** Farthest a fallback lamp may be from the dark cell it is for (blocks, each axis). */
	private static final int REACH = 4;
	/** Period of the sparse groups' floor-lamp lattice. */
	static final int LATTICE = 9;
	/** How far above a group's own blocks its light sees air (feet, head and one more). */
	private static final int AIRSPACE = 3;
	/** What a cell of another group is to a group's light: opaque. */
	private static final BlockState OTHER = Blocks.STONE.defaultBlockState();

	private final BuildPlan plan;
	private final HqStyle style;
	/** feat/hq-layout-packs: {@link #GRID} and {@link #LATTICE} as the layout's pack sets them (clamped, never sparser than the code's). */
	private final int grid;
	private final int lattice;
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
	private final Set<BlockState> fixtures = new HashSet<>();
	/** The board's face block and centre: the same block elsewhere (plain walls) may become a lamp. */
	private final BlockState boardState;
	private final BlockPos board;
	private final Map<BlockPos, BlockState> lamps = new HashMap<>();
	private final Map<BlockPos, Double> floors = new HashMap<>();
	/** Walkable cells that need more than {@link HqLightMap#MIN_LEVEL} (standing rows in front of free desks). */
	private final Map<BlockPos, Integer> need = new HashMap<>();

	private HqLighting(BuildPlan plan, HqStyle style) {
		this.plan = plan;
		this.style = style;
		String layoutId = PackApply.layoutOf(style);
		this.grid = dev.t3craft.hq.pack.LayoutParams.LIGHT_GRID.get(layoutId);
		this.lattice = dev.t3craft.hq.pack.LayoutParams.LIGHT_LATTICE.get(layoutId);
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
		boardState = style.palette().get(HqStyle.Role.BOARD);
		Anchor boardAnchor = plan.anchor(AnchorNames.BOARD);
		board = boardAnchor == null ? null : boardAnchor.block();
		for (HqStyle.Role r : List.of(HqStyle.Role.SCREEN, HqStyle.Role.COMPUTER_ON, HqStyle.Role.COMPUTER_OFF,
				HqStyle.Role.STATUS_LAMP, HqStyle.Role.BEACON, HqStyle.Role.DESK, HqStyle.Role.TERMINAL)) {
			BlockState s = style.palette().get(r);
			if (s != null) {
				fixtures.add(s);
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

	/** {@code plan} with its dark walkable cells lit by lamps let into floors, walls and ceilings. */
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
		return plan.replacing(p -> {
			BlockState s = l.lamps.get(p.rel());
			return s == null ? p : new Placement(p.rel(), s);
		});
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
	 * Lamps of a sparse group, purely by position: every open floor block of the group on the lattice
	 * {@code (x - 2z) mod }{@value #LATTICE}{@code  == 0} (one every {@value #LATTICE} blocks in every row
	 * and every column, so any straight path is within 4 steps of one). Nothing else is looked at, so a
	 * path tile keeps its lamp, or its lack of one, however the grounds around it grow.
	 */
	private void latticeGroup(String g) {
		BlockState floorLamp = style.floorLight();
		for (Map.Entry<BlockPos, String> e : groups.entrySet()) {
			BlockPos f = e.getKey();
			if (!g.equals(e.getValue()) || Math.floorMod(f.getX() - 2 * f.getZ(), lattice) != 0) {
				continue;
			}
			if (view(f.above()).isAir() && view(f.above(2)).isAir() && swappable(g, f, true)) {
				lamps.put(f, floorLamp);
				cur[index(f)] = floorLamp;
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

	/** Lights the walkable cells {@code walk} of group {@code g} with its own lamps only. */
	private void lightGroup(String g, Box bounds, List<BlockPos> walk, boolean grid) {
		int me = groupNames.indexOf(g);
		HqLightMap light = HqLightMap.ofView(bounds, 1, (x, y, z) -> lightView(me, x, y, z));
		walk.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
		Set<BlockPos> walkSet = new HashSet<>(walk);
		Set<Long> walkKeys = new HashSet<>();
		for (BlockPos c : walk) {
			walkKeys.add(c.asLong());
		}
		BlockState floorLamp = style.floorLight();
		BlockState wallLamp = style.wallLight();
		// 1. the floor grid
		for (BlockPos c : walk) {
			if (grid && Math.floorMod(c.getX(), this.grid) == 0 && Math.floorMod(c.getZ(), this.grid) == 0 && floorSlot(g, c) && darkNear(light, walkSet, c)) {
				place(light, c.below(), floorLamp);
			}
		}
		// 2. what the grid misses (stairs, narrow rooms, cells whose grid point is a wall or a desk): the
		// nearby floor, wall or ceiling block that lights this cell and the most other dark ones
		for (BlockPos c : walk) {
			int want = need.getOrDefault(c, HqLightMap.MIN_LEVEL);
			if (light.at(c) >= want) {
				continue;
			}
			BlockPos best = null;
			BlockState bestState = null;
			int bestScore = -1;
			for (BlockPos k : candidates(g, walkSet, c)) {
				BlockState lamp = k.getY() < c.getY() && walkSet.contains(k.above()) ? floorLamp : wallLamp;
				int score = light.gain(k, lamp, walkKeys::contains, c, want);
				if (score > bestScore) {
					best = k;
					bestState = lamp;
					bestScore = score;
				}
			}
			if (best == null) {
				// nothing of the group's own reaches it (a stair climbing through another part's floor):
				// the block of a neighbouring part that lights it best
				for (BlockPos k : candidates(null, walkSet, c)) {
					BlockState lamp = k.getY() < c.getY() && walkSet.contains(k.above()) ? floorLamp : wallLamp;
					int score = light.gain(k, lamp, walkKeys::contains, c, want);
					if (score > bestScore) {
						best = k;
						bestState = lamp;
						bestScore = score;
					}
				}
			}
			if (best != null) {
				place(light, best, bestState);
			}
		}
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
		lamps.put(p, lamp);
		cur[index(p)] = lamp;
		light.addSource(p, lamp);
	}

	/** True when {@code c}'s floor is a plain block of group {@code g} the feet cell leaves open to the light. */
	private boolean floorSlot(String g, BlockPos c) {
		return view(c).isAir() && swappable(g, c.below(), true);
	}

	private static boolean darkNear(HqLightMap light, Set<BlockPos> walk, BlockPos c) {
		int r = GRID - 1;
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

	/**
	 * Floors, walls (feet to two above) and ceilings of the group's walkable cells around a dark cell,
	 * nearest first.
	 */
	private List<BlockPos> candidates(String g, Set<BlockPos> walk, BlockPos c) {
		java.util.LinkedHashSet<BlockPos> out = new java.util.LinkedHashSet<>();
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (int dy = -4; dy <= 2; dy++) {
					BlockPos n = c.offset(dx, dy, dz);
					if (!walk.contains(n)) {
						continue;
					}
					if (floorSlot(g, n)) {
						out.add(n.below());
					}
					for (int h = -1; h <= 2; h++) {
						for (Direction d : Direction.Plane.HORIZONTAL) {
							BlockPos w = n.above(h).relative(d);
							if (swappable(g, w, false)) {
								out.add(w);
							}
						}
					}
					for (int h = 2; h <= 6; h++) {
						BlockPos up = n.above(h);
						if (!view(up).isAir()) {
							if (swappable(g, up, false)) {
								out.add(up);
							}
							break;
						}
					}
				}
			}
		}
		// ceilings over the dark cell's surroundings (stairwells and voids have no walkable cell under them)
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (int h = 2; h <= 6; h++) {
					BlockPos up = c.offset(dx, h, dz);
					if (!view(up).isAir()) {
						if (swappable(g, up, false)) {
							out.add(up);
						}
						break;
					}
				}
			}
		}
		List<BlockPos> list = new ArrayList<>(out);
		list.sort(Comparator.comparingInt(k -> k.distManhattan(c)));
		return list;
	}

	/**
	 * A plain opaque full block of group {@code g} (any group when null) (or a leaf block of a canopy people can climb onto)
	 * that may become a lamp. Never a desk cell; walls and ceilings never swap a block that looks like a
	 * fixture (board, screens, desks, lamps), wherever it is.
	 */
	private boolean swappable(String g, BlockPos p, boolean floor) {
		String owner = groups.get(p);
		if (keep.contains(p) || desks.contains(p) || lamps.containsKey(p) || owner == null || g != null && !g.equals(owner)) {
			return false;
		}
		Placement pl = plan.at(p);
		if (pl == null || pl.be() != null) {
			return false;
		}
		BlockState s = pl.state();
		if (s.isAir() || s.hasBlockEntity() || s.getLightEmission() > 0 || !floor && fixtures.contains(s)) {
			return false;
		}
		if (!floor && s.equals(boardState) && (board == null || p.distManhattan(board) <= 8)) {
			return false; // the board's face
		}
		boolean full = s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, p);
		return s.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock
				|| full && (s.isSolidRender() || floor && s.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock);
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
