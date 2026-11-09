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
 * The console terminal ({@code t3craft:console_terminal}): a command-desk with a slanted keyboard and a small
 * emissive screen, the way into T3's composer from the world. Using it opens a <b>new thread draft</b> (the T3
 * composer); when it is bound to a thread ({@code thread:<id>}, set with {@code /t3craft blocks link}, like a
 * monitor) it opens that thread's chat instead. The client module sets the behaviour through
 * {@link #setClientUse the client hook}; the use is consumed on both sides, like the monitor.
 *
 * <p>The idea (a console that opens the command line) comes from AgentCraft's {@code console_terminal}
 * (MIT); the code, the model and the textures are t3craft's own.
 */
public final class ConsoleTerminalBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	/**
	 * Cabinet (y 0..7) plus the screen housing and its LEDs at the back (north-facing model: the player stands on the
	 * north side, the back is south). The keyboard wedge stays inside the cabinet's footprint, so it adds no shape.
	 */
	private static final VoxelShape NORTH_SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 7, 16), Block.box(2, 7, 8, 14, 15, 13));
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
			Direction.NORTH, NORTH_SHAPE,
			Direction.EAST, Shapes.or(Block.box(0, 0, 0, 16, 7, 16), Block.box(3, 7, 2, 8, 15, 14)),
			Direction.SOUTH, Shapes.or(Block.box(0, 0, 0, 16, 7, 16), Block.box(2, 7, 3, 14, 15, 8)),
			Direction.WEST, Shapes.or(Block.box(0, 0, 0, 16, 7, 16), Block.box(8, 7, 2, 13, 15, 14)));

	/**
	 * Client hook (set by the client module): open the composer ({@code binding} empty or not a thread) or the
	 * chat of the bound thread. Returns whether it did something.
	 */
	@FunctionalInterface
	public interface ClientUse {
		boolean use(BlockPos pos, String binding);
	}

	private static volatile @Nullable ClientUse clientUse;

	private final Supplier<BlockEntityType<ConsoleTerminalBlockEntity>> type;

	public ConsoleTerminalBlock(BlockBehaviour.Properties properties, Supplier<BlockEntityType<ConsoleTerminalBlockEntity>> type) {
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

	/** Outline/collision of a console whose screen faces {@code facing}. */
	static VoxelShape shape(Direction facing) {
		return SHAPES.getOrDefault(facing, NORTH_SHAPE);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ConsoleTerminalBlockEntity(type.get(), pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		// consumed on both sides (see MonitorBlock): sneak to place blocks against it
		if (level.isClientSide()) {
			ClientUse use = clientUse;
			if (use != null) {
				String own = level.getBlockEntity(pos) instanceof BoundBlockEntity be ? be.binding() : "";
				use.use(pos, own);
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
