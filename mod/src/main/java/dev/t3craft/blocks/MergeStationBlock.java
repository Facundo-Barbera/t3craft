package dev.t3craft.blocks;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The merge station ({@code t3craft:merge_station}): a review desk with an upright diff screen and status lamps.
 * It is bound to a project ({@code project:<key>}), an environment or one thread, like a monitor
 * ({@code /t3craft blocks link}, or the HQ plan's data). The {@link #PR} state is the best pull request among
 * its threads (open &gt; draft &gt; merged &gt; none): {@link MergeStationApplier} sets it on the integrated
 * server, it picks the model and gives light while a review is pending. The client's
 * {@code MergeStationRenderer} writes the number of pending PRs on the panel.
 *
 * <p>Using it opens the list of the bound threads with a pending PR (client side, through
 * {@link #setClientUse the client hook}); choosing one opens its pull request in the browser, or its chat.
 * The station never merges anything: it only lets the player review and open (the mod is read only).
 * The use is consumed on both sides, like the monitor.
 *
 * <p>The idea (a station to review and merge from the world) comes from AgentCraft's {@code merge_station}
 * (MIT); the code, the model and the textures are t3craft's own.
 */
public final class MergeStationBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final EnumProperty<MergeState> PR = EnumProperty.create("pr", MergeState.class);

	/**
	 * Cabinet (y 0..9) with the upright screen housing on top, at the back (north-facing model: the player stands
	 * north, the housing is south of the middle).
	 */
	private static final VoxelShape NORTH_SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 9, 16), Block.box(1, 9, 7, 15, 16, 14));
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
			Direction.NORTH, NORTH_SHAPE,
			Direction.EAST, Shapes.or(Block.box(0, 0, 0, 16, 9, 16), Block.box(2, 9, 1, 9, 16, 15)),
			Direction.SOUTH, Shapes.or(Block.box(0, 0, 0, 16, 9, 16), Block.box(1, 9, 2, 15, 16, 9)),
			Direction.WEST, Shapes.or(Block.box(0, 0, 0, 16, 9, 16), Block.box(7, 9, 1, 14, 16, 15)));

	/** Client hook (set by the client module): open the pending-PR list of the station at {@code pos} with binding {@code binding}. */
	@FunctionalInterface
	public interface ClientUse {
		void use(BlockPos pos, String binding);
	}

	private static volatile @Nullable ClientUse clientUse;

	private final Supplier<BlockEntityType<MergeStationBlockEntity>> type;

	public MergeStationBlock(BlockBehaviour.Properties properties, Supplier<BlockEntityType<MergeStationBlockEntity>> type) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PR, MergeState.NONE));
	}

	public static void setClientUse(@Nullable ClientUse use) {
		clientUse = use;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PR);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape(state.getValue(FACING));
	}

	/** Outline/collision of a station whose panel faces {@code facing}. */
	static VoxelShape shape(Direction facing) {
		return SHAPES.getOrDefault(facing, NORTH_SHAPE);
	}

	/** Light for {@code Properties.lightLevel}. */
	public static int light(BlockState state) {
		return state.hasProperty(PR) ? state.getValue(PR).light : 0;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MergeStationBlockEntity(type.get(), pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		// consumed on both sides (see MonitorBlock): sneak to place blocks against it
		if (level.isClientSide()) {
			ClientUse use = clientUse;
			if (use != null) {
				String binding = level.getBlockEntity(pos) instanceof BoundBlockEntity be ? be.binding() : "";
				use.use(pos, binding);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
