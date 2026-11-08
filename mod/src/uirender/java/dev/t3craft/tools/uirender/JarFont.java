package dev.t3craft.tools.uirender;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.font.GlyphInfo;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Minecraft's default font read straight from the jar ({@code assets/minecraft/font/default.json}
 * and the bitmap sheets under {@code textures/font/}), with the same metrics the game computes: the
 * advance of a bitmap glyph is {@code (int)(0.5 + widthOfItsInk * scale) + 1}, spaces come from the
 * {@code space} provider. First provider that has a code point wins, like in the game. Gives the real
 * {@link Font} (widths, wrapping, truncation: the screens lay text out with it) and the pixels
 * {@link Raster} draws. Not loaded: TrueType and Unihex providers (the Unifont hex files are not in
 * the client jar), so characters only Unifont has (for example {@code ⚙}) draw as the missing-glyph box.
 */
final class JarFont {
	/** A bitmap glyph: its cell in the sheet and how it sits on the line. */
	static final class Glyph {
		final BufferedImage sheet;
		final int cellX;
		final int cellY;
		final int cellW;
		final int cellH;
		/** GUI units per sheet pixel ({@code height / cellH}). */
		final float scale;
		final int advance;
		final int ascent;

		Glyph(BufferedImage sheet, int cellX, int cellY, int cellW, int cellH, float scale, int advance, int ascent) {
			this.sheet = sheet;
			this.cellX = cellX;
			this.cellY = cellY;
			this.cellW = cellW;
			this.cellH = cellH;
			this.scale = scale;
			this.advance = advance;
			this.ascent = ascent;
		}
	}

	private static final int MISSING_ADVANCE = 6;
	private static @Nullable JarFont instance;

	private final Map<Integer, Glyph> glyphs = new HashMap<>();
	private final Map<Integer, Integer> spaces = new HashMap<>();
	private final Map<String, BufferedImage> sheets = new HashMap<>();

	static synchronized JarFont get() {
		if (instance == null) {
			JarFont f = new JarFont();
			f.loadFont("minecraft:default");
			instance = f;
		}
		return instance;
	}

	private JarFont() {
	}

	// ---- loading -------------------------------------------------------------------------------

	private void loadFont(String id) {
		JsonObject o = Assets.json(Assets.assetPath(id, "font/", ".json"));
		if (o == null || !o.has("providers")) {
			return;
		}
		for (JsonElement e : o.getAsJsonArray("providers")) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject p = e.getAsJsonObject();
			if (!filterAllows(p)) {
				continue;
			}
			String type = p.has("type") ? p.get("type").getAsString() : "";
			switch (type) {
				case "reference" -> loadFont(p.get("id").getAsString());
				case "space" -> space(p);
				case "bitmap" -> bitmap(p);
				default -> {
					// ttf / unihex / legacy_unicode: not loaded (see the class comment)
				}
			}
		}
	}

	/** Default options: nothing like {@code uniform} or {@code jp} is switched on. */
	private static boolean filterAllows(JsonObject p) {
		if (!p.has("filter") || !p.get("filter").isJsonObject()) {
			return true;
		}
		for (Map.Entry<String, JsonElement> f : p.getAsJsonObject("filter").entrySet()) {
			if (f.getValue().isJsonPrimitive() && f.getValue().getAsJsonPrimitive().isBoolean() && f.getValue().getAsBoolean()) {
				return false;
			}
		}
		return true;
	}

	private void space(JsonObject p) {
		JsonObject adv = p.getAsJsonObject("advances");
		if (adv == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> en : adv.entrySet()) {
			int cp = en.getKey().codePointAt(0);
			spaces.putIfAbsent(cp, (int) Math.round(en.getValue().getAsDouble()));
		}
	}

	private void bitmap(JsonObject p) {
		String file = p.get("file").getAsString();
		BufferedImage sheet = sheets.computeIfAbsent(file, f -> Assets.texture(f.contains(":") ? f.replace(":", ":textures/") : "minecraft:textures/" + f));
		if (sheet == null) {
			return;
		}
		JsonArray chars = p.getAsJsonArray("chars");
		int rows = chars.size();
		int cols = 0;
		int[][] cps = new int[rows][];
		for (int r = 0; r < rows; r++) {
			cps[r] = chars.get(r).getAsString().codePoints().toArray();
			cols = Math.max(cols, cps[r].length);
		}
		if (rows == 0 || cols == 0) {
			return;
		}
		int cellW = sheet.getWidth() / cols;
		int cellH = sheet.getHeight() / rows;
		int height = p.has("height") ? p.get("height").getAsInt() : 8;
		int ascent = p.get("ascent").getAsInt();
		float scale = (float) height / (float) cellH;
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cps[r].length; c++) {
				int cp = cps[r][c];
				if (cp == 0 || glyphs.containsKey(cp) || spaces.containsKey(cp)) {
					continue;
				}
				int ink = inkWidth(sheet, c * cellW, r * cellH, cellW, cellH);
				int advance = (int) (0.5 + ink * scale) + 1;
				glyphs.put(cp, new Glyph(sheet, c * cellW, r * cellH, cellW, cellH, scale, advance, ascent));
			}
		}
	}

	/** Width of the glyph's ink: its rightmost column with a visible pixel, plus one (0 when empty). */
	private static int inkWidth(BufferedImage sheet, int x0, int y0, int w, int h) {
		for (int x = w - 1; x >= 0; x--) {
			for (int y = 0; y < h; y++) {
				if ((sheet.getRGB(x0 + x, y0 + y) >>> 24) != 0) {
					return x + 1;
				}
			}
		}
		return 0;
	}

	// ---- lookup --------------------------------------------------------------------------------

	/** The bitmap glyph for {@code cp}, or null (space, or missing). */
	@Nullable Glyph glyph(int cp) {
		return glyphs.get(cp);
	}

	boolean has(int cp) {
		return glyphs.containsKey(cp) || spaces.containsKey(cp);
	}

	/** Advance in GUI units, plus the bold offset when {@code bold}. */
	float advance(int cp, boolean bold) {
		Integer sp = spaces.get(cp);
		float a = sp != null ? sp : glyphs.containsKey(cp) ? glyphs.get(cp).advance : MISSING_ADVANCE;
		return bold ? a + 1f : a;
	}

	// ---- the game's Font over it -----------------------------------------------------------------

	/** A real {@link Font} whose metrics are these glyphs; it can measure and wrap, not draw. */
	Font font() {
		GlyphSource source = new GlyphSource() {
			@Override
			public BakedGlyph getGlyph(int codepoint) {
				return baked(codepoint);
			}

			@Override
			public BakedGlyph getRandomGlyph(RandomSource random, int codepoint) {
				return baked(codepoint);
			}
		};
		return new Font(new Font.Provider() {
			@Override
			public GlyphSource glyphs(FontDescription description) {
				return source;
			}

			@Override
			public EffectGlyph effect() {
				return (x0, y0, x1, y1, depth, color, shadowColor, shadowOffset) -> {
					throw new UnsupportedOperationException("the headless font only measures");
				};
			}
		});
	}

	private BakedGlyph baked(int cp) {
		GlyphInfo info = GlyphInfo.simple(advance(cp, false));
		return new BakedGlyph() {
			@Override
			public GlyphInfo info() {
				return info;
			}

			@Override
			public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset,
					float shadowOffset) {
				throw new UnsupportedOperationException("the headless font only measures");
			}
		};
	}
}
