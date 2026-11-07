// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.blocks;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import org.jspecify.annotations.Nullable;

/**
 * The monitor: a full block whose front ({@link #FACING}) is a screen. The block model is a dark
 * bezel; the client's {@code MonitorRenderer} draws the bound thread's status on the front face.
 * Using it opens that thread's chat (client side, through {@link #setClientUse the client hook});
 * the use is consumed on both sides.
 *
 * <p>Facing/rotate/mirror handling adapted from AgentCraft's {@code FacingEntityBlock}.
 */
public final class MonitorBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

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
				use.use(pos, level.getBlockEntity(pos) instanceof MonitorBlockEntity m ? m.binding() : "");
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
