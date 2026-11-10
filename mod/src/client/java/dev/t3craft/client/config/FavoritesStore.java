package dev.t3craft.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.t3craft.T3Craft;
import dev.t3craft.client.ModConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * A set of favorite keys kept in one JSON file of {@code config/t3craft/} (a plain array of strings, e.g.
 * {@code piece-favorites.json} for the piece browser, {@code model-favorites.json} for the model picker).
 * The file is read on first use and rewritten on every change; a missing or broken file is an empty set.
 * Thread-safe. {@link #inMemory()} never touches the disk (the UI gallery).
 */
public final class FavoritesStore {
	private static final Gson GSON = new Gson();

	private final @Nullable String fileName;
	private final Set<String> favorites = new TreeSet<>();
	private boolean loaded;

	/** @param fileName a file name inside {@link ModConfig#dataDir()}, e.g. {@code piece-favorites.json} */
	public FavoritesStore(String fileName) {
		this.fileName = fileName;
	}

	private FavoritesStore() {
		this.fileName = null;
		this.loaded = true;
	}

	/** A store that is never read or written. */
	public static FavoritesStore inMemory() {
		return new FavoritesStore();
	}

	public synchronized boolean isFavorite(String key) {
		load();
		return favorites.contains(key);
	}

	public synchronized int count() {
		load();
		return favorites.size();
	}

	/** Flips the favorite flag of {@code key}; returns the new value. */
	public synchronized boolean toggle(String key) {
		load();
		boolean now = favorites.add(key);
		if (!now) {
			favorites.remove(key);
		}
		save();
		return now;
	}

	private void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path f = file();
		if (f == null || !Files.isRegularFile(f)) {
			return;
		}
		try {
			JsonArray a = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonArray.class);
			if (a != null) {
				for (JsonElement e : a) {
					if (e.isJsonPrimitive()) {
						favorites.add(e.getAsString());
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.warn("favorites: could not read {}", f, e);
		}
	}

	private void save() {
		Path f = file();
		if (f == null) {
			return;
		}
		try {
			Files.createDirectories(f.getParent());
			JsonArray a = new JsonArray();
			favorites.forEach(a::add);
			Files.writeString(f, GSON.toJson(a), StandardCharsets.UTF_8);
		} catch (IOException e) {
			T3Craft.LOGGER.warn("favorites: could not write {}", f, e);
		}
	}

	private @Nullable Path file() {
		return fileName == null ? null : ModConfig.dataDir().resolve(fileName);
	}
}
