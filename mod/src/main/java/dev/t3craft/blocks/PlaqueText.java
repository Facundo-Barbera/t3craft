package dev.t3craft.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;

/**
 * What a room plaque says: up to {@link #LINES} lines (project, module number, environment...) and
 * the accent colour of the first line. Pure. Every content change takes a new {@link #version()}
 * from a global counter, so the renderer's glyph cache, keyed by version, never mixes two plaques
 * (not even a plaque replaced at the same position) and rebuilds only when the text really changed.
 *
 * <p>Block entity data: {@code {"line0": "...", ..., "line3": "...", "color": 0xRRGGBB}} (empty lines
 * omitted). The HQ writes it through {@code HqKit.plaqueData}.
 */
public final class PlaqueText {
	public static final int LINES = 4;
	/** Characters per line that fit on the plate. */
	public static final int WIDTH = 17;
	/** Default accent (warm cream) when none is given. */
	public static final int DEFAULT_COLOR = 0xF3E3C3;
	/** Colour of the lines after the first. */
	public static final int BODY_COLOR = 0xD9CBB4;
	public static final String COLOR_KEY = "color";

	private static final AtomicLong VERSIONS = new AtomicLong();

	private List<String> lines = List.of();
	private int color = DEFAULT_COLOR;
	private long version = VERSIONS.incrementAndGet();

	/** Lines as shown (clipped, at most {@link #LINES}; trailing empties dropped). */
	public List<String> lines() {
		return lines;
	}

	/** Accent colour of the first line, {@code 0xRRGGBB}, always readable on the dark plate. */
	public int color() {
		return color;
	}

	public long version() {
		return version;
	}

	/** Sets the content; returns whether it changed (and then takes a new version). */
	public boolean set(List<String> newLines, int newColor) {
		List<String> l = normalize(newLines);
		int c = readable(newColor & 0xFFFFFF);
		if (l.equals(lines) && c == color) {
			return false;
		}
		lines = l;
		color = c;
		version = VERSIONS.incrementAndGet();
		return true;
	}

	static List<String> normalize(List<String> in) {
		List<String> out = new ArrayList<>(LINES);
		for (int i = 0; i < Math.min(LINES, in.size()); i++) {
			out.add(clip(Objects.requireNonNullElse(in.get(i), "")));
		}
		while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
			out.remove(out.size() - 1);
		}
		return List.copyOf(out);
	}

	static String clip(String s) {
		String t = s.strip();
		return t.length() <= WIDTH ? t : t.substring(0, WIDTH - 1) + "…";
	}

	/** Too-dark accents (black, brown dye text) are lifted towards cream so they read on the ink plate. */
	static int readable(int rgb) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
		if (luma >= 90) {
			return rgb;
		}
		// halfway to the default accent
		int dr = DEFAULT_COLOR >> 16 & 0xFF;
		int dg = DEFAULT_COLOR >> 8 & 0xFF;
		int db = DEFAULT_COLOR & 0xFF;
		return (r + dr) / 2 << 16 | (g + dg) / 2 << 8 | (b + db) / 2;
	}

	static String lineKey(int i) {
		return "line" + i;
	}

	/** Block entity data for a plaque (a fresh tag). */
	public static CompoundTag data(int color, String... lines) {
		CompoundTag t = new CompoundTag();
		List<String> l = normalize(List.of(lines));
		for (int i = 0; i < l.size(); i++) {
			if (!l.get(i).isEmpty()) {
				t.putString(lineKey(i), l.get(i));
			}
		}
		t.putInt(COLOR_KEY, color & 0xFFFFFF);
		return t;
	}
}
