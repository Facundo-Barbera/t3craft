package dev.t3craft.blocks;

import java.util.EnumMap;
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
 * The decision podium ({@code t3craft:decision_podium}): a lectern with a brass bell and a lit display. It is bound
 * {@code board:<i>} like a board bulb, so it stands for the i-th most urgent thread that needs the player (errors first,
 * then "needs you"). Its {@link #STATUS} is the same property as a status lamp's, so {@link LampApplier} drives it with no
 * extra code: the display and the light follow the thread's {@link LampStatus}, and a {@code !} blinks on it while the
 * thread asks for the player. Using it opens that thread (client side, {@link #setClientUse the client hook}), to answer
 * its pending question or permission; with nobody waiting, the hook says so. The use is consumed on both sides
 * (see {@link MonitorBlock}): sneak to place blocks against it.
 *
 * <p>The idea (a podium where the agent that needs a decision waits, with a bell) is AgentCraft's {@code decision_podium};
 * model, textures and code are t3craft's own ({@code tools/art/decision_podium_art.py}).
 *
 * <p>Block entity: a {@link LampBlockEntity} of its own type ({@code t3craft:decision_podium}), so the lamp sweep
 * ({@link LiveLamps}) finds it but {@code LampRenderer}, which glows over a full cube, does not draw on it.
 */
public final class DecisionPodiumBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** The very property of {@link StatusLampBlock}: {@link LampApplier} sets it on any block that has it. */
	public static final EnumProperty<LampStatus> STATUS = StatusLampBlock.STATUS;

	/** Client hook (set by the client module): the player used the podium at {@code pos} whose own binding is {@code binding}. */
	@FunctionalInterface
	public interface ClientUse {
		void use(BlockPos pos, String binding);
	}

	private static volatile @Nullable ClientUse clientUse;

	private final Supplier<BlockEntityType<LampBlockEntity>> type;

	public DecisionPodiumBlock(BlockBehaviour.Properties properties, Supplier<BlockEntityType<LampBlockEntity>> type) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, LampStatus.OFF));
	}

	public static void setClientUse(@Nullable ClientUse use) {
		clientUse = use;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, STATUS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LampBlockEntity(type.get(), pos, state);
	}

	/** Light for {@code Properties.lightLevel}: the same as a lamp with that status. */
	public static int light(BlockState state) {
		return StatusLampBlock.light(state);
	}

	// ---- shape: plinth, shaft, desk, display panel at the back (north-facing model; the front looks to -z) ----

	private static final double[][] NORTH_BOXES = {
			{1, 0, 1, 15, 2, 15},   // plinth
			{4, 2, 5, 12, 8, 13},   // shaft
			{1, 8, 2, 15, 10, 14},  // desk
			{1, 10, 8, 15, 16, 14}  // display panel
	};
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			VoxelShape s = Shapes.empty();
			for (double[] b : NORTH_BOXES) {
				s = Shapes.or(s, rotated(d, b));
			}
			SHAPES.put(d, s);
		}
	}

	/** A north-facing box turned so its front looks to {@code facing} (the same turn as the blockstate's {@code y}). */
	private static VoxelShape rotated(Direction facing, double[] b) {
		double x0 = b[0];
		double y0 = b[1];
		double z0 = b[2];
		double x1 = b[3];
		double y1 = b[4];
		double z1 = b[5];
		return switch (facing) {
			case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
			case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
			case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
			default -> Block.box(x0, y0, z0, x1, y1, z1);
		};
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
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
