package dev.t3craft.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes {@code config/t3craft.json}. Pure java.nio + Gson (no Minecraft), so it is
 * unit-tested against a temp dir.
 * <ul>
 *   <li>Missing file: defaults are written.</li>
 *   <li>Older schema: migrated, the original kept as {@code t3craft.json.v<N>.bak}, the upgraded file
 *   written.</li>
 *   <li>Newer schema (written by a newer mod): read best-effort and never overwritten.</li>
 *   <li>Unparseable file: defaults in memory; the broken file is kept and only moved aside to
 *   {@code t3craft.json.broken.bak} on the next explicit save.</li>
 * </ul>
 * Writes are atomic (temp file + move).
 */
public final class ConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();

	private final Path file;
	private JsonObject raw = new JsonObject();
	private boolean fromFuture;
	private boolean broken;

	public ConfigStore(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	/** Result of {@link #load()}; {@code warnings} never contain config values. */
	public record Loaded(T3CraftSettings settings, List<String> warnings, boolean readOnly) {
	}

	public synchronized Loaded load() {
		List<String> warnings = new ArrayList<>();
		fromFuture = false;
		broken = false;
		if (!Files.exists(file)) {
			raw = new JsonObject();
			T3CraftSettings d = T3CraftSettings.defaults();
			try {
				write(ConfigCodec.encode(d, raw));
			} catch (IOException e) {
				warnings.add("could not write defaults: " + e.getClass().getSimpleName());
			}
			return new Loaded(d, warnings, false);
		}
		JsonObject parsed;
		try {
			JsonElement el = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!el.isJsonObject()) {
				throw new IllegalStateException("not a JSON object");
			}
			parsed = el.getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			broken = true;
			raw = new JsonObject();
			warnings.add(file.getFileName() + " is not valid JSON; using defaults (the file is left untouched until you save)");
			return new Loaded(T3CraftSettings.defaults(), warnings, false);
		}
		ConfigMigrations.Migrated m = ConfigMigrations.migrate(parsed);
		raw = m.json();
		ConfigCodec.Decoded d = ConfigCodec.decode(raw);
		warnings.addAll(d.warnings());
		if (m.fromFuture()) {
			fromFuture = true;
			warnings.add(file.getFileName() + " has schemaVersion " + m.fromVersion() + " (newer than " + T3CraftSettings.SCHEMA_VERSION
					+ "): read-only, not overwritten");
		} else if (m.changed()) {
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".v" + m.fromVersion() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
				write(ConfigCodec.encode(d.settings(), raw));
			} catch (IOException e) {
				warnings.add("could not save the migrated config: " + e.getClass().getSimpleName());
			}
		}
		return new Loaded(d.settings(), warnings, fromFuture);
	}

	/** Saves {@code s} merged over the last loaded file. False when the file is read-only (newer schema). */
	public synchronized boolean save(T3CraftSettings s) throws IOException {
		if (fromFuture) {
			return false;
		}
		if (broken && Files.exists(file)) {
			Files.move(file, file.resolveSibling(file.getFileName() + ".broken.bak"), StandardCopyOption.REPLACE_EXISTING);
			broken = false;
		}
		JsonObject out = ConfigCodec.encode(s, raw);
		write(out);
		raw = out;
		return true;
	}

	/** A top-level key of the last loaded file as is (a copy), or null; for keys owned outside {@link T3CraftSettings}. */
	public synchronized @Nullable JsonElement raw(String key) {
		JsonElement e = raw.get(key);
		return e == null ? null : e.deepCopy();
	}

	/**
	 * Sets one top-level key owned outside {@link T3CraftSettings} (e.g. {@code hqLayout}) and writes
	 * the file; later {@link #save}s keep it. False when the file is read-only (newer schema).
	 */
	public synchronized boolean putRaw(String key, JsonElement value) throws IOException {
		if (fromFuture) {
			return false;
		}
		JsonObject out = raw.deepCopy();
		out.add(key, value.deepCopy());
		write(out);
		raw = out;
		return true;
	}

	private void write(JsonObject o) throws IOException {
		Path dir = file.toAbsolutePath().getParent();
		Files.createDirectories(dir);
		Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
		try {
			Files.writeString(tmp, GSON.toJson(o) + "\n", StandardCharsets.UTF_8);
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
