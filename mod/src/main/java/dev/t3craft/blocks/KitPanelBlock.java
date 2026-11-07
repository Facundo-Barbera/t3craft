package dev.t3craft.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Shoji wall panel: paper in a cherry lattice; {@link #LIT} glows from behind. Using it toggles the light. */
public final class KitPanelBlock extends Block {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final int LIGHT = 12;

	public KitPanelBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			level.setBlock(pos, state.cycle(LIT), Block.UPDATE_ALL);
		}
		return InteractionResult.SUCCESS;
	}

	public static int light(BlockState state) {
		return state.hasProperty(LIT) && state.getValue(LIT) ? LIGHT : 0;
	}
}
