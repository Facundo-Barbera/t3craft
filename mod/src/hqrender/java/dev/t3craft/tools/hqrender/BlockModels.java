package dev.t3craft.tools.hqrender;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns block states into textured quads, reading the vanilla resource format (blockstates →
 * models with parents and elements → textures) straight from the classpath, i.e. from the
 * Minecraft client jar Loom already downloaded (and from the mod's own {@code assets/t3craft}).
 * Nothing is copied or redistributed: textures are only sampled in memory to draw the render.
 *
 * <p>Covers what the HQ uses: variants (first entry of weighted lists) and multipart, x/y variant
 * rotations, element rotations (with rescale), per-face uv and uv rotation, tint by block (grass,
 * foliage, water), first frame of animated textures. Blocks drawn by block entity renderers (signs,
 * chests…) get a simple box with their particle texture.
 */
final class BlockModels {
	/** One textured (or solid) rectangle in block space (0..1), facing {@code n}. */
	static final class Quad {
		final double[] p0;
		final double[] e1;
		final double[] e2;
		final double[] n;
		final double e1len2;
		final double e2len2;
		final Texture tex;
		final double u0;
		final double v0;
		final double du;
		final double dv;
		final int uvRot;
		final int tint;
		final boolean emissive;

		Quad(double[] p0, double[] e1, double[] e2, double[] n, Texture tex, double[] uv, int uvRot, int tint, boolean emissive) {
			this.p0 = p0;
			this.e1 = e1;
			this.e2 = e2;
			this.n = n;
			this.e1len2 = dot(e1, e1);
			this.e2len2 = dot(e2, e2);
			this.tex = tex;
			this.u0 = uv[0];
			this.v0 = uv[1];
			this.du = uv[2] - uv[0];
			this.dv = uv[3] - uv[1];
			this.uvRot = uvRot;
			this.tint = tint;
			this.emissive = emissive;
		}

		/** ARGB of the texel at face parameters (a, b) in [0,1]. */
		int sample(double a, double b) {
			double ra = a;
			double rb = b;
			switch (uvRot) {
				case 90 -> {
					ra = b;
					rb = 1 - a;
				}
				case 180 -> {
					ra = 1 - a;
					rb = 1 - b;
				}
				case 270 -> {
					ra = 1 - b;
					rb = a;
				}
				default -> {
				}
			}
			int c = tex.sample((u0 + ra * du) / 16.0, (v0 + rb * dv) / 16.0);
			if (tint != 0xFFFFFF) {
				int r = ((c >> 16) & 255) * ((tint >> 16) & 255) / 255;
				int g = ((c >> 8) & 255) * ((tint >> 8) & 255) / 255;
				int bl = (c & 255) * (tint & 255) / 255;
				c = (c & 0xFF000000) | (r << 16) | (g << 8) | bl;
			}
			return c;
		}
	}

	/** A texture's first frame (square), or a solid colour. */
	static final class Texture {
		final int w;
		final int h;
		final int[] px;

		Texture(int w, int h, int[] px) {
			this.w = w;
			this.h = h;
			this.px = px;
		}

		static Texture solid(int argb) {
			return new Texture(1, 1, new int[] {argb});
		}

		int sample(double u, double v) {
			int x = (int) Math.floor(u * w);
			int y = (int) Math.floor(v * h);
			x = Math.max(0, Math.min(w - 1, x));
			y = Math.max(0, Math.min(h - 1, y));
			return px[y * w + x];
		}
	}

	static final Texture MISSING = new Texture(2, 2, new int[] {0xFFF800F8, 0xFF000000, 0xFF000000, 0xFFF800F8});

	private final ClassLoader loader = BlockModels.class.getClassLoader();
	private final Map<String, JsonObject> json = new HashMap<>();
	private final Map<String, Texture> textures = new HashMap<>();
	private final Map<BlockState, List<Quad>> byState = new HashMap<>();
	/** What could not be resolved (texture ids, block ids without quads), for the run's report. */
	final java.util.Set<String> problems = new java.util.TreeSet<>();

	synchronized List<Quad> quads(BlockState state) {
		List<Quad> q = byState.get(state);
		if (q == null) {
			q = List.copyOf(build(state));
			byState.put(state, q);
		}
		return q;
	}

	// ------------------------------------------------------------------ resources

	private JsonObject readJson(String path) {
		if (json.containsKey(path)) {
			return json.get(path);
		}
		JsonObject o = null;
		try (InputStream in = loader.getResourceAsStream(path)) {
			if (in != null) {
				o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			}
		} catch (IOException | RuntimeException e) {
			o = null;
		}
		json.put(path, o);
		return o;
	}

	private static String[] split(String id) {
		int c = id.indexOf(':');
		return c < 0 ? new String[] {"minecraft", id} : new String[] {id.substring(0, c), id.substring(c + 1)};
	}

	synchronized Texture texture(String id) {
		Texture t = textures.get(id);
		if (t != null) {
			return t;
		}
		String[] p = split(id);
		t = MISSING;
		try (InputStream in = loader.getResourceAsStream("assets/" + p[0] + "/textures/" + p[1] + ".png")) {
			if (in != null) {
				BufferedImage img = ImageIO.read(in);
				int w = img.getWidth();
				int h = Math.min(img.getHeight(), w); // animated strips: first frame
				int[] px = new int[w * h];
				img.getRGB(0, 0, w, h, px, 0, w);
				t = new Texture(w, h, px);
			}
		} catch (IOException e) {
			t = MISSING;
		}
		if (t == MISSING) {
			problems.add("texture " + id);
		}
		textures.put(id, t);
		return t;
	}

	// ------------------------------------------------------------------ blockstates

	private List<Quad> build(BlockState state) {
		if (state.isAir()) {
			return List.of();
		}
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		String[] p = split(id);
		Map<String, String> props = new HashMap<>();
		state.getValues().forEach(v -> props.put(v.property().getName(), v.valueName()));
		int tint = tintOf(p[1]);
		boolean emissive = state.getLightEmission() > 0;
		List<Quad> out = new ArrayList<>();
		JsonObject bs = readJson("assets/" + p[0] + "/blockstates/" + p[1] + ".json");
		if (bs != null && bs.has("variants")) {
			for (Map.Entry<String, JsonElement> e : bs.getAsJsonObject("variants").entrySet()) {
				if (matchesVariant(e.getKey(), props)) {
					addVariant(out, first(e.getValue()), tint, emissive, p[1]);
					break;
				}
			}
		} else if (bs != null && bs.has("multipart")) {
			for (JsonElement part : bs.getAsJsonArray("multipart")) {
				JsonObject po = part.getAsJsonObject();
				if (!po.has("when") || matchesWhen(po.getAsJsonObject("when"), props)) {
					addVariant(out, first(po.get("apply")), tint, emissive, p[1]);
				}
			}
		}
		if (out.isEmpty()) {
			fallback(out, bs, props, p[1], tint, emissive);
			problems.add("drawn as a box (no model elements): " + id);
		}
		return out;
	}

	private static JsonObject first(JsonElement e) {
		return e.isJsonArray() ? e.getAsJsonArray().get(0).getAsJsonObject() : e.getAsJsonObject();
	}

	private static boolean matchesVariant(String key, Map<String, String> props) {
		if (key.isEmpty() || key.equals("normal")) {
			return true;
		}
		for (String kv : key.split(",")) {
			String[] a = kv.split("=", 2);
			if (a.length != 2 || !a[1].equals(props.get(a[0]))) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesWhen(JsonObject when, Map<String, String> props) {
		if (when.has("OR")) {
			for (JsonElement e : when.getAsJsonArray("OR")) {
				if (matchesWhen(e.getAsJsonObject(), props)) {
					return true;
				}
			}
			return false;
		}
		if (when.has("AND")) {
			for (JsonElement e : when.getAsJsonArray("AND")) {
				if (!matchesWhen(e.getAsJsonObject(), props)) {
					return false;
				}
			}
			return true;
		}
		for (Map.Entry<String, JsonElement> e : when.entrySet()) {
			String v = props.get(e.getKey());
			boolean any = false;
			String want = e.getValue().getAsString();
			boolean negate = want.startsWith("!");
			for (String alt : (negate ? want.substring(1) : want).split("\\|")) {
				any |= alt.equals(v);
			}
			if (any == negate) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ models

	private record Resolved(Map<String, String> textures, JsonArray elements) {
	}

	private Resolved resolveModel(String modelId) {
		Map<String, String> tex = new HashMap<>();
		JsonArray elements = null;
		String cur = modelId;
		for (int depth = 0; cur != null && depth < 16; depth++) {
			String[] p = split(cur);
			JsonObject m = readJson("assets/" + p[0] + "/models/" + p[1] + ".json");
			if (m == null) {
				break;
			}
			if (m.has("textures")) {
				for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("textures").entrySet()) {
					if (e.getValue().isJsonPrimitive()) {
						tex.putIfAbsent(e.getKey(), e.getValue().getAsString());
					} else if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("sprite")) {
						// 26.x form: {"sprite": "minecraft:block/glass", "force_translucent": true}
						tex.putIfAbsent(e.getKey(), e.getValue().getAsJsonObject().get("sprite").getAsString());
					}
				}
			}
			if (elements == null && m.has("elements")) {
				elements = m.getAsJsonArray("elements");
			}
			cur = m.has("parent") ? m.get("parent").getAsString() : null;
		}
		return new Resolved(tex, elements);
	}

	private Texture resolveTexture(Map<String, String> tex, String ref) {
		String r = ref;
		for (int i = 0; i < 16 && r != null && r.startsWith("#"); i++) {
			r = tex.get(r.substring(1));
		}
		return r == null ? MISSING : texture(r);
	}

	private void addVariant(List<Quad> out, JsonObject v, int tint, boolean emissive, String blockPath) {
		Resolved m = resolveModel(v.get("model").getAsString());
		int rx = v.has("x") ? v.get("x").getAsInt() : 0;
		int ry = v.has("y") ? v.get("y").getAsInt() : 0;
		if (m.elements() == null) {
			return;
		}
		for (JsonElement ee : m.elements()) {
			JsonObject el = ee.getAsJsonObject();
			double[] from = vec(el.getAsJsonArray("from"));
			double[] to = vec(el.getAsJsonArray("to"));
			JsonObject rot = el.has("rotation") ? el.getAsJsonObject("rotation") : null;
			JsonObject faces = el.getAsJsonObject("faces");
			for (Map.Entry<String, JsonElement> fe : faces.entrySet()) {
				JsonObject f = fe.getValue().getAsJsonObject();
				Texture t = resolveTexture(m.textures(), f.get("texture").getAsString());
				double[] uv = f.has("uv") ? vec(f.getAsJsonArray("uv")) : defaultUv(fe.getKey(), from, to);
				int uvRot = f.has("rotation") ? f.get("rotation").getAsInt() : 0;
				int faceTint = f.has("tintindex") ? tint : 0xFFFFFF;
				Quad q = face(fe.getKey(), from, to, rot, rx, ry, t, uv, uvRot, faceTint, emissive);
				if (q != null) {
					out.add(q);
				}
			}
		}
	}

	private static double[] vec(JsonArray a) {
		double[] v = new double[a.size()];
		for (int i = 0; i < v.length; i++) {
			v[i] = a.get(i).getAsDouble();
		}
		return v;
	}

	private static double[] defaultUv(String face, double[] f, double[] t) {
		return switch (face) {
			case "north" -> new double[] {16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
			case "south" -> new double[] {f[0], 16 - t[1], t[0], 16 - f[1]};
			case "west" -> new double[] {f[2], 16 - t[1], t[2], 16 - f[1]};
			case "east" -> new double[] {16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
			case "up" -> new double[] {f[0], f[2], t[0], t[2]};
			default -> new double[] {f[0], 16 - t[2], t[0], 16 - f[2]};
		};
	}

	/**
	 * Builds one face: corners at face parameters (0,0), (1,0), (0,1) in model units (0..16), then
	 * element rotation, variant x then y rotation, scaled to block units.
	 */
	private static Quad face(String dir, double[] f, double[] t, JsonObject rot, int rx, int ry, Texture tex, double[] uv, int uvRot, int tint,
			boolean emissive) {
		double x1 = f[0], y1 = f[1], z1 = f[2], x2 = t[0], y2 = t[1], z2 = t[2];
		double[] a;
		double[] b;
		double[] c;
		double[] n;
		switch (dir) {
			case "north" -> {
				a = new double[] {x2, y2, z1};
				b = new double[] {x1, y2, z1};
				c = new double[] {x2, y1, z1};
				n = new double[] {0, 0, -1};
			}
			case "south" -> {
				a = new double[] {x1, y2, z2};
				b = new double[] {x2, y2, z2};
				c = new double[] {x1, y1, z2};
				n = new double[] {0, 0, 1};
			}
			case "west" -> {
				a = new double[] {x1, y2, z1};
				b = new double[] {x1, y2, z2};
				c = new double[] {x1, y1, z1};
				n = new double[] {-1, 0, 0};
			}
			case "east" -> {
				a = new double[] {x2, y2, z2};
				b = new double[] {x2, y2, z1};
				c = new double[] {x2, y1, z2};
				n = new double[] {1, 0, 0};
			}
			case "up" -> {
				a = new double[] {x1, y2, z1};
				b = new double[] {x2, y2, z1};
				c = new double[] {x1, y2, z2};
				n = new double[] {0, 1, 0};
			}
			case "down" -> {
				a = new double[] {x1, y1, z2};
				b = new double[] {x2, y1, z2};
				c = new double[] {x1, y1, z1};
				n = new double[] {0, -1, 0};
			}
			default -> {
				return null;
			}
		}
		if (rot != null) {
			double angle = rot.has("angle") ? rot.get("angle").getAsDouble() : 0;
			String axis = rot.has("axis") ? rot.get("axis").getAsString() : "y";
			double[] o = rot.has("origin") ? vec(rot.getAsJsonArray("origin")) : new double[] {8, 8, 8};
			boolean rescale = rot.has("rescale") && rot.get("rescale").getAsBoolean();
			if (angle != 0) {
				a = elementRotate(a, o, axis, angle, rescale);
				b = elementRotate(b, o, axis, angle, rescale);
				c = elementRotate(c, o, axis, angle, rescale);
				n = elementRotate(n, new double[3], axis, angle, false);
			}
		}
		double[][] pts = {a, b, c};
		for (double[] p : pts) {
			variantRotate(p, rx, ry, 8);
		}
		variantRotate(n, rx, ry, 0);
		for (double[] p : pts) {
			for (int i = 0; i < 3; i++) {
				p[i] /= 16.0;
			}
		}
		double[] e1 = sub(b, a);
		double[] e2 = sub(c, a);
		if (dot(e1, e1) < 1e-9 || dot(e2, e2) < 1e-9) {
			return null;
		}
		return new Quad(a, e1, e2, normalize(n), tex, uv, uvRot, tint, emissive);
	}

	private static double[] elementRotate(double[] p, double[] o, String axis, double deg, boolean rescale) {
		double r = Math.toRadians(deg);
		double cos = Math.cos(r);
		double sin = Math.sin(r);
		double x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
		double s = rescale ? 1 / Math.cos(r) : 1;
		double[] q;
		switch (axis) {
			case "x" -> q = new double[] {x, (y * cos - z * sin) * s, (y * sin + z * cos) * s};
			case "z" -> q = new double[] {(x * cos - y * sin) * s, (x * sin + y * cos) * s, z};
			default -> q = new double[] {(x * cos + z * sin) * s, y, (-x * sin + z * cos) * s};
		}
		return new double[] {q[0] + o[0], q[1] + o[1], q[2] + o[2]};
	}

	/** Variant rotation: x first (x=90 turns north into down), then y (y=90 turns north into east), about {@code c}. */
	private static void variantRotate(double[] p, int rx, int ry, double c) {
		for (int i = 0; i < Math.floorMod(rx, 360) / 90; i++) {
			double y = p[1] - c, z = p[2] - c;
			p[1] = z + c;
			p[2] = -y + c;
		}
		for (int i = 0; i < Math.floorMod(ry, 360) / 90; i++) {
			double x = p[0] - c, z = p[2] - c;
			p[0] = -z + c;
			p[2] = x + c;
		}
	}

	// ------------------------------------------------------------------ fallbacks and tints

	/** Block-entity-rendered blocks: a box in the shape of the block with its particle texture. */
	private void fallback(List<Quad> out, JsonObject bs, Map<String, String> props, String path, int tint, boolean emissive) {
		Texture t = MISSING;
		int ry = 0;
		if (bs != null && bs.has("variants")) {
			for (Map.Entry<String, JsonElement> e : bs.getAsJsonObject("variants").entrySet()) {
				if (matchesVariant(e.getKey(), props)) {
					JsonObject v = first(e.getValue());
					Resolved m = resolveModel(v.get("model").getAsString());
					String part = m.textures().get("particle");
					if (part != null) {
						t = resolveTexture(m.textures(), part);
					}
					ry = v.has("y") ? v.get("y").getAsInt() : 0;
					break;
				}
			}
		}
		if (t == MISSING && path.endsWith("_sign")) {
			String wood = path.replace("_wall_hanging_sign", "").replace("_hanging_sign", "").replace("_wall_sign", "").replace("_sign", "");
			t = texture("minecraft:block/" + wood + "_planks");
		}
		if (path.contains("water")) {
			t = Texture.solid(0xB03F76E4);
			box(out, new double[] {0, 0, 0}, new double[] {16, 14, 16}, t, 0, 0xFFFFFF, false);
			return;
		}
		if (path.endsWith("_wall_hanging_sign")) {
			box(out, new double[] {1, 0, 7}, new double[] {15, 10, 9}, t, ry, tint, emissive);
			box(out, new double[] {0, 14, 6}, new double[] {16, 16, 10}, t, ry, tint, emissive);
		} else if (path.endsWith("_hanging_sign")) {
			box(out, new double[] {1, 0, 7}, new double[] {15, 10, 9}, t, ry, tint, emissive);
		} else if (path.endsWith("_wall_sign")) {
			box(out, new double[] {0, 4.5, 0}, new double[] {16, 12.5, 2}, t, ry, tint, emissive);
		} else if (path.endsWith("_sign")) {
			box(out, new double[] {7.5, 0, 7.5}, new double[] {8.5, 9, 8.5}, t, 0, tint, emissive);
			box(out, new double[] {0, 9, 7}, new double[] {16, 17, 9}, t, 0, tint, emissive);
		} else if (path.contains("chest")) {
			box(out, new double[] {1, 0, 1}, new double[] {15, 14, 15}, t, 0, tint, emissive);
		} else if (!t.equals(MISSING)) {
			box(out, new double[] {2, 0, 2}, new double[] {14, 12, 14}, t, 0, tint, emissive);
		}
	}

	/** A plain box (all six faces, uv from the box bounds) with a y rotation. */
	static void box(List<Quad> out, double[] from, double[] to, Texture t, int ry, int tint, boolean emissive) {
		for (String d : new String[] {"north", "south", "west", "east", "up", "down"}) {
			Quad q = face(d, from, to, null, 0, ry, t, defaultUv(d, from, to), 0, tint, emissive);
			if (q != null) {
				out.add(q);
			}
		}
	}

	private static int tintOf(String path) {
		return switch (path) {
			case "grass_block", "short_grass", "tall_grass", "fern", "large_fern", "potted_fern", "sugar_cane", "bush" -> 0x91BD59;
			case "oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "vine" -> 0x77AB2F;
			case "spruce_leaves" -> 0x619961;
			case "birch_leaves" -> 0x80A755;
			case "lily_pad" -> 0x208030;
			case "water", "water_cauldron" -> 0x3F76E4;
			default -> 0xFFFFFF;
		};
	}

	// ------------------------------------------------------------------ vector maths

	static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	static double[] sub(double[] a, double[] b) {
		return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	static double[] normalize(double[] a) {
		double l = Math.sqrt(dot(a, a));
		return l == 0 ? a : new double[] {a[0] / l, a[1] / l, a[2] / l};
	}
}
