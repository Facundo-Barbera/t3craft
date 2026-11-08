package dev.t3craft.hq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * fix/hq-lanterns: lighting and decoration validator of a {@link BuildPlan}. Finds
 * <ul>
 *   <li>{@link Kind#FLOATING}: decoration with nothing holding it (a hanging lantern without a solid
 *   block or chain above, a standing one, pot or candle without a floor, a torch or rod off its wall,
 *   a chain whose top does not end in a block);</li>
 *   <li>{@link Kind#OBSTRUCTS}: decoration in a cell people need: desk standing spots, doors and
 *   standing anchors (feet and head), the cell in front of a 3×2 screen or of the kanban wall, the
 *   hologram column, at head height over a walkable cell, or cutting a passage (a 1-wide corridor,
 *   stair or doorway that is only walkable without it);</li>
 *   <li>{@link Kind#EXCESS}: more than {@link #EXCESS_LIGHTS} loose lights (lanterns, torches, candles,
 *   rods) or more than {@link #EXCESS_MAX} decorations (chains not counted) in a 9 × 9 × 9 window;</li>
 *   <li>{@link Kind#DARK}: a cell an NPC or player can reach on foot from the spawn whose block light
 *   at night ({@link HqLightMap}) is under {@link HqLightMap#MIN_LEVEL}. Roofs are not part of the
 *   walking area: a cell standing on the style's roof tiles under the open sky (an eave flush with a
 *   terrace) is counted apart ({@link Report#roofDim}); no mob spawns on those half blocks.</li>
 * </ul>
 * Walkability is the client navigation's own ({@link PlanWalkability}).
 */
final class HqDecorValidator {
	enum Kind {
		FLOATING, OBSTRUCTS, EXCESS, DARK
	}

	record Issue(Kind kind, BlockPos pos, String what) {
		@Override
		public String toString() {
			return kind + " " + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + what;
		}
	}

	record Report(List<Issue> issues, int walkable, int decor, int lights, int roofDim) {
		int count(Kind k) {
			return (int) issues.stream().filter(i -> i.kind() == k).count();
		}

		List<Issue> of(Kind k) {
			return issues.stream().filter(i -> i.kind() == k).toList();
		}

		/** The non-dark issues (decoration). */
		int decorIssues() {
			return issues.size() - count(Kind.DARK);
		}

		String summary() {
			Map<Kind, Integer> m = new EnumMap<>(Kind.class);
			for (Kind k : Kind.values()) {
				m.put(k, count(k));
			}
			return m + " walkable=" + walkable + " decor=" + decor + " lights=" + lights + " roofDim=" + roofDim;
		}
	}

	/** Half side of the excess window (9 × 9 columns). */
	static final int EXCESS_RADIUS = 4;
	/** Most decorations (chains not counted) a 9 × 9 × 9 window may hold. */
	static final int EXCESS_MAX = 8;
	/** Most loose lights a 9 × 9 × 9 window may hold (light belongs in floors, walls and ceilings). */
	static final int EXCESS_LIGHTS = 4;

	private static final Set<String> KIT_DECOR = Set.of("paper_lantern", "stone_lantern", "cherry_planter");

	private HqDecorValidator() {
	}

	/** Lanterns, chains, torches, end rods, candles, pots and the kit's lanterns and planters. */
	static boolean decor(BlockState s) {
		Block b = s.getBlock();
		if (b instanceof LanternBlock || b instanceof ChainBlock || b instanceof BaseTorchBlock || b instanceof EndRodBlock
				|| b instanceof CandleBlock || b instanceof FlowerPotBlock) {
			return true;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(b);
		return "t3craft".equals(id.getNamespace()) && KIT_DECOR.contains(id.getPath());
	}

	static Report check(BuildPlan plan, HqStyle style) {
		List<Issue> out = new ArrayList<>();
		List<Placement> decor = new ArrayList<>();
		int lights = 0;
		for (Placement p : plan.placements()) {
			if (decor(p.state())) {
				decor.add(p);
			}
			if (p.state().getLightEmission() > 0) {
				lights++;
			}
		}
		for (Placement p : decor) {
			String why = floating(plan, p);
			if (why != null) {
				out.add(new Issue(Kind.FLOATING, p.rel(), name(p.state()) + ": " + why));
			}
		}
		obstructions(plan, decor, out);
		excess(decor, out);

		PlanWalkability walk = new PlanWalkability(plan);
		Anchor spawn = plan.anchor(AnchorNames.SPAWN);
		Set<BlockPos> reach = spawn == null ? Set.of() : walk.reachable(spawn.block());
		HqLightMap light = HqLightMap.of(plan);
		List<BlockPos> dark = new ArrayList<>();
		Set<Block> roof = Set.of(style.palette().get(HqStyle.Role.ROOF).getBlock(), style.palette().get(HqStyle.Role.ROOF_TRIM).getBlock(),
				style.palette().get(HqStyle.Role.RIDGE).getBlock());
		int roofDim = 0;
		for (BlockPos c : reach) {
			if (light.at(c) < HqLightMap.MIN_LEVEL) {
				if (onRoof(plan, roof, c)) {
					roofDim++;
				} else {
					dark.add(c);
				}
			}
		}
		dark.sort(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
		for (BlockPos c : dark) {
			out.add(new Issue(Kind.DARK, c, "light " + light.at(c)));
		}
		return new Report(out, reach.size(), decor.size(), lights, roofDim);
	}

	/** Standing on a roof tile with nothing over the head up to the top of the plan. */
	private static boolean onRoof(BuildPlan plan, Set<Block> roof, BlockPos c) {
		if (!roof.contains(state(plan, c.below()).getBlock())) {
			return false;
		}
		for (int y = c.getY() + 1; y <= plan.footprint().maxY(); y++) {
			BlockState s = state(plan, new BlockPos(c.getX(), y, c.getZ()));
			if (!s.isAir()) {
				return false;
			}
		}
		return true;
	}

	private static BlockState state(BuildPlan plan, BlockPos p) {
		Placement pl = plan.at(p);
		return pl == null ? Blocks.AIR.defaultBlockState() : pl.state();
	}

	private static boolean sturdy(BuildPlan plan, BlockPos p, Direction face, SupportType type) {
		BlockState s = state(plan, p);
		return !s.isAir() && s.isFaceSturdy(EmptyBlockGetter.INSTANCE, p, face, type);
	}

	/** Why {@code p} floats, or null when something holds it. */
	private static String floating(BuildPlan plan, Placement p) {
		BlockState s = p.state();
		BlockPos at = p.rel();
		Block b = s.getBlock();
		if (b instanceof ChainBlock) {
			Direction.Axis axis = s.getValue(BlockStateProperties.AXIS);
			if (axis == Direction.Axis.Y) {
				BlockPos top = at;
				while (state(plan, top.above()).getBlock() instanceof ChainBlock) {
					top = top.above();
				}
				return sturdy(plan, top.above(), Direction.DOWN, SupportType.CENTER) ? null : "chain not tied to anything above";
			}
			Direction d = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
			BlockPos a = at;
			while (state(plan, a.relative(d)).getBlock() instanceof ChainBlock) {
				a = a.relative(d);
			}
			BlockPos z = at;
			while (state(plan, z.relative(d.getOpposite())).getBlock() instanceof ChainBlock) {
				z = z.relative(d.getOpposite());
			}
			return state(plan, a.relative(d)).isAir() || state(plan, z.relative(d.getOpposite())).isAir() ? "chain loose at an end" : null;
		}
		if (b instanceof LanternBlock) {
			if (s.getValue(BlockStateProperties.HANGING)) {
				return sturdy(plan, at.above(), Direction.DOWN, SupportType.CENTER) ? null : "hanging with nothing above";
			}
			return sturdy(plan, at.below(), Direction.UP, SupportType.CENTER) ? null : "standing on nothing";
		}
		if (b instanceof WallTorchBlock) {
			Direction f = s.getValue(WallTorchBlock.FACING);
			return sturdy(plan, at.relative(f.getOpposite()), f, SupportType.FULL) ? null : "wall torch without a wall";
		}
		if (b instanceof EndRodBlock) {
			Direction f = s.getValue(EndRodBlock.FACING);
			return state(plan, at.relative(f.getOpposite())).isAir() ? "end rod attached to nothing" : null;
		}
		// torches, candles, pots and the kit's lanterns and planters stand on a floor
		return sturdy(plan, at.below(), Direction.UP, SupportType.CENTER) ? null : "standing on nothing";
	}

	private static void obstructions(BuildPlan plan, List<Placement> decor, List<Issue> out) {
		Set<BlockPos> keep = new HashSet<>();
		for (Anchor a : plan.anchors().values()) {
			if (AnchorNames.standing(a.name())) {
				keep.add(a.block());
				keep.add(a.block().above());
			}
		}
		for (Workstation w : plan.workstations()) {
			keep.add(w.deskAnchor().block());
			keep.add(w.deskAnchor().block().above());
			for (BlockPos c : w.screenCells()) {
				keep.add(c.relative(w.screenFacing()));
			}
		}
		for (Placement p : plan.placements()) {
			if (p.state().getBlock() instanceof DoorBlock) {
				keep.add(p.rel());
				for (Direction d : Direction.Plane.HORIZONTAL) {
					keep.add(p.rel().relative(d));
				}
			}
		}
		Anchor wall = plan.anchor(AnchorNames.THREAD_WALL);
		Anchor wallEnd = plan.anchor(AnchorNames.THREAD_WALL_END);
		if (wall != null && wallEnd != null) {
			Box b = Box.of(wall.block().getX(), wall.block().getY(), wall.block().getZ(),
					wallEnd.block().getX(), wallEnd.block().getY(), wallEnd.block().getZ());
			for (int x = b.minX(); x <= b.maxX(); x++) {
				for (int y = b.minY(); y <= b.maxY(); y++) {
					for (int z = b.minZ(); z <= b.maxZ(); z++) {
						keep.add(new BlockPos(x, y, z).relative(wall.facing()));
					}
				}
			}
		}
		Anchor holo = plan.anchor(AnchorNames.HOLOGRAM);
		Anchor hub = plan.anchor(AnchorNames.HUB);
		if (holo != null) {
			int y0 = hub != null && hub.block().getX() == holo.block().getX() && hub.block().getZ() == holo.block().getZ()
					? hub.block().getY() : holo.block().getY() - 4;
			for (int y = y0; y <= holo.block().getY() + 1; y++) {
				keep.add(new BlockPos(holo.block().getX(), y, holo.block().getZ()));
			}
		}

		// walkability without any decoration: where people could go if nothing was in the way
		BuildPlan bare = plan.replacing(p -> decor(p.state()) ? new Placement(p.rel(), Blocks.AIR.defaultBlockState()) : p);
		PlanWalkability walk = new PlanWalkability(bare);
		Anchor spawn = plan.anchor(AnchorNames.SPAWN);
		Set<BlockPos> reach = spawn == null ? Set.of() : walk.reachable(spawn.block());
		for (Placement p : decor) {
			BlockPos d = p.rel();
			String name = name(p.state());
			if (keep.contains(d)) {
				out.add(new Issue(Kind.OBSTRUCTS, d, name + ": in a desk, door, anchor, screen, kanban or hologram cell"));
			} else if (reach.contains(d.below()) && !(p.state().getBlock() instanceof ChainBlock && reach.contains(d))) {
				out.add(new Issue(Kind.OBSTRUCTS, d, name + ": at head height over a walkable cell"));
			} else if (reach.contains(d) && cutsPassage(walk, reach, d)) {
				out.add(new Issue(Kind.OBSTRUCTS, d, name + ": blocks a passage"));
			}
		}
	}

	/** True when the walkable neighbours of {@code c} only connect to each other through {@code c} (nearby). */
	private static boolean cutsPassage(PlanWalkability walk, Set<BlockPos> reach, BlockPos c) {
		List<BlockPos> nbrs = new ArrayList<>();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int dy = -1; dy <= 1; dy++) {
				BlockPos n = c.relative(d).above(dy);
				if (reach.contains(n) && walk.step(c, n)) {
					nbrs.add(n);
				}
			}
		}
		if (nbrs.size() < 2) {
			return false;
		}
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		seen.add(nbrs.get(0));
		todo.add(nbrs.get(0));
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = p.relative(d).above(dy);
					if (n.equals(c) || seen.contains(n) || Math.abs(n.getX() - c.getX()) > 2 || Math.abs(n.getZ() - c.getZ()) > 2
							|| Math.abs(n.getY() - c.getY()) > 2 || !reach.contains(n) || !walk.step(p, n)) {
						continue;
					}
					seen.add(n);
					todo.add(n);
				}
			}
		}
		return !seen.containsAll(nbrs);
	}

	private static void excess(List<Placement> decor, List<Issue> out) {
		List<Placement> counted = new ArrayList<>();
		for (Placement p : decor) {
			if (!(p.state().getBlock() instanceof ChainBlock)) {
				counted.add(p);
			}
		}
		for (Placement c : counted) {
			int n = 0;
			int lights = 0;
			for (Placement o : counted) {
				if (Math.abs(o.rel().getX() - c.rel().getX()) <= EXCESS_RADIUS && Math.abs(o.rel().getZ() - c.rel().getZ()) <= EXCESS_RADIUS
						&& Math.abs(o.rel().getY() - c.rel().getY()) <= EXCESS_RADIUS) {
					n++;
					if (o.state().getLightEmission() > 0) {
						lights++;
					}
				}
			}
			if (lights > EXCESS_LIGHTS && c.state().getLightEmission() > 0) {
				out.add(new Issue(Kind.EXCESS, c.rel(), lights + " loose lights in the 9×9×9 window around it"));
			} else if (n > EXCESS_MAX) {
				out.add(new Issue(Kind.EXCESS, c.rel(), n + " decorations in the 9×9×9 window around it"));
			}
		}
	}

	private static String name(BlockState s) {
		String n = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		return s.hasProperty(BlockStateProperties.HANGING) && s.getValue(BlockStateProperties.HANGING) ? n + "[hanging]" : n;
	}
}
