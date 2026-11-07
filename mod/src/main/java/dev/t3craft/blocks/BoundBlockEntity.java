// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Base of t3craft's block entities: a {@link Binding} string that tells the client what to show
 * ({@code thread:<npcKey>}, {@code board:<i>} or empty). The HQ plan writes it as block entity data
 * ({@code {"binding": "..."}}, see {@link HqBlockFixtures}); it is saved with the world and synced to
 * the client with the chunk and on every change.
 *
 * <p>Adapted from AgentCraft's {@code StationBlockEntity} (binding field, save/load, update packet
 * and update tag for client sync).
 */
public abstract class BoundBlockEntity extends BlockEntity {
	public static final String KEY = "binding";
	private String binding = "";

	protected BoundBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Raw stored binding ({@link Binding#parse} it). */
	public String binding() {
		return binding;
	}

	/** Server side: sets, saves and syncs the binding. */
	public void setBinding(String value) {
		String b = Binding.parse(value).wire();
		if (!b.equals(binding)) {
			binding = b;
			setChanged();
			Level level = getLevel();
			if (level != null && !level.isClientSide()) {
				level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
				bindingChanged(level);
			}
		}
	}

	/** Server side, after {@link #setBinding} stored a different binding. */
	protected void bindingChanged(Level level) {
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		binding = Binding.parse(input.getStringOr(KEY, "")).wire();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!binding.isEmpty()) {
			output.putString(KEY, binding);
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
