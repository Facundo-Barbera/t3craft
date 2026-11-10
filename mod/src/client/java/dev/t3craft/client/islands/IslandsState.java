package dev.t3craft.client.islands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import dev.t3craft.mapgen.islands.IslandMix;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the islands world has to remember, in {@code t3craft-islands.json} next to {@code level.dat}: the seed and
 * size mix its field is generated from, and which islands and bridges it already has. The field is deterministic,
 * so that is all: an island or bridge in the file is never built again (the player's edits stay), one that is
 * not (never built, or interrupted) is built from scratch. Server thread.
 *
 * <pre>{"generator": "islands-v2", "seed": "80740369183859", "mix": "S40,M30,L20,XG10", "islands": [...], "bridges": [...]}</pre>
 * The seed is a string (a JSON number may lose digits of a long elsewhere). A file without seed or mix (or none
 * at all) has no settings yet: {@link IslandsClient} gives it the ones the world was created with.
 */
final class IslandsState {
	private static final String FILE = "t3craft-islands.json";
	private static final String GENERATOR = "islands-v2";

	private final Path file;
	private final Set<String> islands = new TreeSet<>();
	private final Set<String> bridges = new TreeSet<>();
	private boolean hasSettings;
	private long seed = IslandsWorld.DEFAULT_SEED;
	private IslandMix mix = IslandMix.DEFAULT;
	private boolean dirty;

	private IslandsState(Path file) {
		this.file = file;
	}

	static IslandsState load(Path worldRoot) {
		IslandsState s = new IslandsState(worldRoot.resolve(FILE));
		if (Files.exists(s.file)) {
			try {
				JsonElement e = JsonParser.parseString(Files.readString(s.file, StandardCharsets.UTF_8));
				if (e.isJsonObject()) {
					JsonObject o = e.getAsJsonObject();
					read(o, "islands", s.islands);
					read(o, "bridges", s.bridges);
					readSettings(o, s);
				}
			} catch (IOException | RuntimeException ex) {
				T3Craft.LOGGER.warn("[islands] could not read {} ({}); the islands are checked again as players near them", FILE,
						ex.getClass().getSimpleName());
			}
		}
		return s;
	}

	private static void readSettings(JsonObject o, IslandsState s) {
		if (!o.has("seed") || !o.has("mix") || !o.get("seed").isJsonPrimitive() || !o.get("mix").isJsonPrimitive()) {
			return;
		}
		IslandMix m = IslandMix.parse(o.get("mix").getAsString());
		long seed;
		try {
			seed = Long.parseLong(o.get("seed").getAsString().trim());
		} catch (NumberFormatException ex) {
			T3Craft.LOGGER.warn("[islands] {} has an unreadable seed; using the default", FILE);
			return;
		}
		if (m == null) {
			T3Craft.LOGGER.warn("[islands] {} has an unreadable size mix '{}'; using the default", FILE, o.get("mix").getAsString());
			m = IslandMix.DEFAULT;
		}
		s.seed = seed;
		s.mix = m;
		s.hasSettings = true;
	}

	private static void read(JsonObject o, String key, Set<String> into) {
		if (o.has(key) && o.get(key).isJsonArray()) {
			for (JsonElement x : o.getAsJsonArray(key)) {
				if (x.isJsonPrimitive()) {
					into.add(x.getAsString());
				}
			}
		}
	}

	/** Whether the file named a seed and a mix. */
	boolean hasSettings() {
		return hasSettings;
	}

	long seed() {
		return seed;
	}

	IslandMix mix() {
		return mix;
	}

	/** Records the field's seed and mix (keeps what is built). */
	void settings(long newSeed, IslandMix newMix) {
		dirty |= !hasSettings || newSeed != seed || !newMix.equals(mix);
		seed = newSeed;
		mix = newMix;
		hasSettings = true;
	}

	/** Forgets every island and bridge and records a new seed and mix: what a regeneration ends with. */
	void reset(long newSeed, IslandMix newMix) {
		islands.clear();
		bridges.clear();
		seed = newSeed;
		mix = newMix;
		hasSettings = true;
		dirty = true;
	}

	boolean hasIsland(String id) {
		return islands.contains(id);
	}

	boolean hasBridge(String id) {
		return bridges.contains(id);
	}

	void addIsland(String id) {
		dirty |= islands.add(id);
	}

	void addBridge(String id) {
		dirty |= bridges.add(id);
	}

	int islandCount() {
		return islands.size();
	}

	int bridgeCount() {
		return bridges.size();
	}

	/** The ids of the islands built so far (a copy). */
	Set<String> islandIds() {
		return Set.copyOf(islands);
	}

	void saveIfDirty() {
		if (!dirty) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("generator", GENERATOR);
		if (hasSettings) {
			o.addProperty("seed", Long.toString(seed));
			o.addProperty("mix", mix.text());
		}
		o.add("islands", array(islands));
		o.add("bridges", array(bridges));
		Path tmp = file.resolveSibling(FILE + ".tmp");
		try {
			Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException | RuntimeException ex) {
			T3Craft.LOGGER.warn("[islands] could not save {} ({})", FILE, ex.getClass().getSimpleName());
		}
	}

	private static JsonArray array(Set<String> ids) {
		JsonArray a = new JsonArray();
		ids.forEach(a::add);
		return a;
	}
}
