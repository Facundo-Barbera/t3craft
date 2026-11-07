package dev.t3craft.client.workstation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The workstations' own block in {@code config/t3craft.json} (additive; nothing else changes):
 *
 * <pre>{@code "workstations": { "enabled": true }}</pre>
 *
 * Missing block or key → defaults (screens on). Kept out of {@code T3CraftSettings} on purpose so
 * no other branch's record constructor changes: {@code ConfigCodec} merges over the JSON it read, so
 * this key survives saves from the settings screen. After {@link #save} the caller reloads
 * {@code ConfigManager} so its merge base includes the new value.
 */
public record WorkstationConfig(boolean enabled) {
	public static final String KEY = "workstations";
	public static final WorkstationConfig DEFAULTS = new WorkstationConfig(true);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Reads our block from the whole config object; tolerant of wrong types (→ default). */
	public static WorkstationConfig parse(JsonObject root) {
		if (root == null || !(root.get(KEY) instanceof JsonObject o)) {
			return DEFAULTS;
		}
		JsonElement e = o.get("enabled");
		boolean enabled = e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : DEFAULTS.enabled;
		return new WorkstationConfig(enabled);
	}

	/** Writes our block into {@code root} (in place), keeping every other key, ours included. */
	public JsonObject writeInto(JsonObject root) {
		JsonObject o = root.get(KEY) instanceof JsonObject existing ? existing : new JsonObject();
		o.addProperty("enabled", enabled);
		root.add(KEY, o);
		return root;
	}

	/** Loads from {@code file}; missing or unreadable file → defaults (never throws). */
	public static WorkstationConfig load(Path file) {
		try {
			if (!Files.exists(file)) {
				return DEFAULTS;
			}
			JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
			return parse(root);
		} catch (IOException | JsonParseException | IllegalStateException e) {
			return DEFAULTS;
		}
	}

	/**
	 * Saves into {@code file} atomically, merged over its current content. Refuses (IOException) to
	 * touch a file it cannot parse, so a hand-broken config is never overwritten from here.
	 */
	public void save(Path file) throws IOException {
		JsonObject root = new JsonObject();
		if (Files.exists(file)) {
			try {
				JsonObject read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
				if (read != null) {
					root = read;
				}
			} catch (JsonParseException | IllegalStateException e) {
				throw new IOException("config file is not valid JSON; not overwriting it");
			}
		}
		writeInto(root);
		Path dir = file.toAbsolutePath().getParent();
		Files.createDirectories(dir);
		Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
		try {
			Files.writeString(tmp, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}
}
