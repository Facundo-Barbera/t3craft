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
 * The thread wall's anchor ({@code t3craft:thread_board}): one block that the client's
 * {@code ThreadBoardRenderer} uses as the <b>bottom-left corner</b> (as seen from the front,
 * {@link #FACING}) of a kanban wall of threads, {@code width × height} blocks, drawn over the wall
 * blocks next to and above it. Size and column mode come from the block entity
 * ({@link ThreadBoardBlockEntity}, written by the HQ plan) or from the config.
 *
 * <p>No multiblock: the other cells of the wall can be any solid block. Clicks anywhere on the
 * wall are handled on the client (a {@code UseBlockCallback} hit-tests the card); using the anchor
 * itself is consumed on both sides so the held item is not used against it.
 *
 * <p>Facing/rotate/mirror handling adapted from AgentCraft's {@code FacingEntityBlock}; the
 * "panel origin draws the whole board" idea from its {@code TaskBoardBlock}.
 */
public final class ThreadBoardBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	private final Supplier<BlockEntityType<ThreadBoardBlockEntity>> type;

	public ThreadBoardBlock(BlockBehaviour.Properties properties, Supplier<BlockEntityType<ThreadBoardBlockEntity>> type) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
		return new ThreadBoardBlockEntity(type.get(), pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		// the card under the crosshair is opened by the client's UseBlockCallback (it runs first);
		// here the use is only consumed, like the monitor's (sneak to place blocks against it)
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
