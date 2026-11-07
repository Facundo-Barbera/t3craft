// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.blocks;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * Status lamp: the {@link #STATUS} block state picks the lit texture and the light level
 * ({@link LampStatus#light}). Two blocks share this class: {@code t3craft:status_lamp} (one per desk)
 * and {@code t3craft:board_lamp} (the bulbs on the HQ's "needs attention" board). Its block entity
 * carries the binding; {@link LampApplier} drives the state.
 *
 * <p>Adapted from AgentCraft's {@code StatusLampBlock} (status enum property + binding block entity).
 */
public final class StatusLampBlock extends BaseEntityBlock {
	public static final EnumProperty<LampStatus> STATUS = EnumProperty.create("status", LampStatus.class);

	private final Supplier<BlockEntityType<LampBlockEntity>> type;

	public StatusLampBlock(BlockBehaviour.Properties properties, Supplier<BlockEntityType<LampBlockEntity>> type) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(STATUS, LampStatus.OFF));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STATUS);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LampBlockEntity(type.get(), pos, state);
	}

	/** Light for {@code Properties.lightLevel}. */
	public static int light(BlockState state) {
		return state.hasProperty(STATUS) ? state.getValue(STATUS).light : 0;
	}
}
