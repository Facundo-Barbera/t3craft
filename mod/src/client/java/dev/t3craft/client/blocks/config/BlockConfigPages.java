package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.ArchiveTerminalBlock;
import dev.t3craft.blocks.ConsoleTerminalBlock;
import dev.t3craft.blocks.DecisionPodiumBlock;
import dev.t3craft.blocks.KitLanternBlock;
import dev.t3craft.blocks.MergeStationBlock;
import dev.t3craft.blocks.MonitorBlock;
import dev.t3craft.blocks.StatusLampBlock;
import dev.t3craft.blocks.ThreadBoardBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Block type → its config page section. Sneak + right click (empty main hand) on a block whose class is
 * registered here opens {@link BlockConfigPanel}; any other block keeps its usual sneak behaviour. Client thread.
 *
 * <p>Registered: the monitor (and its status screen), the status lamps and the paper lantern, the decision podium, the
 * merge station, the console terminal, the archive terminal and the thread wall (issue #43; see
 * {@link BlockConfigSection} for how to add one). Blocks without a page: the plaque (text set by the plan), the glow
 * strips and panels and the rest of the HQ kit (no block entity).
 */
public final class BlockConfigPages {
	private static final Map<Class<? extends Block>, Supplier<BlockConfigSection>> PAGES = new LinkedHashMap<>();

	static {
		register(MonitorBlock.class, MonitorConfigSection::new);
		register(StatusLampBlock.class, () -> new LampConfigSection("Lamp"));
		register(KitLanternBlock.class, () -> new LampConfigSection("Paper lantern"));
		register(DecisionPodiumBlock.class, PodiumConfigSection::new);
		register(MergeStationBlock.class, MergeStationConfigSection::new);
		register(ConsoleTerminalBlock.class, ConsoleConfigSection::new);
		register(ArchiveTerminalBlock.class, ArchiveConfigSection::new);
		register(ThreadBoardBlock.class, ThreadBoardConfigSection::new);
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
