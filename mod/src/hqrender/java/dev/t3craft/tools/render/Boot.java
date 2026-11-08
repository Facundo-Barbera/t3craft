package dev.t3craft.tools.render;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * Boots the vanilla registries and registers the mod's {@code t3craft:*} blocks (real monitors and
 * lamps, plus the HQ fixtures), once per JVM, headless. Shared by {@code HqRender.main} and the render
 * daemon, which calls it a single time at start-up; jobs never boot again.
 */
public final class Boot {
	private static boolean booted;

	private Boot() {
	}

	public static synchronized void boot() throws Exception {
		if (booted) {
			return;
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		dev.t3craft.blocks.RenderBlocks.install();
		booted = true;
	}
}
