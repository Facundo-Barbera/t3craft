package dev.t3craft.blocks;

import java.util.Locale;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * kit/cherry-railing: the cherry railing, a handrail that runs along <b>one edge</b> of its cell
 * ({@link #FACING}, the edge it stands on, as in {@code HqKit.Role.RAILING}) and joins its neighbours
 * into one continuous run: posts only where a run ends or turns, a handrail without seams along a
 * straight run, corners closed, and steps (a run that moves one cell in or out, as on a curved
 * promenade) bridged by a short side rail.
 *
 * <p>State, in the frame of the edge (looking out over it, {@code left} = {@code FACING.ccw}):
 * <ul>
 * <li>{@link #FRONT}: the rail along the edge itself. False only for an inner corner, where the
 *     neighbour in front turns the run and this cell keeps just the corner post.</li>
 * <li>{@link #LEFT}, {@link #RIGHT}: what happens at each end of the edge ({@link End}): the run
 *     goes on ({@code open}), it ends at a post ({@code post}), it turns inwards along that side with a
 *     corner post ({@code turn}), or it steps one cell inwards ({@code step}: corner post and a side
 *     rail that reaches the next run's post).</li>
 * </ul>
 *
 * <p>Everything is a pure function of the {@code FACING} of the railings within one cell (and one
 * diagonal) of this one: {@link #resolve(BlockState, Neighbours)}. The world calls it on placement and
 * on every neighbour update (and refreshes the diagonals by hand, which vanilla does not notify);
 * {@code dev.t3craft.hq.PlanConnect} calls it on a plan's cell map, so generated plans bake the same
 * states the game would.
 */
public class KitRailingBlock extends HorizontalDirectionalBlock {
	/** What one end of the edge does. */
	public enum End implements StringRepresentable {
		/** The run goes on into the neighbour: no post. */
		OPEN,
		/** The run ends here: a post. */
		POST,
		/** The run turns inwards along this side: corner post and a full side rail. */
		TURN,
		/** The run steps one cell inwards: corner post and a side rail reaching the next run's post. */
		STEP;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public boolean post() {
			return this != OPEN;
		}

		public boolean side() {
			return this == TURN || this == STEP;
		}
	}

	public static final BooleanProperty FRONT = BooleanProperty.create("front");
	public static final EnumProperty<End> LEFT = EnumProperty.create("left", End.class);
	public static final EnumProperty<End> RIGHT = EnumProperty.create("right", End.class);

	/** The neighbour at an offset from the railing (y is always the railing's). */
	@FunctionalInterface
	public interface Neighbours {
		BlockState at(int dx, int dz);
	}

	private final Function<BlockState, VoxelShape> shapes;
	private final Function<BlockState, VoxelShape> collisions;

	public KitRailingBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FRONT, true)
				.setValue(LEFT, End.POST).setValue(RIGHT, End.POST));
		this.shapes = getShapeForEachState(st -> shapeOf(st, 16));
		this.collisions = getShapeForEachState(st -> shapeOf(st, 24));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, FRONT, LEFT, RIGHT);
	}

	// ------------------------------------------------------------------ rule

	private static Direction facingOf(BlockState s) {
		return s.getBlock() instanceof KitRailingBlock ? s.getValue(FACING) : null;
	}

	/** The facing of the railing at {@code a + b} (null when there is none). */
	private static Direction rail(Neighbours n, Direction a, Direction b) {
		int dx = a.getStepX() + (b == null ? 0 : b.getStepX());
		int dz = a.getStepZ() + (b == null ? 0 : b.getStepZ());
		return facingOf(n.at(dx, dz));
	}

	/**
	 * The connected state of the railing {@code s} (only its {@code FACING} is read) among
	 * {@code n}. Deterministic and order-free: neighbours are read by facing only.
	 */
	public static BlockState resolve(BlockState s, Neighbours n) {
		Direction f = s.getValue(FACING);
		Direction left = f.getCounterClockWise();
		Direction right = f.getClockWise();
		End l = end(n, f, left);
		End r = end(n, f, right);
		boolean front = true;
		// inner corner: the neighbour in front carries the run away from us along its edge `ahead`, which
		// meets our edge at our `ahead` end; when the run comes in from that side and goes nowhere beyond,
		// our own edge would only dangle past the corner, so this cell keeps just the corner post
		Direction ahead = rail(n, f, null);
		if (ahead != null && ahead.getAxis() != f.getAxis()) {
			Direction meet = ahead;
			Direction away = meet.getOpposite();
			boolean comesIn = rail(n, meet, null) == f;
			boolean goesOn = rail(n, away, null) == f;
			if (comesIn && !goesOn) {
				front = false;
				l = meet == left ? End.POST : End.OPEN;
				r = meet == right ? End.POST : End.OPEN;
			}
		}
		return s.setValue(FRONT, front).setValue(LEFT, l).setValue(RIGHT, r);
	}

	/** What the end of edge {@code f} towards side {@code d} does. */
	private static End end(Neighbours n, Direction f, Direction d) {
		Direction in = f.getOpposite();
		Direction beside = rail(n, d, null);
		if (beside == f) {
			return End.OPEN; // straight run
		}
		if (rail(n, in, null) == d) {
			return End.TURN; // the inward neighbour carries the run on along our side d
		}
		if (beside == null && rail(n, d, in) == f) {
			return End.STEP; // the run goes on one cell inwards
		}
		if (beside == d && rail(n, d, f) != d) {
			return End.OPEN; // the neighbour beside turns onto our edge and owns the corner post
		}
		if (beside == d.getOpposite()) {
			return End.OPEN; // our end butts into the neighbour's rail on the shared boundary (a T)
		}
		return End.POST;
	}

	// ------------------------------------------------------------------ shapes

	/**
	 * Outline (16 high, what the model shows) or collision (24 high, like a fence: a railing on a
	 * bridge or a balcony has to stop people, not be hopped over or stood on).
	 */
	private static VoxelShape shapeOf(BlockState s, double h) {
		// north frame: the edge is z 0, left = west (x 0), right = east (x 16)
		VoxelShape v = Shapes.empty();
		if (s.getValue(FRONT)) {
			v = Shapes.or(v, Block.box(0, 0, 0, 16, h, 3));
		}
		End l = s.getValue(LEFT);
		End r = s.getValue(RIGHT);
		if (l.post()) {
			v = Shapes.or(v, Block.box(0, 0, 0, 4, h, 4));
		}
		if (l.side()) {
			v = Shapes.or(v, Block.box(0, 0, 0, 3, h, 16));
		}
		if (r.post()) {
			v = Shapes.or(v, Block.box(12, 0, 0, 16, h, 4));
		}
		if (r.side()) {
			v = Shapes.or(v, Block.box(13, 0, 0, 16, h, 16));
		}
		if (v.isEmpty()) {
			v = Block.box(0, 0, 0, 4, h, 4);
		}
		return Shapes.rotateHorizontal(v).get(s.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return collisions.apply(state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.apply(state);
	}

	// ------------------------------------------------------------------ world

	private static Neighbours around(BlockGetter level, BlockPos pos) {
		return (dx, dz) -> level.getBlockState(pos.offset(dx, 0, dz));
	}

	/** Placed on the edge the player looks at (stand on the balcony, look out, place). */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState s = defaultBlockState().setValue(FACING, context.getHorizontalDirection());
		return resolve(s, around(context.getLevel(), context.getClickedPos()));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return dir.getAxis().isHorizontal() ? resolve(state, around(level, pos)) : state;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(state.getBlock())) {
			refreshDiagonals(level, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		refreshDiagonals(level, pos);
	}

	/** Vanilla notifies only the six faces; a step reads a diagonal, so the diagonals are refreshed here. */
	private static void refreshDiagonals(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}
		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				BlockPos p = pos.offset(dx, 0, dz);
				BlockState s = level.getBlockState(p);
				if (s.getBlock() instanceof KitRailingBlock) {
					BlockState u = resolve(s, around(level, p));
					if (u != s) {
						level.setBlock(p, u, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		BlockState m = super.mirror(state, mirror);
		if (mirror == Mirror.NONE) {
			return m;
		}
		return m.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
	}
}
