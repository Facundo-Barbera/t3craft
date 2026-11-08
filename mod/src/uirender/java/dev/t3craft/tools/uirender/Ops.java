package dev.t3craft.tools.uirender;

import java.util.List;

/**
 * What a screen drew during one frame, in GUI coordinates (already through the pose matrix), in
 * draw order. {@link RecordingGraphics} produces them, {@link Raster} paints them.
 */
final class Ops {
	private Ops() {
	}

	/** Scissor rectangle in GUI coordinates, {@code x1}/{@code y1} exclusive. Null in an op = no clip. */
	record Clip(int x0, int y0, int x1, int y1) {
		Clip intersect(Clip o) {
			return new Clip(Math.max(x0, o.x0), Math.max(y0, o.y0), Math.min(x1, o.x1), Math.min(y1, o.y1));
		}

		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	sealed interface Op permits Fill, Gradient, Text, Sprite, Texture {
		Clip clip();
	}

	record Fill(float x0, float y0, float x1, float y1, int argb, Clip clip) implements Op {
	}

	/** Vertical gradient, {@code top} at {@code y0}, {@code bottom} at {@code y1}. */
	record Gradient(float x0, float y0, float x1, float y1, int top, int bottom, Clip clip) implements Op {
	}

	/** One glyph of a text run: code point, colour override (rgb, -1 = none) and style flags. */
	record G(int cp, int rgb, boolean bold, boolean italic, boolean underline, boolean strike) {
	}

	/** A run of text whose top-left is ({@code x}, {@code y}); {@code scale} comes from the pose matrix. */
	record Text(List<G> glyphs, float x, float y, int argb, boolean shadow, float scale, float opacity, Clip clip) implements Op {
	}

	/** A GUI sprite (nine-slice / tiled / stretched per its mcmeta) from {@code textures/gui/sprites/}. */
	record Sprite(String id, float x, float y, float w, float h, int argb, Clip clip) implements Op {
	}

	/** A region of a texture file: ({@code u}, {@code v}) and size ({@code rw}, {@code rh}) in texels of a {@code texW}×{@code texH} sheet. */
	record Texture(String id, float x, float y, float w, float h, float u, float v, int rw, int rh, int texW, int texH, int argb,
			Clip clip) implements Op {
	}

	/** A tooltip the screen asked for ({@code setTooltipForNextFrame}); drawn last, over everything. */
	record Tooltip(List<List<G>> lines, int x, int y) {
	}
}
