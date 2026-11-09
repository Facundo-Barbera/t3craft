package dev.t3craft.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The merge station's block entity: a {@link Binding} ({@code project:<key>}, {@code env:<id>} or
 * {@code thread:<key>}) and nothing else. Server-side instances register in a weak set while loaded, so
 * {@link MergeStationApplier} never scans chunks (same idea as {@link LiveLamps}).
 */
public final class MergeStationBlockEntity extends BoundBlockEntity {
	private static final Set<MergeStationBlockEntity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

	public MergeStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** A copy of the loaded server-side stations, safe to iterate while they change. */
	public static synchronized List<MergeStationBlockEntity> snapshot() {
		return new ArrayList<>(LIVE);
	}

	/** How many server-side stations are loaded (the client skips its PR scan while there are none and none is in view). */
	public static synchronized int liveCount() {
		return LIVE.size();
	}

	private static synchronized void add(MergeStationBlockEntity be) {
		LIVE.add(be);
	}

	private static synchronized void remove(MergeStationBlockEntity be) {
		LIVE.remove(be);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) {
			add(this);
		}
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		Level level = getLevel();
		if (level != null && !level.isClientSide()) {
			add(this);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		remove(this);
	}
}
