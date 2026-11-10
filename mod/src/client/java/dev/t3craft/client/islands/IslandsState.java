package dev.t3craft.client.islands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which islands and bridges the world already has, in {@code t3craft-islands.json} next to {@code level.dat}.
 * The field is deterministic, so this is the only thing that has to be remembered: an island or bridge in the
 * file is never built again (the player's edits stay), one that is not (never built, or interrupted) is built
 * from scratch. Server thread.
 */
final class IslandsState {
	private static final String FILE = "t3craft-islands.json";

	private final Path file;
	private final Set<String> islands = new TreeSet<>();
	private final Set<String> bridges = new TreeSet<>();
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
					read(e.getAsJsonObject(), "islands", s.islands);
					read(e.getAsJsonObject(), "bridges", s.bridges);
				}
			} catch (IOException | RuntimeException ex) {
				T3Craft.LOGGER.warn("[islands] could not read {} ({}); the islands are checked again as players near them", FILE,
						ex.getClass().getSimpleName());
			}
		}
		return s;
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

	void saveIfDirty() {
		if (!dirty) {
			return;
		}
		JsonObject o = new JsonObject();
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
