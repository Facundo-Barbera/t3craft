package dev.t3craft.tools.render;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanilla block models and textures, decoded once per JVM. The render daemon keeps this class in its
 * own (parent) class loader, so every job, whatever worktree's classes it runs, shares the same copy:
 * {@code BlockModels} asks here before reading a {@code minecraft} resource from the jar. Resources of
 * the mod's own namespace are never cached (they change with each worktree). Plain data only
 * ({@code int[]}, Gson trees); nothing here refers to a class a job could reload.
 */
public final class ModelCache {
	private ModelCache() {
	}

	/** A decoded texture: first frame, ARGB. */
	public record Pixels(int w, int h, int[] px) {
	}

	private static final Map<String, JsonObject> JSON = new ConcurrentHashMap<>();
	private static final Map<String, Pixels> TEXTURES = new ConcurrentHashMap<>();

	/** The parsed JSON of a classpath resource, or null when it was not cached yet. Callers must not modify it. */
	public static JsonObject json(String path) {
		return JSON.get(path);
	}

	public static void putJson(String path, JsonObject o) {
		JSON.put(path, o);
	}

	public static Pixels texture(String id) {
		return TEXTURES.get(id);
	}

	public static void putTexture(String id, Pixels p) {
		TEXTURES.put(id, p);
	}

	public static int size() {
		return JSON.size() + TEXTURES.size();
	}
}
