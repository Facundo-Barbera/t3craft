package dev.t3craft.client.portable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Portable terminal settings, an additive section of {@code config/t3craft.json} (feat/config's
 * codec keeps unknown sections, so this survives its saves):
 * <pre>{@code
 * "portable": {
 *   "heldScreen": true,      // live screen on the T3 Terminal when held (map-style); false = plain item
 *   "heldShowsChat": true    // held screen shows the open thread's chat; false = always the thread list
 * }
 * }</pre>
 * Missing file, section or key → defaults (both true). Read-only here: the file is re-read only when
 * its modification time changes, checked at most every {@link #CHECK_MS}.
 */
public record PortableSettings(boolean heldScreen, boolean heldShowsChat) {
	public static final PortableSettings DEFAULTS = new PortableSettings(true, true);
	public static final long CHECK_MS = 3_000;

	public static PortableSettings parse(@Nullable String json) {
		if (json == null || json.isBlank()) {
			return DEFAULTS;
		}
		try {
			JsonElement root = JsonParser.parseString(json);
			JsonObject p = root.isJsonObject() && root.getAsJsonObject().get("portable") instanceof JsonObject o ? o : null;
			if (p == null) {
				return DEFAULTS;
			}
			return new PortableSettings(bool(p, "heldScreen", true), bool(p, "heldShowsChat", true));
		} catch (RuntimeException e) {
			return DEFAULTS; // broken JSON: feat/config reports it; the terminal keeps working
		}
	}

	private static boolean bool(JsonObject o, String key, boolean def) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
	}

	/** Caches the parsed file; re-reads when the file's mtime changes. */
	public static final class Watcher {
		private final Path file;
		private PortableSettings current = DEFAULTS;
		private long lastCheck = Long.MIN_VALUE;
		private long lastModified = Long.MIN_VALUE;

		public Watcher(Path file) {
			this.file = file;
		}

		public PortableSettings get(long nowMs) {
			if (lastCheck != Long.MIN_VALUE && nowMs - lastCheck < CHECK_MS) {
				return current;
			}
			lastCheck = nowMs;
			long mtime;
			try {
				mtime = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
			} catch (IOException e) {
				mtime = -1;
			}
			if (mtime != lastModified) {
				lastModified = mtime;
				try {
					current = parse(mtime < 0 ? null : Files.readString(file));
				} catch (IOException e) {
					current = DEFAULTS;
				}
			}
			return current;
		}
	}
}
