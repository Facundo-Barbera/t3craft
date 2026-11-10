package dev.t3craft.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The thread wall anchor's data: the wall size in blocks and the column mode, each optional
 * ({@code 0} / empty = use the config, {@code blocks.threadWall}). The HQ plan writes them as block
 * entity data ({@code {"width": 5, "height": 3}}, see {@link HqBlockFixtures}); saved with the world
 * and synced to the client with the chunk.
 *
 * <p>It is a {@link BoundBlockEntity} only for the options of its config page (issue #43): it shows every
 * thread, so it has no binding ({@link #bindable()} is false: the HQ binding writer, the binding picker and
 * {@code /t3craft blocks link} leave it alone). The page's {@code columns} option wins over the column mode the
 * plan wrote.
 */
public final class ThreadBoardBlockEntity extends BoundBlockEntity {
	public static final String WIDTH = "width";
	public static final String HEIGHT = "height";
	public static final String COLUMNS = "columns";
	/** Option key of the config page's column mode ({@code status | project | environment}); missing = the plan's / the config's. */
	public static final String OPTION_COLUMNS = "columns";
	/** Largest wall the renderer draws (blocks). */
	public static final int MAX_WIDTH = 16;
	public static final int MAX_HEIGHT = 8;

	private int width;
	private int height;
	private String columns = "";

	public ThreadBoardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public boolean bindable() {
		return false;
	}

	/** Wall width in blocks, 0 = config. */
	public int width() {
		return width;
	}

	/** Wall height in blocks, 0 = config. */
	public int height() {
		return height;
	}

	/** Column mode ({@code status | project | environment}): the page's option, else the plan's data; empty = config. */
	public String columns() {
		return option(OPTION_COLUMNS, columns);
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
}
