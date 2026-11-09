package dev.t3craft.hq.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * feat/hq-layout-packs: what one {@code <layoutId>.json} (plus its {@code rooms/<layoutId>/*.nbt})
 * changes in a layout. Immutable; an empty pack changes nothing. See docs/feat-hq-layout-packs.md.
 *
 * @param roles   {@code HqStyle.Role} name (upper case) → replacement
 * @param palette field of {@code dev.t3craft.hq.Palette} → replacement
 * @param blocks  any block → replacement (applied last, over everything)
 * @param params  {@link LayoutParams} key → value (clamped when read)
 * @param rooms   module id → room
 * @param hash    hash of everything above ("" for the empty pack)
 */
public record LayoutPack(String layoutId, Map<String, Replacement> roles, Map<String, Replacement> palette, Map<Block, Replacement> blocks,
		Map<String, Integer> params, Map<String, Room> rooms, String hash) {
	/** A replacement block: the text as written, the parsed state and the properties written explicitly (they win over carried ones). */
	public record Replacement(String text, BlockState state, Set<String> fixed) {
		static Replacement of(String text) {
			return new Replacement(text, BlockStateText.parse(text), BlockStateText.explicitProperties(text));
		}

		/** {@code original} turned into this block, keeping its facing/half/axis... unless written here. */
		public BlockState apply(BlockState original) {
			return BlockStateText.carry(original, state, fixed);
		}
	}

	/** A module's room: the structure, its rotation and its offset from the module box's minimum corner. */
	public record Room(RoomTemplate template, Rotation rotation, BlockPos offset) {
	}

	public LayoutPack {
		roles = Collections.unmodifiableMap(new TreeMap<>(roles));
		palette = Collections.unmodifiableMap(new TreeMap<>(palette));
		blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
		params = Collections.unmodifiableMap(new TreeMap<>(params));
		rooms = Collections.unmodifiableMap(new TreeMap<>(rooms));
	}

	public static LayoutPack empty(String layoutId) {
		return new LayoutPack(layoutId, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), "");
	}

	public boolean isEmpty() {
		return roles.isEmpty() && palette.isEmpty() && blocks.isEmpty() && params.isEmpty() && rooms.isEmpty();
	}

	/** A pack problem with where it is ({@code file:line}). */
	public static final class PackException extends Exception {
		public PackException(String message) {
			super(message);
		}
	}

	/**
	 * Parses {@code json} (the text of {@code <layoutId>.json}); {@code rooms}: rooms already read from
	 * the folder (file options in the JSON apply to them). Throws with file and line on any error.
	 */
	public static LayoutPack parse(String layoutId, String fileName, String json, Map<String, RoomTemplate> rooms) throws PackException {
		JsonObject root;
		try {
			JsonReader r = new JsonReader(new StringReader(json));
			r.setStrictness(Strictness.LENIENT); // comments and trailing commas are fine in a hand-edited file
			JsonElement e = JsonParser.parseReader(r);
			if (e.isJsonNull()) {
				root = new JsonObject();
			} else if (!e.isJsonObject()) {
				throw new PackException(fileName + ":1: the file must hold one JSON object");
			} else {
				root = e.getAsJsonObject();
			}
		} catch (RuntimeException e) {
			throw new PackException(fileName + ": " + gsonMessage(e));
		}
		Map<String, Replacement> roleMap = new TreeMap<>();
		Map<String, Replacement> paletteMap = new TreeMap<>();
		Map<Block, Replacement> blockMap = new LinkedHashMap<>();
		Map<String, Integer> paramMap = new TreeMap<>();
		Map<String, Room> roomMap = new TreeMap<>();
		for (Map.Entry<String, JsonElement> top : root.entrySet()) {
			String k = top.getKey();
			JsonElement v = top.getValue();
			switch (k) {
				case "layout", "_comment", "_doc", "_params", "_roles", "_palette" -> {
					// informative keys written by the export
				}
				case "roles" -> {
					for (Map.Entry<String, JsonElement> e : object(fileName, json, k, v).entrySet()) {
						if (e.getKey().startsWith("_")) {
							continue;
						}
						String role = e.getKey().trim().toUpperCase(Locale.ROOT);
						if (!PackRoles.known(role)) {
							throw new PackException(at(fileName, json, e.getKey()) + "unknown role '" + e.getKey() + "' (known: " + PackRoles.names() + ")");
						}
						roleMap.put(role, override(fileName, json, e.getKey(), e.getValue()));
					}
				}
				case "palette" -> {
					for (Map.Entry<String, JsonElement> e : object(fileName, json, k, v).entrySet()) {
						if (e.getKey().startsWith("_")) {
							continue;
						}
						if (!PackRoles.knownPaletteField(e.getKey())) {
							throw new PackException(at(fileName, json, e.getKey()) + "unknown palette field '" + e.getKey() + "' (known: "
									+ PackRoles.paletteFields() + ")");
						}
						paletteMap.put(e.getKey(), override(fileName, json, e.getKey(), e.getValue()));
					}
				}
				case "blocks" -> {
					for (Map.Entry<String, JsonElement> e : object(fileName, json, k, v).entrySet()) {
						if (e.getKey().startsWith("_")) {
							continue;
						}
						Block from;
						try {
							from = BlockStateText.block(e.getKey());
						} catch (IllegalArgumentException x) {
							throw new PackException(at(fileName, json, e.getKey()) + x.getMessage());
						}
						blockMap.put(from, override(fileName, json, e.getKey(), e.getValue()));
					}
				}
				case "params" -> {
					for (Map.Entry<String, JsonElement> e : object(fileName, json, k, v).entrySet()) {
						if (e.getKey().startsWith("_")) {
							continue;
						}
						LayoutParams.Spec spec = LayoutParams.find(layoutId, e.getKey());
						if (spec == null) {
							throw new PackException(at(fileName, json, e.getKey()) + "unknown parameter '" + e.getKey() + "' for layout " + layoutId);
						}
						JsonElement n = e.getValue();
						if (!n.isJsonPrimitive() || !n.getAsJsonPrimitive().isNumber()) {
							throw new PackException(at(fileName, json, e.getKey()) + "'" + e.getKey() + "' must be a number");
						}
						paramMap.put(e.getKey(), spec.fromPack(n.getAsDouble()));
					}
				}
				case "rooms" -> {
					for (Map.Entry<String, JsonElement> e : object(fileName, json, k, v).entrySet()) {
						if (e.getKey().startsWith("_")) {
							continue;
						}
						RoomTemplate t = rooms.get(e.getKey());
						if (t == null) {
							throw new PackException(at(fileName, json, e.getKey()) + "room '" + e.getKey() + "' has no file rooms/" + layoutId + "/"
									+ e.getKey() + ".nbt");
						}
						roomMap.put(e.getKey(), room(fileName, json, e.getKey(), e.getValue(), t));
					}
				}
				default -> throw new PackException(at(fileName, json, k) + "unknown section '" + k + "' (roles, palette, blocks, params, rooms)");
			}
		}
		for (Map.Entry<String, RoomTemplate> e : rooms.entrySet()) {
			roomMap.putIfAbsent(e.getKey(), new Room(e.getValue(), Rotation.NONE, BlockPos.ZERO));
		}
		return build(layoutId, roleMap, paletteMap, blockMap, paramMap, roomMap);
	}

	/** A pack of only rooms (no JSON file). */
	public static LayoutPack ofRooms(String layoutId, Map<String, RoomTemplate> rooms) {
		Map<String, Room> roomMap = new TreeMap<>();
		rooms.forEach((k, t) -> roomMap.put(k, new Room(t, Rotation.NONE, BlockPos.ZERO)));
		return build(layoutId, Map.of(), Map.of(), Map.of(), Map.of(), roomMap);
	}

	private static LayoutPack build(String layoutId, Map<String, Replacement> roles, Map<String, Replacement> palette, Map<Block, Replacement> blocks,
			Map<String, Integer> params, Map<String, Room> rooms) {
		boolean empty = roles.isEmpty() && palette.isEmpty() && blocks.isEmpty() && params.isEmpty() && rooms.isEmpty();
		if (empty) {
			return empty(layoutId);
		}
		StringBuilder sb = new StringBuilder("pack1|").append(layoutId).append('\n');
		roles.forEach((k, o) -> sb.append("role ").append(k).append('=').append(BlockStateText.format(o.state())).append(o.fixed()).append('\n'));
		palette.forEach((k, o) -> sb.append("pal ").append(k).append('=').append(BlockStateText.format(o.state())).append(o.fixed()).append('\n'));
		blocks.forEach((k, o) -> sb.append("blk ").append(BlockStateText.format(k.defaultBlockState())).append('=')
				.append(BlockStateText.format(o.state())).append(o.fixed()).append('\n'));
		params.forEach((k, n) -> sb.append("par ").append(k).append('=').append(n).append('\n'));
		rooms.forEach((k, r) -> sb.append("room ").append(k).append(' ').append(r.rotation()).append(' ').append(r.offset().toShortString()).append('\n')
				.append(r.template().digestText()));
		return new LayoutPack(layoutId, roles, palette, blocks, params, rooms, sha(sb.toString()));
	}

	private static Room room(String file, String json, String key, JsonElement v, RoomTemplate t) throws PackException {
		if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
			return new Room(t, Rotation.NONE, BlockPos.ZERO);
		}
		if (!v.isJsonObject()) {
			throw new PackException(at(file, json, key) + "room '" + key + "' must be an object {rotation, offset}");
		}
		JsonObject o = v.getAsJsonObject();
		Rotation rot = Rotation.NONE;
		if (o.has("rotation")) {
			String r = o.get("rotation").getAsString().trim().toLowerCase(Locale.ROOT);
			rot = switch (r) {
				case "none", "0" -> Rotation.NONE;
				case "clockwise_90", "90" -> Rotation.CLOCKWISE_90;
				case "180", "clockwise_180" -> Rotation.CLOCKWISE_180;
				case "counterclockwise_90", "270", "-90" -> Rotation.COUNTERCLOCKWISE_90;
				default -> throw new PackException(at(file, json, "rotation") + "rotation must be none, clockwise_90, 180 or counterclockwise_90");
			};
		}
		BlockPos off = BlockPos.ZERO;
		if (o.has("offset")) {
			JsonElement a = o.get("offset");
			if (!a.isJsonArray() || a.getAsJsonArray().size() != 3) {
				throw new PackException(at(file, json, "offset") + "offset must be [x, y, z]");
			}
			off = new BlockPos(a.getAsJsonArray().get(0).getAsInt(), a.getAsJsonArray().get(1).getAsInt(), a.getAsJsonArray().get(2).getAsInt());
		}
		return new Room(t, rot, off);
	}

	private static Replacement override(String file, String json, String key, JsonElement v) throws PackException {
		if (!v.isJsonPrimitive() || !((JsonPrimitive) v).isString()) {
			throw new PackException(at(file, json, key) + "'" + key + "' must be a block state string like \"minecraft:oak_planks\"");
		}
		try {
			return Replacement.of(v.getAsString());
		} catch (IllegalArgumentException e) {
			throw new PackException(at(file, json, key) + e.getMessage());
		}
	}

	private static JsonObject object(String file, String json, String key, JsonElement v) throws PackException {
		if (!v.isJsonObject()) {
			throw new PackException(at(file, json, key) + "'" + key + "' must be an object");
		}
		return v.getAsJsonObject();
	}

	/** {@code file:line: } of the first line that mentions {@code "key"}. */
	static String at(String file, String json, String key) {
		return file + ":" + lineOf(json, "\"" + key + "\"") + ": ";
	}

	static int lineOf(String text, String needle) {
		int i = text.indexOf(needle);
		if (i < 0) {
			return 1;
		}
		int line = 1;
		for (int k = 0; k < i; k++) {
			if (text.charAt(k) == '\n') {
				line++;
			}
		}
		return line;
	}

	/** Gson's message, which carries "line N column M". */
	private static String gsonMessage(RuntimeException e) {
		Throwable c = e.getCause() != null ? e.getCause() : e;
		String m = c.getMessage() == null ? c.toString() : c.getMessage();
		int nl = m.indexOf('\n');
		return nl < 0 ? m : m.substring(0, nl);
	}

	static String sha(String text) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(d, 0, 12);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Parameter value as written (null: the code default). */
	public @Nullable Integer param(String key) {
		return params.get(key);
	}
}
