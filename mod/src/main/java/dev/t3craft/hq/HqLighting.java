package dev.t3craft.hq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   the hub and stable while the HQ grows), only where some walkable cell nearby is dark;</li>
 *   <li>then, for each cell still dark, the floor, wall or ceiling block nearby that lights the most
 *   dark cells.</li>
 * </ol>
 * Only plain opaque full blocks are swapped (never screens, desks, the board, signs or anything an
 * anchor names), so walkability, anchors and module ownership stay exactly as generated. Pure and
 * deterministic: the same plan gives the same lit plan.
 */
final class HqLighting {
	/** Spacing of the floor-lamp grid: every cell of a grid square is within 4 steps of a lamp. */
	static final int GRID = 5;
	/** Farthest a fallback lamp may be from the dark cell it is for (blocks, each axis). */
	private static final int REACH = 4;

	private final BuildPlan plan;
	private final HqStyle style;
	private final HqLightMap light;
	private final Set<BlockPos> keep = new HashSet<>();
	private final Set<BlockState> fixtures = new HashSet<>();
	private final Map<BlockPos, BlockState> lamps = new HashMap<>();
	private final Map<BlockPos, Double> floors = new HashMap<>();

	private HqLighting(BuildPlan plan, HqStyle style) {
		this.plan = plan;
		this.style = style;
		this.light = HqLightMap.of(plan);
		for (Anchor a : plan.anchors().values()) {
			keep.add(a.block());
		}
		for (Workstation w : plan.workstations()) {
			keep.addAll(w.screenCells());
			keep.add(w.computerPos());
			keep.add(w.computerPos().below());
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
		for (HqStyle.Role r : List.of(HqStyle.Role.BOARD, HqStyle.Role.SCREEN, HqStyle.Role.COMPUTER_ON, HqStyle.Role.COMPUTER_OFF,
				HqStyle.Role.STATUS_LAMP, HqStyle.Role.BEACON, HqStyle.Role.DESK, HqStyle.Role.TERMINAL)) {
			BlockState s = style.palette().get(r);
			if (s != null) {
				fixtures.add(s);
			}
		}
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

	private void run(BlockPos spawn) {
		List<BlockPos> walk = new ArrayList<>(reachable(spawn));
		walk.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
		Set<BlockPos> walkSet = new HashSet<>(walk);
		BlockState floorLamp = style.floorLight();
		// 1. the floor grid
		for (BlockPos c : walk) {
			if (Math.floorMod(c.getX(), GRID) == 0 && Math.floorMod(c.getZ(), GRID) == 0 && floorSlot(c) && darkNear(walkSet, c)) {
				place(c.below(), floorLamp);
			}
		}
		// 2. what the grid misses (stairs, narrow rooms, cells whose grid point is a wall or a desk): the
		// nearby floor, wall or ceiling block that lights this cell and the most other dark ones
		BlockState wallLamp = style.wallLight();
		Set<Long> walkKeys = new HashSet<>();
		for (BlockPos c : walk) {
			walkKeys.add(c.asLong());
		}
		for (BlockPos c : walk) {
			if (light.at(c) >= HqLightMap.MIN_LEVEL) {
				continue;
			}
			BlockPos best = null;
			BlockState bestState = null;
			int bestScore = -1;
			for (BlockPos k : candidates(walkSet, c)) {
				BlockState lamp = k.getY() < c.getY() && walkSet.contains(k.above()) ? floorLamp : wallLamp;
				int score = light.gain(k, lamp, walkKeys::contains, c);
				if (score > bestScore) {
					best = k;
					bestState = lamp;
					bestScore = score;
				}
			}
			if (best != null) {
				place(best, bestState);
			}
		}
	}

	private void place(BlockPos p, BlockState lamp) {
		lamps.put(p, lamp);
		light.addSource(p, lamp);
	}

	/** True when {@code c}'s floor is a plain block the feet cell leaves open to the light. */
	private boolean floorSlot(BlockPos c) {
		return light.state(c.getX(), c.getY(), c.getZ()).isAir() && swappable(c.below(), true);
	}

	private boolean darkNear(Set<BlockPos> walk, BlockPos c) {
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
	 * Floors, walls (feet and head level) and ceilings of the walkable cells around a dark cell,
	 * nearest first.
	 */
	private List<BlockPos> candidates(Set<BlockPos> walk, BlockPos c) {
		java.util.LinkedHashSet<BlockPos> out = new java.util.LinkedHashSet<>();
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos n = c.offset(dx, dy, dz);
					if (!walk.contains(n)) {
						continue;
					}
					if (floorSlot(n)) {
						out.add(n.below());
					}
					for (int h = 0; h <= 2; h++) {
						for (Direction d : Direction.Plane.HORIZONTAL) {
							BlockPos w = n.above(h).relative(d);
							if (swappable(w, false)) {
								out.add(w);
							}
						}
					}
					for (int h = 2; h <= 6; h++) {
						BlockPos up = n.above(h);
						if (!light.state(up.getX(), up.getY(), up.getZ()).isAir()) {
							if (swappable(up, false)) {
								out.add(up);
							}
							break;
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
	 * A plain opaque full block of the plan (or a leaf block of a canopy people can climb onto) that may
	 * become a lamp. Walls and ceilings never swap a block that looks like a fixture (board, screens,
	 * desks, lamps), wherever it is.
	 */
	private boolean swappable(BlockPos p, boolean floor) {
		if (keep.contains(p) || lamps.containsKey(p)) {
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
		boolean full = s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, p);
		return s.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock
				|| full && (s.isSolidRender() || floor && s.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock);
	}

	// ------------------------------------------------------------------ where people walk (estimate)

	private static final double EPS = 1e-6;

	private VoxelShape shape(int x, int y, int z) {
		BlockState s = light.state(x, y, z);
		if (s.isAir() || s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock) {
			return net.minecraft.world.phys.shapes.Shapes.empty();
		}
		return s.getCollisionShape(EmptyBlockGetter.INSTANCE, new BlockPos(x, y, z));
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

	/** Cells reachable on foot from {@code start}: steps of at most one block, one block up or down. */
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
