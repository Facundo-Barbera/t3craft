package dev.t3craft.client.blocks;

import dev.t3craft.blocks.MonitorBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * The screen a monitor block belongs to: {@code width} × {@code height} in blocks (as the viewer
 * sees it) and whether this block is the panel's {@code origin}, the one that draws the whole
 * screen (others draw nothing). Every layout and LOD decision of {@link MonitorRenderer} takes one
 * of these instead of assuming a single block.
 *
 * <p><b>Contract with the connected-monitor work (P1)</b>: install a {@link Provider} with
 * {@code BlocksClient.get().setScreenExtents(...)}. The origin is the panel's <b>bottom-left block
 * as the viewer faces the screen</b>; the renderer draws from that block's face rightwards and
 * upwards over {@code width} × {@code height} blocks, all with the same facing.
 */
public record ScreenExtent(int width, int height, boolean origin) {
	public static final ScreenExtent ONE = new ScreenExtent(1, 1, true);

	/** Which screen the monitor at {@code be} belongs to; null = a 1×1 screen of its own. */
	@FunctionalInterface
	public interface Provider {
		@Nullable ScreenExtent extent(MonitorBlockEntity be);
	}

	public ScreenExtent {
		width = Math.max(1, width);
		height = Math.max(1, height);
	}

	/** An origin block of a {@code width} × {@code height} screen. */
	public ScreenExtent(int width, int height) {
		this(width, height, true);
	}

	/** A non-origin block of a panel: it draws nothing. */
	public static ScreenExtent member(int width, int height) {
		return new ScreenExtent(width, height, false);
	}

	/** The smaller side: what limits text density and readability. */
	public int minSide() {
		return Math.min(width, height);
	}
}
