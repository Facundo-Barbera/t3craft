// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The monitor: a full block whose front ({@link #FACING}) is a screen. The block model is a dark
 * bezel; the client's {@code MonitorRenderer} draws the bound thread's status on the front face.
 * Using it opens that thread's chat (client side, through {@link #setClientUse the client hook});
 * the use is consumed on both sides. Sneak + right click with an empty hand opens the monitor's config page instead
 * (the client module intercepts it before this block sees it, issue #33).
 *
 * <p><b>Large screens.</b> Monitors that touch with the same facing merge into one screen when they
 * form a full rectangle with agreeing bindings ({@link MonitorWall}). {@link #UP}/{@link #DOWN}/
 * {@link #LEFT}/{@link #RIGHT} (as seen by the viewer) say which neighbours belong to the same screen;
 * the multipart model drops the bezel on those sides, so a 3×2 wall reads as one surface. The flags are
 * recomputed on the server one tick after a monitor is placed, removed or rebound (the delay lets a
 * structure or the HQ builder load the block entity data first), for the whole group at once.
 * {@link #extent} reads the screen back from the flags (the client renderer draws once, from the
 * bottom-left cell, {@link #isDrawOrigin}) and {@link #screenBinding} gives what the whole screen shows.
 *
 * <p>Facing/rotate/mirror handling adapted from AgentCraft's {@code FacingEntityBlock}; the connection
 * properties and origin/extent walk from AgentCraft's {@code PanelBlock}.
 */
public final class MonitorBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** The neighbour above / below / on the viewer's left / right is part of the same screen. */
	public static final BooleanProperty UP = BlockStateProperties.UP;
	public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
	public static final BooleanProperty LEFT = BooleanProperty.create("left");
	public static final BooleanProperty RIGHT = BooleanProperty.create("right");
	/** Flags for block updates of the connection refresh: sync to clients, no neighbour/shape cascade. */
	private static final int REFRESH_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** Client hook (set by the client module): open whatever the monitor at {@code pos} shows. */
	@FunctionalInterface
	public interface ClientUse {
		boolean use(BlockPos pos, String binding);
	}

	private static volatile @Nullable ClientUse clientUse;

	private final Supplier<BlockEntityType<MonitorBlockEntity>> type;

	public MonitorBlock(BlockBehaviour.Properties properties, Supplier<BlockEntityType<MonitorBlockEntity>> type) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
				.setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
	}

	public static void setClientUse(@Nullable ClientUse use) {
		clientUse = use;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, UP, DOWN, LEFT, RIGHT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MonitorBlockEntity(type.get(), pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		// Consumed on both sides, always: if the server passed while the client opened the chat, the
		// server would go on to use the held item (e.g. place a block against the monitor). Sneak to
		// place blocks against it, as with any interactive block.
		if (level.isClientSide()) {
			ClientUse use = clientUse;
			if (use != null) {
				ScreenRect e = extent(level, pos, state);
				use.use(e.origin(), screenBinding(level, e));
			}
		}
		return InteractionResult.SUCCESS;
	}

	// ---- large screens ------------------------------------------------------------------------

	/** {@code state} with its connection flags set from {@code sides}. */
	public static BlockState connected(BlockState state, ScreenRect.Sides sides) {
		return state.setValue(UP, sides.up()).setValue(DOWN, sides.down()).setValue(LEFT, sides.left()).setValue(RIGHT, sides.right());
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		super.onPlace(state, level, pos, old, movedByPiston);
		// only a new monitor or a turned one changes the walls; the refresh's own flag updates do not
		if (!level.isClientSide() && (!old.is(this) || old.getValue(FACING) != state.getValue(FACING))) {
			level.scheduleTick(pos, this, 1);
			if (old.is(this)) {
				scheduleNeighbours(level, pos);
			}
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		scheduleNeighbours(level, pos);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		List<BlockPos> seeds = new ArrayList<>(7);
		seeds.add(pos);
		for (Direction d : Direction.values()) {
			seeds.add(pos.relative(d));
		}
		refresh(level, seeds);
	}

	/** Asks the monitors around {@code pos} to recompute their screen next tick. */
	void scheduleNeighbours(Level level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			if (level.isLoaded(n) && level.getBlockState(n).is(this)) {
				level.scheduleTick(n, this, 1);
			}
		}
	}

	/** Server: recomputes the screens of the groups around {@code seeds}; writes only flags that change. Returns how many. */
	public static int refresh(Level level, List<BlockPos> seeds) {
		Map<BlockPos, ScreenRect> screens = MonitorWall.resolve(lookup(level), seeds);
		int changed = 0;
		for (Map.Entry<BlockPos, ScreenRect> e : screens.entrySet()) {
			BlockState s = level.getBlockState(e.getKey());
			if (!(s.getBlock() instanceof MonitorBlock)) {
				continue;
			}
			BlockState want = connected(s, e.getValue().sides(e.getKey()));
			if (want != s) {
				level.setBlock(e.getKey(), want, REFRESH_FLAGS);
				changed++;
			}
		}
		return changed;
	}

	/** The level as a {@link MonitorWall.Lookup} (unloaded cells read as empty, so nothing is loaded). */
	public static MonitorWall.Lookup lookup(Level level) {
		return p -> {
			if (!level.isLoaded(p)) {
				return null;
			}
			BlockState s = level.getBlockState(p);
			if (!(s.getBlock() instanceof MonitorBlock)) {
				return null;
			}
			return new MonitorWall.Cell(s.getValue(FACING), level.getBlockEntity(p) instanceof BoundBlockEntity be ? be.binding() : "");
		};
	}

	/**
	 * The screen the monitor at {@code pos} belongs to, read from the connection flags (cheap: walks
	 * to the top-left corner, then along the top row and the left column). A monitor whose flags say
	 * nothing is a 1×1 screen; a non-monitor state gives a 1×1 facing north.
	 */
	public static ScreenRect extent(BlockGetter level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof MonitorBlock)) {
			return ScreenRect.single(pos, Direction.NORTH);
		}
		Direction facing = state.getValue(FACING);
		if (!state.getValue(UP) && !state.getValue(DOWN) && !state.getValue(LEFT) && !state.getValue(RIGHT)) {
			return ScreenRect.single(pos, facing);
		}
		Direction left = facing.getClockWise();
		Direction right = facing.getCounterClockWise();
		BlockPos p = pos;
		BlockState s = state;
		for (int i = 0; i < MonitorWall.MAX_HEIGHT && s.getValue(UP); i++) {
			BlockState n = level.getBlockState(p.above());
			if (!same(n, facing)) {
				break;
			}
			p = p.above();
			s = n;
		}
		for (int i = 0; i < MonitorWall.MAX_WIDTH && s.getValue(LEFT); i++) {
			BlockState n = level.getBlockState(p.relative(left));
			if (!same(n, facing)) {
				break;
			}
			p = p.relative(left);
			s = n;
		}
		BlockPos origin = p.immutable();
		BlockState o = s;
		int w = 1;
		for (BlockState c = o; w < MonitorWall.MAX_WIDTH && c.getValue(RIGHT); w++) {
			BlockState n = level.getBlockState(origin.relative(right, w));
			if (!same(n, facing)) {
				break;
			}
			c = n;
		}
		int h = 1;
		for (BlockState c = o; h < MonitorWall.MAX_HEIGHT && c.getValue(DOWN); h++) {
			BlockState n = level.getBlockState(origin.below(h));
			if (!same(n, facing)) {
				break;
			}
			c = n;
		}
		ScreenRect e = new ScreenRect(origin, facing, w, h);
		return e.contains(pos) ? e : ScreenRect.single(pos, facing);
	}

	/** True when this monitor draws its screen: the bottom-left cell as seen by the viewer (a lone monitor always). */
	public static boolean isDrawOrigin(BlockState state) {
		return state.getBlock() instanceof MonitorBlock && !state.getValue(DOWN) && !state.getValue(LEFT);
	}

	/** True when the flags say this monitor is part of a larger screen. */
	public static boolean isConnected(BlockState state) {
		return state.getBlock() instanceof MonitorBlock
				&& (state.getValue(UP) || state.getValue(DOWN) || state.getValue(LEFT) || state.getValue(RIGHT));
	}

	/** What a whole screen shows: the first bound cell's raw binding, origin first ("" when none is bound). */
	public static String screenBinding(BlockGetter level, ScreenRect e) {
		if (level.getBlockEntity(e.origin()) instanceof BoundBlockEntity o && !o.binding().isEmpty()) {
			return o.binding();
		}
		if (e.isMulti()) {
			for (BlockPos p : e.cells()) {
				if (level.getBlockEntity(p) instanceof BoundBlockEntity be && !be.binding().isEmpty()) {
					return be.binding();
				}
			}
		}
		return "";
	}

	private static boolean same(BlockState s, Direction facing) {
		return s.getBlock() instanceof MonitorBlock && s.getValue(FACING) == facing;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		BlockState out = state.rotate(mirror.getRotation(state.getValue(FACING)));
		// a mirror swaps the viewer's left and right
		return mirror == Mirror.NONE ? out : out.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
	}
}
