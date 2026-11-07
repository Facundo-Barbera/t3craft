package dev.t3craft.hq.construct;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.hq.HqLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@code <world>/t3craft/hq/construction.json}: the site the HQ was applied to, the applied layout
 * ({@link HqLayout#toJson}, so desks and modules stay put across sessions) and the pause flag.
 * Written to a temp file and moved into place. An unreadable file is kept aside as
 * {@code construction.json.bad} and the HQ starts from an empty layout (the site's own record still
 * says what is built).
 */
public final class HqStore {
	public static final int FORMAT = 1;
	private final Path file;

	public HqStore(Path worldRoot) {
		this(worldRoot, "construction.json");
	}

	/** Another file in the same folder (the demo world keeps its own). */
	public HqStore(Path worldRoot, String fileName) {
		this.file = worldRoot.resolve("t3craft").resolve("hq").resolve(fileName);
	}

	public Path file() {
		return file;
	}

	public HqConstruction.Saved load() {
		if (!Files.exists(file)) {
			return HqConstruction.Saved.EMPTY;
		}
		try {
			JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!o.has("format") || o.get("format").getAsInt() != FORMAT) {
				throw new IllegalStateException("unknown format");
			}
			String site = o.has("site") && !o.get("site").isJsonNull() ? o.get("site").getAsString() : null;
			boolean paused = o.has("paused") && o.get("paused").getAsBoolean();
			return new HqConstruction.Saved(site, HqLayout.fromJson(o.get("layout")), paused);
		} catch (IOException | RuntimeException e) {
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// keep going with an empty layout
			}
			return HqConstruction.Saved.EMPTY;
		}
	}

	public void save(HqConstruction.Saved s) throws IOException {
		JsonObject o = new JsonObject();
		o.addProperty("format", FORMAT);
		if (s.siteId() != null) {
			o.addProperty("site", s.siteId());
		}
		o.addProperty("paused", s.paused());
		o.add("layout", s.layout().toJson());
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
