package dev.t3craft.client.portable.held;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A pixel font for the held-item screen, rasterised on the CPU. Built at runtime from the game's
 * own bitmap sheet ({@code minecraft:textures/font/ascii.png} + the {@code chars} rows of its
 * provider in {@code minecraft:font/include/default.json}), so the held screen uses Minecraft's
 * letters without shipping any Mojang asset. Widths follow the vanilla bitmap rule: last lit column
 * + 1, plus 1 px spacing; space advances 4. Pure Java (tested with synthetic sheets).
 */
public final class BitmapFont {
	/** Line height in px (8 px cell + 1 px leading). */
	public static final int LINE_H = 9;

	/** {@code rows[y]} is a bitmask of lit columns (bit x) for a glyph of cell height. */
	record Glyph(int[] rows, int advance) {
	}

	private final Map<Integer, Glyph> glyphs;
	private final int cellH;
	private final Glyph missing;

	private BitmapFont(Map<Integer, Glyph> glyphs, int cellH) {
		this.glyphs = glyphs;
		this.cellH = cellH;
		this.missing = glyphs.getOrDefault((int) '?', box(cellH));
	}

	/**
	 * @param argb  sheet pixels, row-major, alpha in the top byte (lit = alpha &gt; 0)
	 * @param chars provider rows; {@code \u0000} cells are empty
	 */
	public static BitmapFont fromSheet(int[] argb, int width, int height, List<String> chars) {
		int rows = chars.size();
		int cols = chars.stream().mapToInt(s -> s.codePointCount(0, s.length())).max().orElse(1);
		int cw = width / Math.max(1, cols);
		int ch = height / Math.max(1, rows);
		Map<Integer, Glyph> map = new HashMap<>();
		for (int r = 0; r < rows; r++) {
			int[] cps = chars.get(r).codePoints().toArray();
			for (int c = 0; c < cps.length; c++) {
				int cp = cps[c];
				if (cp == 0) {
					continue;
				}
				int[] bits = new int[ch];
				int last = -1;
				for (int y = 0; y < ch; y++) {
					for (int x = 0; x < cw && x < 31; x++) {
						int px = argb[(r * ch + y) * width + c * cw + x];
						if ((px >>> 24) != 0) {
							bits[y] |= 1 << x;
							last = Math.max(last, x);
						}
					}
				}
				int advance = cp == ' ' ? 4 : last + 2;
				map.put(cp, new Glyph(bits, Math.max(advance, 2)));
			}
		}
		map.putIfAbsent((int) ' ', new Glyph(new int[ch], 4));
		return new BitmapFont(map, ch);
	}

	/**
	 * The classic {@code ascii.png} layout (16×16 cells, code point = row × 16 + column) for the
	 * printable ASCII range; other cells empty. Used when the provider JSON cannot be read.
	 */
	public static List<String> classicAsciiGrid() {
		List<String> rows = new java.util.ArrayList<>(16);
		for (int r = 0; r < 16; r++) {
			StringBuilder b = new StringBuilder(16);
			for (int c = 0; c < 16; c++) {
				int cp = r * 16 + c;
				b.append(cp >= 0x20 && cp <= 0x7E ? (char) cp : '\u0000');
			}
			rows.add(b.toString());
		}
		return rows;
	}

	/** Fallback when the sheet cannot be read: every character is a small box (still shows layout). */
	public static BitmapFont boxes() {
		Map<Integer, Glyph> map = new HashMap<>();
		map.put((int) ' ', new Glyph(new int[8], 4));
		return new BitmapFont(map, 8);
	}

	private static Glyph box(int h) {
		int[] bits = new int[h];
		for (int y = 1; y < Math.min(h, 7); y++) {
			bits[y] = (y == 1 || y == 6) ? 0b1111 : 0b1001;
		}
		return new Glyph(bits, 5);
	}

	public int cellHeight() {
		return cellH;
	}

	Glyph glyph(int cp) {
		Glyph g = glyphs.get(cp);
		if (g != null) {
			return g;
		}
		int folded = fold(cp);
		return folded != cp ? glyphs.getOrDefault(folded, missing) : missing;
	}

	public boolean has(int cp) {
		return glyphs.containsKey(cp);
	}

	/** Strips accents ("é" → "e") and maps common typography to ASCII; unknown stays as is. */
	static int fold(int cp) {
		switch (cp) {
			case '…', '·', '•' -> {
				return '.';
			}
			case '–', '—', '−' -> {
				return '-';
			}
			case '‘', '’' -> {
				return '\'';
			}
			case '“', '”' -> {
				return '"';
			}
			case '→' -> {
				return '>';
			}
			default -> {
			}
		}
		String s = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return s.isEmpty() ? cp : s.codePointAt(0);
	}

	public int width(String s) {
		int w = 0;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			w += glyph(cp).advance();
			i += Character.charCount(cp);
		}
		return w;
	}

	/** Draws {@code s} into {@code argb} (row-major {@code w}×{@code h}); clipped to {@code maxX}. Returns the end x. */
	public int draw(int[] argb, int w, int h, int x, int y, String s, int color, int maxX) {
		int argbColor = 0xFF000000 | color;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			Glyph g = glyph(cp);
			if (x + g.advance() - 1 > maxX) {
				break;
			}
			for (int gy = 0; gy < g.rows().length; gy++) {
				int bits = g.rows()[gy];
				int py = y + gy;
				if (bits == 0 || py < 0 || py >= h) {
					continue;
				}
				for (int gx = 0; bits >>> gx != 0; gx++) {
					if ((bits >>> gx & 1) != 0) {
						int px = x + gx;
						if (px >= 0 && px < w) {
							argb[py * w + px] = argbColor;
						}
					}
				}
			}
			x += g.advance();
		}
		return x;
	}

	/** Cuts {@code s} to fit {@code maxW} px, adding "..." when cut. */
	public String clip(String s, int maxW) {
		if (width(s) <= maxW) {
			return s;
		}
		int dots = width("...");
		StringBuilder b = new StringBuilder();
		int wsum = 0;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			int a = glyph(cp).advance();
			if (wsum + a + dots > maxW) {
				break;
			}
			b.appendCodePoint(cp);
			wsum += a;
			i += Character.charCount(cp);
		}
		return b.toString().stripTrailing() + "...";
	}
}
