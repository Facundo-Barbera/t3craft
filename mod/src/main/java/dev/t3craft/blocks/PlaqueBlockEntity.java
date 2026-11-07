package dev.t3craft.blocks;

import java.util.ArrayList;
import java.util.List;
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
 * A room plaque's text ({@link PlaqueText}): written by the HQ plan as block entity data, saved with
 * the world and synced to the client, where {@code PlaqueRenderer} draws it (glyphs cached by
 * {@link PlaqueText#version()}).
 */
public final class PlaqueBlockEntity extends BlockEntity {
	private final PlaqueText text = new PlaqueText();

	public PlaqueBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public PlaqueText text() {
		return text;
	}

	/** Server side: sets, saves and syncs the text. */
	public void setText(int color, String... lines) {
		if (text.set(List.of(lines), color)) {
			setChanged();
			Level level = getLevel();
			if (level != null && !level.isClientSide()) {
				level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		List<String> lines = new ArrayList<>(PlaqueText.LINES);
		for (int i = 0; i < PlaqueText.LINES; i++) {
			lines.add(input.getStringOr(PlaqueText.lineKey(i), ""));
		}
		text.set(lines, input.getIntOr(PlaqueText.COLOR_KEY, PlaqueText.DEFAULT_COLOR));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		List<String> lines = text.lines();
		for (int i = 0; i < lines.size(); i++) {
			if (!lines.get(i).isEmpty()) {
				output.putString(PlaqueText.lineKey(i), lines.get(i));
			}
		}
		output.putInt(PlaqueText.COLOR_KEY, text.color());
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
