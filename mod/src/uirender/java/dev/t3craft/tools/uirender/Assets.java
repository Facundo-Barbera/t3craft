package dev.t3craft.tools.uirender;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

/**
 * Where the renderer finds pictures: classpath resources ({@code assets/<namespace>/...} of the
 * Minecraft jars and of the mod, exactly the lookup the game does through its resource packs, minus
 * resource-pack overrides), plus in-memory textures a scenario registers (the composer's image
 * thumbnails). Nothing is copied to disk.
 */
final class Assets {
	/** Metadata of a GUI sprite: how it scales ({@code gui.scaling} in its {@code .mcmeta}). */
	record Scaling(String type, int width, int height, int left, int top, int right, int bottom, boolean stretchInner) {
		static final Scaling STRETCH = new Scaling("stretch", 0, 0, 0, 0, 0, 0, false);
	}

	/** A sprite picture (first frame when animated) and its scaling. */
	record SpriteImage(BufferedImage image, Scaling scaling) {
	}

	private static final Map<String, BufferedImage> IMAGES = new HashMap<>();
	private static final Map<String, SpriteImage> SPRITES = new HashMap<>();
	private static final Map<String, BufferedImage> MEMORY = new HashMap<>();
	private static final BufferedImage NONE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

	private Assets() {
	}

	static @Nullable InputStream open(String path) {
		ClassLoader[] loaders = {Thread.currentThread().getContextClassLoader(), Assets.class.getClassLoader(), ClassLoader.getSystemClassLoader()};
		for (ClassLoader l : loaders) {
			if (l == null) {
				continue;
			}
			InputStream in = l.getResourceAsStream(path);
			if (in != null) {
				return in;
			}
		}
		return null;
	}

	static @Nullable JsonObject json(String path) {
		try (InputStream in = open(path)) {
			if (in == null) {
				return null;
			}
			JsonElement e = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			return e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (IOException | RuntimeException ex) {
			return null;
		}
	}

	/** Registers (or replaces) an in-memory texture under {@code id} ({@code namespace:path}). */
	static synchronized void register(String id, BufferedImage image) {
		MEMORY.put(id, image);
	}

	static synchronized void clearMemory() {
		MEMORY.clear();
	}

	/** {@code namespace:path} → {@code assets/namespace/path}. */
	static String assetPath(String id, String prefix, String suffix) {
		int colon = id.indexOf(':');
		String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
		String p = colon < 0 ? id : id.substring(colon + 1);
		return "assets/" + ns + "/" + prefix + p + suffix;
	}

	/** A texture file by its identifier ({@code minecraft:textures/gui/...png}), or null. */
	static synchronized @Nullable BufferedImage texture(String id) {
		BufferedImage mem = MEMORY.get(id);
		if (mem != null) {
			return mem;
		}
		return read(assetPath(id, "", ""));
	}

	/** A GUI sprite by its identifier ({@code minecraft:widget/button}), or null. */
	static synchronized @Nullable SpriteImage sprite(String id) {
		SpriteImage cached = SPRITES.get(id);
		if (cached != null) {
			return cached.image() == NONE ? null : cached;
		}
		BufferedImage img = read(assetPath(id, "textures/gui/sprites/", ".png"));
		if (img == null) {
			SPRITES.put(id, new SpriteImage(NONE, Scaling.STRETCH));
			return null;
		}
		JsonObject meta = json(assetPath(id, "textures/gui/sprites/", ".png.mcmeta"));
		Scaling scaling = Scaling.STRETCH;
		if (meta != null) {
			if (meta.has("animation") && img.getHeight() > img.getWidth()) {
				img = img.getSubimage(0, 0, img.getWidth(), img.getWidth());
			}
			JsonObject gui = meta.has("gui") && meta.get("gui").isJsonObject() ? meta.getAsJsonObject("gui") : null;
			JsonObject sc = gui != null && gui.has("scaling") && gui.get("scaling").isJsonObject() ? gui.getAsJsonObject("scaling") : null;
			if (sc != null) {
				scaling = scaling(sc, img);
			}
		}
		SpriteImage out = new SpriteImage(img, scaling);
		SPRITES.put(id, out);
		return out;
	}

	private static Scaling scaling(JsonObject sc, BufferedImage img) {
		String type = sc.has("type") ? sc.get("type").getAsString() : "stretch";
		int w = sc.has("width") ? sc.get("width").getAsInt() : img.getWidth();
		int h = sc.has("height") ? sc.get("height").getAsInt() : img.getHeight();
		int l = 0;
		int t = 0;
		int r = 0;
		int b = 0;
		if (sc.has("border")) {
			JsonElement bd = sc.get("border");
			if (bd.isJsonPrimitive()) {
				l = t = r = b = bd.getAsInt();
			} else if (bd.isJsonObject()) {
				JsonObject o = bd.getAsJsonObject();
				l = o.has("left") ? o.get("left").getAsInt() : 0;
				t = o.has("top") ? o.get("top").getAsInt() : 0;
				r = o.has("right") ? o.get("right").getAsInt() : 0;
				b = o.has("bottom") ? o.get("bottom").getAsInt() : 0;
			}
		}
		boolean inner = sc.has("stretch_inner") && sc.get("stretch_inner").getAsBoolean();
		return new Scaling(type, w, h, l, t, r, b, inner);
	}

	private static @Nullable BufferedImage read(String path) {
		BufferedImage cached = IMAGES.get(path);
		if (cached != null) {
			return cached == NONE ? null : cached;
		}
		try (InputStream in = open(path)) {
			if (in == null) {
				IMAGES.put(path, NONE);
				return null;
			}
			BufferedImage raw = ImageIO.read(in);
			if (raw == null) {
				IMAGES.put(path, NONE);
				return null;
			}
			BufferedImage argb = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
			argb.getGraphics().drawImage(raw, 0, 0, null);
			IMAGES.put(path, argb);
			return argb;
		} catch (IOException | RuntimeException e) {
			IMAGES.put(path, NONE);
			return null;
		}
	}
}
