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
 * The archive terminal ({@code t3craft:archive_terminal}): a console (a desk with a keyboard ledge
 * and an upright screen at the back) where the closed threads of every environment are browsed. The
 * screen faces {@link #FACING}; the client's {@code ArchiveTerminalRenderer} draws "ARCHIVE · N
 * threads" and the latest three on it. Using it opens the archive screen (client side, through
 * {@link #setClientUse the client hook}); the use is consumed on both sides, like the monitor.
 *
 * <p>Not bound to a thread: it always shows the whole archive, so its block entity holds nothing.
 */
public final class ArchiveTerminalBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	/** Desk (y 0..7) plus the screen housing at the back (north-facing model: back = south). */
	private static final VoxelShape NORTH_SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 7, 16), Block.box(1, 7, 6, 15, 16, 15));
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
			Direction.NORTH, NORTH_SHAPE,
			Direction.EAST, Shapes.or(Block.box(0, 0, 0, 16, 7, 16), Block.box(1, 7, 1, 10, 16, 15)),
			Direction.SOUTH, Shapes.or(Block.box(0, 0, 0, 16, 7, 16), Block.box(1, 7, 1, 15, 16, 10)),
			Direction.WEST, Shapes.or(Block.box(0, 0, 0, 16, 7, 16), Block.box(6, 7, 1, 15, 16, 15)));

	/** Client hook (set by the client module): open the archive screen. */
	@FunctionalInterface
	public interface ClientUse {
		void use(BlockPos pos);
	}

	private static volatile @Nullable ClientUse clientUse;

	private final Supplier<BlockEntityType<ArchiveTerminalBlockEntity>> type;

	public ArchiveTerminalBlock(BlockBehaviour.Properties properties, Supplier<BlockEntityType<ArchiveTerminalBlockEntity>> type) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public static void setClientUse(@Nullable ClientUse use) {
		clientUse = use;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape(state.getValue(FACING));
	}

	/** Outline/collision of a terminal whose screen faces {@code facing}. */
	static VoxelShape shape(Direction facing) {
		return SHAPES.getOrDefault(facing, NORTH_SHAPE);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ArchiveTerminalBlockEntity(type.get(), pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		// consumed on both sides (see MonitorBlock): sneak to place blocks against it
		if (level.isClientSide()) {
			ClientUse use = clientUse;
			if (use != null) {
				use.use(pos);
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
