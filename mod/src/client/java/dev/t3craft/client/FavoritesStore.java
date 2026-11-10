package dev.t3craft.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.t3craft.T3Craft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * A set of favorite keys kept in {@code config/t3craft/<file>}, a JSON array of strings: the piece browser
 * ({@code piece-favorites.json}) and the model picker ({@code model-favorites.json}). Read on first use,
 * written on every change. The keys mean whatever the owner decides; the store only keeps them. Thread-safe.
 */
public final class FavoritesStore {
	private static final Gson GSON = new Gson();

	private final String fileName;
	private final String owner;
	private final Set<String> keys = new TreeSet<>();
	private boolean loaded;

	/**
	 * @param fileName the file under {@link ModConfig#dataDir()}
	 * @param owner    who is reading and writing, for the log line when the disk refuses
	 */
	public FavoritesStore(String fileName, String owner) {
		this.fileName = fileName;
		this.owner = owner;
	}

	public synchronized boolean isFavorite(String key) {
		load();
		return keys.contains(key);
	}

	public synchronized int count() {
		load();
		return keys.size();
	}

	/** Flips the favorite flag of {@code key}; returns the new value. */
	public synchronized boolean toggle(String key) {
		load();
		boolean now = keys.add(key);
		if (!now) {
			keys.remove(key);
		}
		save();
		return now;
	}

	private Path file() {
		return ModConfig.dataDir().resolve(fileName);
	}

	private void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return;
		}
		try {
			JsonArray a = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonArray.class);
			if (a != null) {
				for (JsonElement e : a) {
					if (e.isJsonPrimitive()) {
						keys.add(e.getAsString());
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.warn("{}: could not read {}", owner, f, e);
		}
	}

	private void save() {
		Path f = file();
		try {
			Files.createDirectories(f.getParent());
			JsonArray a = new JsonArray();
			keys.forEach(a::add);
			Files.writeString(f, GSON.toJson(a), StandardCharsets.UTF_8);
		} catch (IOException e) {
			T3Craft.LOGGER.warn("{}: could not write {}", owner, f, e);
		}
	}
}
