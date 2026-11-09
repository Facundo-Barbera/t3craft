// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Pixel densities (originally 128 px/block for one-block-high screens, 96 for bigger ones; now ~30 columns across, see nearPpb) follow
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
	/** The status band under the title row (a thread screen): the state's colour with dark text. */
	static final int STATUS_BAND = 11;
	static final int FOOTER = 11;
	static final int PAD = 4;
	/** FAR density: 9 characters across one block, ≈ 0.125 block per text line. */
	static final float FAR_PPB = 64f;

	/**
	 * Face-space layout (pixels; origin = top-left of the whole screen).
	 *
	 * @param ppb   pixels per block
	 * @param bezel margin to the screen's left, right and top edges (the model's 2 px frame)
	 * @param chin  margin at the bottom (the model's 5 px chin: its upper 2 px are the lip band that carries the LED, the lower 3 px lift the casing onto the stand)
	 * @param cols  characters per row inside the padding
	 * @param rows  log rows (0 for FAR)
	 */
	public record Layout(Level level, ScreenExtent extent, float ppb, float bezel, float chin, int cols, int rows) {
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
			return height() - chin - PAD / 2f;
		}

		/** One model pixel (1/16 block) in face pixels. */
		public float modelPx() {
			return ppb / 16f;
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

	/**
	 * Pixel density of the NEAR level: about 25 characters across whatever the width, so the 8 px font stays
	 * 1/8 block tall on a 3-wide desk screen (60 px/block: readable at 4-6 blocks) and a lone block gets 96
	 * (a character is 1/12 block). Very wide walls bottom out at 32 (a character is 1/4 block).
	 */
	public static float nearPpb(ScreenExtent e) {
		return Math.max(32f, Math.min(96f, 150f / e.width()));
	}

	/**
	 * The model's frame, in model pixels: top / left / right bezel and the bottom chin. The chin is the band the casing's
	 * lifted bottom edge and the status LED sit in (y 3..5 of the block); the glass starts above it. The stand below
	 * ({@code tools/art/monitor_art.py}) is part of the block, not of the screen.
	 */
	static final int BEZEL_PX = 2;
	static final int CHIN_PX = 5;

	/** FAR: the status word's largest scale (a 3-wide desk screen gets ≈ 3×, readable across a hall). */
	static final float FAR_STATUS_MAX = 4f;
	/** FAR: the title's largest scale (never above the status word's). */
	static final float FAR_TITLE_MAX = 2.5f;
	/** Gap between the status word and the title (face pixels). */
	static final float FAR_GAP = 4f;
	/** Breathing period of a screen that needs the user (ms). */
	public static final long BREATH_MS = 1600;

	/** Scales of the FAR level's two lines: the status word on top and the title under it. */
	public record FarText(float statusScale, float titleScale) {
		/** Height of both lines with the gap (face pixels). */
		public float height(boolean withTitle) {
			return 8 * statusScale + (withTitle ? FAR_GAP + 8 * titleScale : 0);
		}
	}

	/**
	 * The largest scales that fit the FAR layout's text area: the status word ({@code statusW} font
	 * pixels wide at scale 1) up to {@link #FAR_STATUS_MAX}, the title ({@code titleW}, 0 = none) up to
	 * {@link #FAR_TITLE_MAX} and never larger than the status word, both lines together within the
	 * height. At least 1 each (the renderer ellipsizes a title that does not fit at 1).
	 */
	public static FarText farText(Layout l, int statusW, int titleW) {
		float w = l.x1() - l.x0();
		float h = l.y1() - l.y0();
		float ss = Math.min(FAR_STATUS_MAX, w / Math.max(1, statusW));
		float ts = titleW <= 0 ? 0 : Math.min(Math.min(FAR_TITLE_MAX, ss), w / titleW);
		float need = 8 * ss + (titleW <= 0 ? 0 : FAR_GAP + 8 * ts);
		if (need > h) {
			float k = h / need;
			ss *= k;
			ts *= k;
		}
		return new FarText(Math.max(1f, ss), titleW <= 0 ? 0 : Math.max(1f, ts));
	}

	/** Breathing phase at {@code ms}: 0 (dim) … 1 (bright), a smooth cosine over {@link #BREATH_MS}. */
	public static float breath(long ms) {
		double ph = Math.floorMod(ms, BREATH_MS) / (double) BREATH_MS;
		return (float) (0.5 - 0.5 * Math.cos(ph * Math.PI * 2));
	}

	/**
	 * {@code argb} breathing over {@code bg} (both opaque): at {@code phase} 0 it is 45% of the way from
	 * the background, at 1 the full colour, so it never vanishes into the background.
	 */
	public static int breathe(int argb, int bg, float phase) {
		float t = 0.45f + 0.55f * Math.max(0f, Math.min(1f, phase));
		int r = mix((bg >> 16) & 0xFF, (argb >> 16) & 0xFF, t);
		int g = mix((bg >> 8) & 0xFF, (argb >> 8) & 0xFF, t);
		int b = mix(bg & 0xFF, argb & 0xFF, t);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static int mix(int a, int b, float t) {
		return Math.round(a + (b - a) * t);
	}

	public static Layout layout(Level level, ScreenExtent e) {
		float ppb = level == Level.NEAR ? nearPpb(e) : FAR_PPB;
		float bezel = BEZEL_PX * ppb / 16f;
		float chin = CHIN_PX * ppb / 16f;
		float innerW = e.width() * ppb - 2 * (bezel + PAD);
		int cols = Math.max(1, (int) Math.floor(innerW / GLYPH_W));
		int rows = 0;
		if (level == Level.NEAR) {
			float innerH = e.height() * ppb - bezel - chin - PAD - HEADER - STATUS_BAND - FOOTER;
			rows = Math.max(1, (int) Math.floor(innerH / LINE));
		}
		return new Layout(level, e, ppb, bezel, chin, cols, rows);
	}
}
