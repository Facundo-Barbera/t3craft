package dev.t3craft.hq;

import dev.t3craft.blocks.KitGlowStripBlock;
import dev.t3craft.blocks.KitRailingBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * fix/plan-connect: bakes the connection shapes of fences, walls, panes/bars and stairs into a plan,
 * deterministically, from the final cell map (position → state) alone. No world, no proxy, no
 * {@code updateShape}: the rules are vanilla's ({@code FenceBlock.connectsTo}, {@code WallBlock},
 * {@code IronBarsBlock.attachsTo}, {@code StairBlock.getStairsShape}, read from the 26.3 sources)
 * written out here, so nothing can fail silently.
 *
 * <ul>
 * <li><b>Fences</b> ({@link FenceBlock}): connect to a fence of the same family (wooden with wooden;
 *     nether brick only with nether brick), a {@link FenceGateBlock} whose axis is perpendicular to the
 *     direction, or a neighbour whose touching face is sturdy ({@link #sturdy}).</li>
 * <li><b>Panes and bars</b> ({@link IronBarsBlock}, stained panes included): connect to panes/bars,
 *     walls and sturdy faces.</li>
 * <li><b>Walls</b> ({@link WallBlock}): connect to walls, perpendicular gates, panes/bars and sturdy
 *     faces. A connected side is {@code TALL} when the collision shape of the block above covers that
 *     side's column (a full cube, or a wall connected over it), else {@code LOW}. {@code UP}: vanilla's
 *     {@code shouldRaisePost} (post above, not a straight run, or something on top that needs it).</li>
 * <li><b>Stairs</b> ({@link StairBlock#SHAPE}): vanilla's {@code getStairsShape} from the stairs behind
 *     and in front with the same {@code HALF}.</li>
 * <li><b>Kit railings</b> ({@link KitRailingBlock}, kit/cherry-railing): the block's own rule
 *     ({@link KitRailingBlock#resolve}): continuous runs, posts at ends and turns, closed corners and
 *     bridged steps, from the {@code FACING} of the railings around (pure, one pass).</li>
 * <li><b>Kit glow strips</b> ({@link KitGlowStripBlock}): the leg of an inner corner, from the strip in front
 *     ({@link KitGlowStripBlock#resolve}).</li>
 * </ul>
 *
 * "Sturdy" is {@code isFaceSturdy(EmptyBlockGetter, ZERO, face)} (what vanilla asks, a pure function of
 * the state's shape) minus vanilla's connection exceptions (leaves, barrier, pumpkins, melon, shulker
 * boxes). Block tags are not used (they may be unbound when a plan is generated headless), so the
 * families are told apart by class. {@code WATERLOGGED} is never touched. Cells outside the map read as air.
 *
 * <p>Order: stairs first (their shape changes which faces are sturdy), then fences and panes, then walls
 * from the top down (a wall reads the wall above it).
 */
public final class PlanConnect {
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	/** Vanilla's wall test shapes: the post column and each side's arm. */
	private static final VoxelShape TEST_POST = Block.column(2.0, 0.0, 16.0);
	private static final Map<Direction, VoxelShape> TEST_SIDE = Shapes.rotateHorizontal(Block.boxZ(2.0, 16.0, 0.0, 9.0));

	private PlanConnect() {
	}

	/** True for the blocks this pass shapes. */
	public static boolean shaped(BlockState s) {
		Block b = s.getBlock();
		return b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock || b instanceof KitRailingBlock
				|| b instanceof KitGlowStripBlock;
	}

	/** True for the blocks that connect sideways (fences, walls, panes, bars). */
	public static boolean connecting(BlockState s) {
		Block b = s.getBlock();
		return b instanceof FenceBlock || b instanceof IronBarsBlock || b instanceof WallBlock;
	}

	// ------------------------------------------------------------------ entry points

	/** {@link #apply(Map)} on a finished plan: the shaped placements get their resolved state, positions and order unchanged. */
	public static BuildPlan apply(BuildPlan plan) {
		Map<Long, BlockState> cells = cells(plan);
		Map<Long, BlockState> before = new HashMap<>(cells);
		apply(cells);
		return plan.replacing(p -> {
			long k = p.rel().asLong();
			BlockState fin = before.get(k);
			// only the placement that holds the cell's final state (the last one at that position) is reshaped
			if (fin != p.state() || !shaped(p.state())) {
				return p;
			}
			BlockState u = cells.get(k);
			return u == p.state() ? p : new Placement(p.rel(), u, p.be());
		});
	}

	/** Resolves, in place, every shaped cell of {@code cells} (packed {@link BlockPos#asLong()} keys). */
	public static void apply(Map<Long, BlockState> cells) {
		List<Long> stairs = new ArrayList<>();
		List<Long> sides = new ArrayList<>();
		List<Long> walls = new ArrayList<>();
		List<Long> railings = new ArrayList<>();
		List<Long> strips = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : cells.entrySet()) {
			Block b = e.getValue().getBlock();
			if (b instanceof StairBlock) {
				stairs.add(e.getKey());
			} else if (b instanceof FenceBlock || b instanceof IronBarsBlock) {
				sides.add(e.getKey());
			} else if (b instanceof WallBlock) {
				walls.add(e.getKey());
			} else if (b instanceof KitRailingBlock) {
				railings.add(e.getKey());
			} else if (b instanceof KitGlowStripBlock) {
				strips.add(e.getKey());
			}
		}
		// glow strips read only the facing/half of the strip in front, which this pass never changes
		Map<Long, BlockState> stripOut = new HashMap<>();
		for (long k : strips) {
			BlockState s = cells.get(k);
			BlockPos p = BlockPos.of(k);
			BlockState u = KitGlowStripBlock.resolve(s, at(cells, p.relative(s.getValue(KitGlowStripBlock.FACING))));
			if (u != s) {
				stripOut.put(k, u);
			}
		}
		cells.putAll(stripOut);
		// kit railings read only the FACING of the railings around, which this pass never changes
		Map<Long, BlockState> railOut = new HashMap<>();
		for (long k : railings) {
			BlockState s = cells.get(k);
			BlockPos p = BlockPos.of(k);
			BlockState u = KitRailingBlock.resolve(s, (dx, dz) -> at(cells, p.offset(dx, 0, dz)));
			if (u != s) {
				railOut.put(k, u);
			}
		}
		cells.putAll(railOut);
		// stairs read only the facing/half of their neighbours, so one pass over the original states is exact
		Map<Long, BlockState> stairOut = new HashMap<>();
		for (long k : stairs) {
			BlockState s = cells.get(k);
			StairsShape shape = stairShape(cells, BlockPos.of(k), s);
			if (s.getValue(StairBlock.SHAPE) != shape) {
				stairOut.put(k, s.setValue(StairBlock.SHAPE, shape));
			}
		}
		cells.putAll(stairOut);
		// fences and panes read neighbour identity and sturdy faces, which their own connections never change
		Map<Long, BlockState> sideOut = new HashMap<>();
		for (long k : sides) {
			BlockState s = cells.get(k);
			BlockState u = crossState(cells, BlockPos.of(k), s);
			if (u != s) {
				sideOut.put(k, u);
			}
		}
		cells.putAll(sideOut);
		// walls top-down: each one sees the resolved wall above it
		walls.sort(Comparator.comparingInt((Long k) -> BlockPos.getY(k)).reversed().thenComparingLong(k -> k));
		for (long k : walls) {
			BlockState s = cells.get(k);
			BlockState u = wallState(cells, BlockPos.of(k), s);
			if (u != s) {
				cells.put(k, u);
			}
		}
	}

	// ------------------------------------------------------------------ rules

	private static BlockState at(Map<Long, BlockState> cells, BlockPos p) {
		BlockState s = cells.get(p.asLong());
		return s == null ? AIR : s;
	}

	/** Vanilla's {@code Block.isExceptionForConnection}, by class/identity instead of tags. */
	static boolean exception(BlockState s) {
		Block b = s.getBlock();
		return b instanceof LeavesBlock || b instanceof ShulkerBoxBlock || b == Blocks.BARRIER || b == Blocks.CARVED_PUMPKIN
				|| b == Blocks.JACK_O_LANTERN || b == Blocks.MELON || b == Blocks.PUMPKIN;
	}

	/** The face of {@code s} that looks {@code face} is a full sturdy face, and {@code s} is no connection exception. */
	static boolean sturdy(BlockState s, Direction face) {
		return !s.isAir() && !exception(s) && s.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face);
	}

	private static boolean gateAcross(BlockState n, Direction dir) {
		return n.getBlock() instanceof FenceGateBlock && n.getValue(FenceGateBlock.FACING).getAxis() == dir.getClockWise().getAxis();
	}

	private static boolean netherFence(Block b) {
		return b == Blocks.NETHER_BRICK_FENCE;
	}

	/**
	 * True when {@code self} (a fence, pane/bars or wall) connects towards {@code dir} to {@code n}, the
	 * neighbour on that side.
	 */
	public static boolean connectsTo(BlockState self, BlockState n, Direction dir) {
		Block sb = self.getBlock();
		Block nb = n.getBlock();
		boolean solid = sturdy(n, dir.getOpposite());
		if (sb instanceof FenceBlock) {
			boolean sameFence = nb instanceof FenceBlock && netherFence(nb) == netherFence(sb);
			return sameFence || gateAcross(n, dir) || solid;
		}
		if (sb instanceof IronBarsBlock) {
			return nb instanceof IronBarsBlock || nb instanceof WallBlock || solid;
		}
		if (sb instanceof WallBlock) {
			return nb instanceof WallBlock || nb instanceof IronBarsBlock || gateAcross(n, dir) || solid;
		}
		return false;
	}

	private static BlockState crossState(Map<Long, BlockState> cells, BlockPos pos, BlockState s) {
		BlockState u = s;
		for (Direction d : HORIZONTAL) {
			BooleanProperty prop = CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d);
			u = u.setValue(prop, connectsTo(s, at(cells, pos.relative(d)), d));
		}
		return u;
	}

	/** Blocks that raise a wall's post when they stand on it (vanilla's {@code #wall_post_override}, by class). */
	private static boolean postOverride(BlockState above) {
		Block b = above.getBlock();
		return b instanceof BaseTorchBlock || b instanceof StandingSignBlock || b instanceof BannerBlock || b instanceof BasePressurePlateBlock;
	}

	private static boolean covered(VoxelShape aboveFace, VoxelShape test) {
		return !Shapes.joinIsNotEmpty(test, aboveFace, BooleanOp.ONLY_FIRST);
	}

	private static BlockState wallState(Map<Long, BlockState> cells, BlockPos pos, BlockState s) {
		BlockState above = at(cells, pos.above());
		VoxelShape aboveFace = above.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).getFaceShape(Direction.DOWN);
		BlockState u = s;
		for (Direction d : HORIZONTAL) {
			EnumProperty<WallSide> prop = WallBlock.PROPERTY_BY_DIRECTION.get(d);
			WallSide side = !connectsTo(s, at(cells, pos.relative(d)), d) ? WallSide.NONE
					: covered(aboveFace, TEST_SIDE.get(d)) ? WallSide.TALL : WallSide.LOW;
			u = u.setValue(prop, side);
		}
		return u.setValue(WallBlock.UP, raisePost(u, above, aboveFace));
	}

	/** Vanilla's {@code WallBlock.shouldRaisePost}. */
	private static boolean raisePost(BlockState s, BlockState above, VoxelShape aboveFace) {
		if (above.getBlock() instanceof WallBlock && above.getValue(WallBlock.UP)) {
			return true;
		}
		WallSide n = s.getValue(WallBlock.NORTH);
		WallSide so = s.getValue(WallBlock.SOUTH);
		WallSide e = s.getValue(WallBlock.EAST);
		WallSide w = s.getValue(WallBlock.WEST);
		boolean nn = n == WallSide.NONE;
		boolean sn = so == WallSide.NONE;
		boolean en = e == WallSide.NONE;
		boolean wn = w == WallSide.NONE;
		if (nn && sn && wn && en || nn != sn || wn != en) {
			return true;
		}
		boolean tall = n == WallSide.TALL && so == WallSide.TALL || e == WallSide.TALL && w == WallSide.TALL;
		return !tall && (postOverride(above) || covered(aboveFace, TEST_POST));
	}

	/** Vanilla's {@code StairBlock.getStairsShape}. */
	static StairsShape stairShape(Map<Long, BlockState> cells, BlockPos pos, BlockState s) {
		Direction facing = s.getValue(StairBlock.FACING);
		Half half = s.getValue(StairBlock.HALF);
		BlockState behind = at(cells, pos.relative(facing));
		if (behind.getBlock() instanceof StairBlock && behind.getValue(StairBlock.HALF) == half) {
			Direction bf = behind.getValue(StairBlock.FACING);
			if (bf.getAxis() != facing.getAxis() && canTakeShape(cells, pos, s, bf.getOpposite())) {
				return bf == facing.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
			}
		}
		BlockState front = at(cells, pos.relative(facing.getOpposite()));
		if (front.getBlock() instanceof StairBlock && front.getValue(StairBlock.HALF) == half) {
			Direction ff = front.getValue(StairBlock.FACING);
			if (ff.getAxis() != facing.getAxis() && canTakeShape(cells, pos, s, ff)) {
				return ff == facing.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
			}
		}
		return StairsShape.STRAIGHT;
	}

	private static boolean canTakeShape(Map<Long, BlockState> cells, BlockPos pos, BlockState s, Direction side) {
		BlockState n = at(cells, pos.relative(side));
		return !(n.getBlock() instanceof StairBlock) || n.getValue(StairBlock.FACING) != s.getValue(StairBlock.FACING)
				|| n.getValue(StairBlock.HALF) != s.getValue(StairBlock.HALF);
	}

	// ------------------------------------------------------------------ check (hq-render report)

	/** A fence, wall or pane with no connection at all although a neighbour it connects to is beside it. */
	public record Isolated(BlockPos pos, BlockState state, Direction towards, BlockState neighbour) {
	}

	/**
	 * Every fence/wall/pane of {@code plan} that is connected on no side although at least one horizontal
	 * neighbour qualifies under {@link #connectsTo}. Zero once {@link #apply} has run on the final plan.
	 * Nothing is caught: an error here is an error of the check.
	 */
	public static List<Isolated> isolated(BuildPlan plan) {
		Map<Long, BlockState> cells = cells(plan);
		List<Isolated> out = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : cells.entrySet()) {
			BlockState s = e.getValue();
			if (!connecting(s) || anySide(s)) {
				continue;
			}
			BlockPos p = BlockPos.of(e.getKey());
			for (Direction d : HORIZONTAL) {
				BlockState n = at(cells, p.relative(d));
				if (connectsTo(s, n, d)) {
					out.add(new Isolated(p, s, d, n));
					break;
				}
			}
		}
		out.sort(Comparator.comparingLong(i -> i.pos().asLong()));
		return out;
	}

	private static boolean anySide(BlockState s) {
		for (Direction d : HORIZONTAL) {
			if (s.getBlock() instanceof WallBlock) {
				if (s.getValue(WallBlock.PROPERTY_BY_DIRECTION.get(d)) != WallSide.NONE) {
					return true;
				}
			} else if (s.getValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d))) {
				return true;
			}
		}
		return false;
	}

	/** The final state of every cell (the last placement at a position wins, as when the plan is built). */
	static Map<Long, BlockState> cells(BuildPlan plan) {
		Map<Long, BlockState> cells = new HashMap<>(plan.size() * 2);
		for (Placement p : plan.placements()) {
			cells.put(p.rel().asLong(), p.state());
		}
		return cells;
	}
}
