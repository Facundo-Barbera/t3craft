package dev.t3craft.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The thread wall anchor's data: the wall size in blocks and the column mode, each optional
 * ({@code 0} / empty = use the config, {@code blocks.threadWall}). The HQ plan writes them as block
 * entity data ({@code {"width": 5, "height": 3}}, see {@link HqBlockFixtures}); saved with the world
 * and synced to the client with the chunk. Not a {@link BoundBlockEntity}: it shows every thread,
 * so the HQ binding writer leaves it alone.
 */
public final class ThreadBoardBlockEntity extends BlockEntity {
	public static final String WIDTH = "width";
	public static final String HEIGHT = "height";
	public static final String COLUMNS = "columns";
	/** Largest wall the renderer draws (blocks). */
	public static final int MAX_WIDTH = 16;
	public static final int MAX_HEIGHT = 8;

	private int width;
	private int height;
	private String columns = "";

	public ThreadBoardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Wall width in blocks, 0 = config. */
	public int width() {
		return width;
	}

	/** Wall height in blocks, 0 = config. */
	public int height() {
		return height;
	}

	/** Column mode ({@code status | project | environment}), empty = config. */
	public String columns() {
		return columns;
	}

	static int clamp(int v, int max) {
		return v <= 0 ? 0 : Math.min(v, max);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		width = clamp(input.getIntOr(WIDTH, 0), MAX_WIDTH);
		height = clamp(input.getIntOr(HEIGHT, 0), MAX_HEIGHT);
		columns = input.getStringOr(COLUMNS, "");
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (width > 0) {
			output.putInt(WIDTH, width);
		}
		if (height > 0) {
			output.putInt(HEIGHT, height);
		}
		if (!columns.isEmpty()) {
			output.putString(COLUMNS, columns);
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
