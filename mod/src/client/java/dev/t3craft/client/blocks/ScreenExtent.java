package dev.t3craft.client.blocks;

/**
 * Size of a monitor screen in blocks ({@code width} × {@code height}, as the viewer sees it). Every
 * layout and LOD decision of the monitor renderer takes one of these instead of assuming a single
 * block, so a multi-block panel (the connected-monitor work, P1) only has to hand the renderer its
 * extent from the panel origin.
 */
public record ScreenExtent(int width, int height) {
	public static final ScreenExtent ONE = new ScreenExtent(1, 1);

	public ScreenExtent {
		width = Math.max(1, width);
		height = Math.max(1, height);
	}

	/** The smaller side: what limits text density and readability. */
	public int minSide() {
		return Math.min(width, height);
	}
}
