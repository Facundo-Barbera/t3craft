package dev.t3craft.client.chat.compose;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/**
 * Unsent text per thread ({@code env:threadId}), like T3 Code's composer draft store
 * ({@code apps/web/src/composerDraftStore.ts}): switching threads keeps what you were writing.
 * At most {@value #MAX} threads, least recently used dropped first. Empty drafts are not kept.
 *
 * <p>feat/t3-composer: {@link #shared()} is one store for the whole game session, so closing the
 * T3 screen (a new {@code T3Screen} each time) keeps the drafts and the images waiting in them.
 * Its texts are also written to {@code config/t3craft/cache/drafts.json} (only {@code env:threadId}
 * → text and caret: no tokens, URLs or images) and read back at the next game start. The write is
 * coalesced on a daemon thread (temp file + move). Images stay in memory only.
 */
public final class ComposerDrafts {
	static final int MAX = 64;
	/** Per draft on disk (a pasted log is clipped, not dropped). */
	static final int MAX_DISK_CHARS = ComposerText.MAX_CHARS;
	private static final int FORMAT = 1;

	public record Draft(String text, int caret) {
	}

	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "t3craft-drafts");
		t.setDaemon(true);
		return t;
	});
	private static @Nullable ComposerDrafts shared;

	private final LinkedHashMap<String, Draft> drafts = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Draft> e) {
			return size() > MAX;
		}
	};
	/** Images waiting per thread (memory only, never written). */
	private final Map<String, List<ImageAttachment>> images = new HashMap<>();
	private final @Nullable Path file;
	private volatile @Nullable String lastWritten;

	/** A store that lives only in memory (tests, hosts that want their own). */
	public ComposerDrafts() {
		this(null);
	}

	private ComposerDrafts(@Nullable Path file) {
		this.file = file;
	}

	/** The game-wide store, loaded from disk the first time. Client thread. */
	public static synchronized ComposerDrafts shared() {
		if (shared == null) {
			Path dir;
			try {
				dir = dev.t3craft.client.ModConfig.dataDir();
			} catch (RuntimeException | LinkageError e) {
				dir = Path.of("config", "t3craft");
			}
			ComposerDrafts d = new ComposerDrafts(dir.resolve("cache").resolve("drafts.json"));
			d.load();
			shared = d;
		}
		return shared;
	}

	public void save(String key, String text, int caret) {
		Draft before = drafts.get(key);
		if (text.isEmpty()) {
			if (drafts.remove(key) != null) {
				persist();
			}
		} else if (before == null || !before.text().equals(text) || before.caret() != caret) {
			drafts.put(key, new Draft(text, caret));
			persist();
		}
	}

	public @Nullable Draft get(String key) {
		return drafts.get(key);
	}

	public void remove(String key) {
		if (drafts.remove(key) != null) {
			persist();
		}
		images.remove(key);
	}

	public int size() {
		return drafts.size();
	}

	// ---- images -------------------------------------------------------------------------------

	public List<ImageAttachment> images(String key) {
		return images.getOrDefault(key, List.of());
	}

	public void setImages(String key, List<ImageAttachment> list) {
		if (list.isEmpty()) {
			images.remove(key);
		} else {
			images.put(key, List.copyOf(list));
		}
	}

	/** Every image held by any draft (the thumbnails still needed). */
	public List<ImageAttachment> allImages() {
		List<ImageAttachment> out = new ArrayList<>();
		images.values().forEach(out::addAll);
		return out;
	}

	// ---- disk ---------------------------------------------------------------------------------

	private void load() {
		Path f = file;
		if (f == null || !Files.isRegularFile(f)) {
			return;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			if (!root.isJsonObject() || root.getAsJsonObject().get("drafts") == null || !root.getAsJsonObject().get("drafts").isJsonObject()) {
				return;
			}
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().getAsJsonObject("drafts").entrySet()) {
				if (!e.getValue().isJsonObject()) {
					continue;
				}
				JsonObject o = e.getValue().getAsJsonObject();
				JsonElement t = o.get("text");
				if (t == null || !t.isJsonPrimitive()) {
					continue;
				}
				String text = ComposerText.clean(t.getAsString(), MAX_DISK_CHARS);
				int caret = o.get("caret") != null && o.get("caret").isJsonPrimitive() ? o.get("caret").getAsInt() : text.length();
				if (!text.isEmpty()) {
					drafts.put(e.getKey(), new Draft(text, Math.clamp(caret, 0, text.length())));
				}
			}
		} catch (Exception e) {
			T3Craft.LOGGER.warn("[chat] drafts file unreadable, ignoring it: {}", e.getClass().getSimpleName());
		}
	}

	/** Writes the texts when they changed (built here, written on the IO thread). */
	private void persist() {
		Path f = file;
		if (f == null) {
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT);
		JsonObject ds = new JsonObject();
		drafts.forEach((k, d) -> {
			JsonObject o = new JsonObject();
			String text = d.text().length() > MAX_DISK_CHARS ? d.text().substring(0, MAX_DISK_CHARS) : d.text();
			o.addProperty("text", text);
			o.addProperty("caret", Math.min(d.caret(), text.length()));
			ds.add(k, o);
		});
		root.add("drafts", ds);
		String json = root.toString();
		if (json.equals(lastWritten)) {
			return;
		}
		lastWritten = json;
		CompletableFuture.runAsync(() -> {
			// only the newest write matters: a later one supersedes this one
			if (!json.equals(lastWritten)) {
				return;
			}
			try {
				Files.createDirectories(f.getParent());
				Path tmp = f.resolveSibling("drafts.json.tmp");
				Files.writeString(tmp, json, StandardCharsets.UTF_8);
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				T3Craft.LOGGER.warn("[chat] could not write the drafts file: {}", e.getClass().getSimpleName());
			}
		}, IO);
	}
}
