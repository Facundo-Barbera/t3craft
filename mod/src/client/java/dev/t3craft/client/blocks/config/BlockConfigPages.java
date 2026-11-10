package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.MonitorBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Block type → its config page section. Sneak + right click (empty main hand) on a block whose class is
 * registered here opens {@link BlockConfigPanel}; any other block keeps its usual sneak behaviour. Client thread.
 *
 * <p>Registered today: {@link MonitorBlock} ({@link MonitorConfigSection}). The other blocks get theirs in T10
 * (see {@link BlockConfigSection} for how to add one).
 */
public final class BlockConfigPages {
	private static final Map<Class<? extends Block>, Supplier<BlockConfigSection>> PAGES = new LinkedHashMap<>();

	static {
		register(MonitorBlock.class, MonitorConfigSection::new);
	}

	private BlockConfigPages() {
	}

	/** Adds (or replaces) the page of a block class; a subclass of it gets the same page. */
	public static void register(Class<? extends Block> block, Supplier<BlockConfigSection> section) {
		PAGES.put(block, section);
	}

	/** A fresh section for {@code block}, or null when it has no config page. */
	public static @Nullable BlockConfigSection find(Block block) {
		for (Map.Entry<Class<? extends Block>, Supplier<BlockConfigSection>> e : PAGES.entrySet()) {
			if (e.getKey().isInstance(block)) {
				return e.getValue().get();
			}
		}
		return null;
	}
}
