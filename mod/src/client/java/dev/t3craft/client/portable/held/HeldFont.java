package dev.t3craft.client.portable.held;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.t3craft.T3Craft;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Loads {@link BitmapFont} from the game's own bitmap sheet ({@code minecraft:textures/font/ascii.png}
 * and the {@code chars} of its provider in {@code minecraft:font/include/default.json}), so no Mojang
 * asset is shipped. Falls back to the classic 16×16 ASCII grid for the chars, and to box glyphs if
 * the sheet cannot be read at all.
 */
final class HeldFont {
	static final Identifier SHEET = Identifier.withDefaultNamespace("textures/font/ascii.png");
	static final Identifier PROVIDERS = Identifier.withDefaultNamespace("font/include/default.json");

	private HeldFont() {
	}

	static BitmapFont load(ResourceManager rm) {
		try (InputStream in = rm.open(SHEET); NativeImage img = NativeImage.read(in)) {
			int w = img.getWidth();
			int h = img.getHeight();
			int[] argb = new int[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					argb[y * w + x] = img.getPixel(x, y);
				}
			}
			return BitmapFont.fromSheet(argb, w, h, chars(rm));
		} catch (Exception e) {
			T3Craft.LOGGER.warn("[portable] held screen font unavailable ({}); using boxes", e.getClass().getSimpleName());
			return BitmapFont.boxes();
		}
	}

	private static List<String> chars(ResourceManager rm) {
		try (Reader r = rm.openAsReader(PROVIDERS)) {
			for (JsonElement p : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("providers")) {
				JsonObject o = p.getAsJsonObject();
				if (o.has("file") && o.get("file").getAsString().endsWith("font/ascii.png") && o.has("chars")) {
					List<String> rows = new ArrayList<>();
					o.getAsJsonArray("chars").forEach(e -> rows.add(e.getAsString()));
					return rows;
				}
			}
		} catch (Exception e) {
			// fall through to the classic layout
		}
		return BitmapFont.classicAsciiGrid();
	}
}
