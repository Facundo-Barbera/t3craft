// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Pixel densities (128 px/block for one-block-high screens, 96 for bigger ones) follow
// MonitorRenderer.density in mod/src/client/java/dev/agentcraft/client/monitor/MonitorRenderer.java.
package dev.t3craft.client.blocks;

import org.jspecify.annotations.Nullable;

/**
 * Level of detail of a monitor screen by distance, and the layout of each level for a screen of a
 * given {@link ScreenExtent}. Pure (tested).
 *
 * <ul>
 *   <li>{@link Level#NEAR}: header (status dot, title, status word), the log tail and the caret /
 *       "needs you" footer, small text.</li>
 *   <li>{@link Level#FAR}: the face filled with the status colour, the status word as large as the
 *       face allows and the title under it; legible across a room.</li>
 * </ul>
 * The switch distance is {@link MonitorSettings#nearDistance()} per block of the screen's smaller
 * side (bigger panels stay detailed from further away), with {@link #HYSTERESIS} blocks of
 * hysteresis so the screen does not flicker at the edge.
 */
public final class MonitorLod {
	public enum Level { NEAR, FAR }

	static final double HYSTERESIS = 2;
	/** Face pixels of one text line / of the 8 px font. */
	public static final int LINE = 10;
	public static final int GLYPH_W = 6;
	static final int HEADER = 13;
	static final int FOOTER = 11;
	static final int PAD = 4;
	/** FAR density: 9 characters across one block, ≈ 0.125 block per text line. */
	static final float FAR_PPB = 64f;

	/**
	 * Face-space layout (pixels; origin = top-left of the whole screen).
	 *
	 * @param ppb   pixels per block
	 * @param bezel margin to the block edge (the model's frame)
	 * @param cols  characters per row inside the padding
	 * @param rows  log rows (0 for FAR)
	 */
	public record Layout(Level level, ScreenExtent extent, float ppb, float bezel, int cols, int rows) {
		public float width() {
			return extent.width() * ppb;
		}

		public float height() {
			return extent.height() * ppb;
		}

		/** Inner text area, left/top/right/bottom. */
		public float x0() {
			return bezel + PAD;
		}

		public float x1() {
			return width() - bezel - PAD;
		}

		public float y0() {
			return bezel + PAD / 2f;
		}

		public float y1() {
			return height() - bezel - PAD / 2f;
		}
	}

	/** Smallest layer step (blocks) towards the viewer: AgentCraft's {@code DisplayDraw.Z_STEP}. */
	static final float MIN_Z_STEP = 0.0012f;
	/** Safety factor over one depth-buffer step. */
	private static final double Z_SAFETY = 4;
	private static final double NEAR_PLANE = 0.05;
	private static final double DEPTH_STEPS = 1 << 24;

	private MonitorLod() {
	}

	/**
	 * Separation (blocks) between the screen's layers (block face → background → text) at
	 * {@code distance}. A perspective depth buffer resolves about {@code d² / (near · 2^24)} blocks at
	 * distance {@code d}: a fixed 0.003 offset z-fights with the block face beyond ~40 blocks (the
	 * "unreadable from afar" flicker), so the step grows with the square of the distance; up close it
	 * stays at {@link #MIN_Z_STEP} so nothing floats visibly off the face.
	 */
	public static float zStep(double distance) {
		double d = Math.max(0, distance);
		return (float) Math.max(MIN_Z_STEP, Z_SAFETY * d * d / (NEAR_PLANE * DEPTH_STEPS));
	}

	/** Distance below which the screen goes (or stays, + {@link #HYSTERESIS}) NEAR. */
	public static double nearLimit(ScreenExtent e, MonitorSettings s) {
		return s.nearDistance() * e.minSide();
	}

	public static Level next(@Nullable Level prev, double distance, ScreenExtent e, MonitorSettings s) {
		double limit = nearLimit(e, s);
		if (prev == Level.NEAR) {
			return distance <= limit + HYSTERESIS ? Level.NEAR : Level.FAR;
		}
		if (prev == Level.FAR) {
			return distance <= limit ? Level.NEAR : Level.FAR;
		}
		return distance <= limit ? Level.NEAR : Level.FAR;
	}

	/** Pixel density of the NEAR level. */
	public static float nearPpb(ScreenExtent e) {
		return e.minSide() <= 1 ? 128f : 96f;
	}

	public static Layout layout(Level level, ScreenExtent e) {
		float ppb = level == Level.NEAR ? nearPpb(e) : FAR_PPB;
		float bezel = ppb / 16f; // one model pixel of frame
		float innerW = e.width() * ppb - 2 * (bezel + PAD);
		int cols = Math.max(1, (int) Math.floor(innerW / GLYPH_W));
		int rows = 0;
		if (level == Level.NEAR) {
			float innerH = e.height() * ppb - 2 * bezel - PAD - HEADER - FOOTER;
			rows = Math.max(1, (int) Math.floor(innerH / LINE));
		}
		return new Layout(level, e, ppb, bezel, cols, rows);
	}
}
