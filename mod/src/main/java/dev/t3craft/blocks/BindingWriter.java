package dev.t3craft.blocks;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Server thread: writes HQ bindings into the {@link BoundBlockEntity}s already in the world (no block
 * is placed or replaced). Only cells that hold one of the module's blocks, are loaded and whose
 * stored binding differs are written, so re-sending the same map is free and a steady HQ costs one
 * lookup per fixture. Cells without a t3craft block (a module not built yet, an archive shelf, a
 * vanilla HQ) are skipped, and so are block entities whose binding was set by hand
 * ({@link BoundBlockEntity#manual()}: the block's config page or {@code /t3craft blocks link}) until "Reset to
 * automatic" clears the flag.
 */
public final class BindingWriter {
	private BindingWriter() {
	}

	/** World cell ({@link BlockPos#asLong}) → binding. Returns how many block entities changed. */
	public static int apply(Level level, Map<Long, Binding> bindings) {
		int changed = 0;
		for (Map.Entry<Long, Binding> e : bindings.entrySet()) {
			BlockPos pos = BlockPos.of(e.getKey());
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (level.getBlockEntity(pos) instanceof BoundBlockEntity be && !be.manual() && !Binding.parse(be.binding()).equals(e.getValue())) {
				be.setBinding(e.getValue().wire());
				changed++;
			}
		}
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.BINDING_WRITES, changed);
		return changed;
	}
}
