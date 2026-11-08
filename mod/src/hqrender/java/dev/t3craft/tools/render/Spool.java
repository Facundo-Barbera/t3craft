package dev.t3craft.tools.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** The file spool (default {@code ~/.cache/t3craft-render}): inbox, outbox, cache index and the daemon's heartbeat. */
final class Spool {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	final Path root;
	final Path inbox;
	final Path outbox;
	final Path cache;

	Spool(Path root) throws IOException {
		this.root = root;
		this.inbox = Files.createDirectories(root.resolve("inbox"));
		this.outbox = Files.createDirectories(root.resolve("outbox"));
		this.cache = Files.createDirectories(root.resolve("cache"));
	}

	static Path defaultRoot() {
		String env = System.getenv("T3CRAFT_RENDER_HOME");
		if (env != null && !env.isBlank()) {
			return Path.of(env);
		}
		return Path.of(System.getProperty("user.home"), ".cache", "t3craft-render");
	}

	Path outDir(String id) {
		return outbox.resolve(id);
	}

	/** Writes through a temp file + rename, so a reader never sees half a file. */
	static void atomicWrite(Path target, byte[] data) throws IOException {
		Path tmp = target.resolveSibling("." + target.getFileName() + ".tmp");
		Files.write(tmp, data);
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	static void atomicWrite(Path target, String text) throws IOException {
		atomicWrite(target, text.getBytes(StandardCharsets.UTF_8));
	}

	/** What an agent polls: {@code outbox/<id>/status.json}. */
	static final class Status {
		final String id;
		final String agent;
		final String kind;
		final String priority;
		String state = "queued";
		String message = "";
		String log = "";
		boolean cached;
		int position;
		long queuedAt = System.currentTimeMillis();
		long startedAt;
		long finishedAt;
		final List<String> files = new ArrayList<>();
		String report;

		Status(Request r) {
			this(r.id(), r.agent(), r.kind(), r.preview() ? "preview" : "final");
		}

		Status(String id, String agent, String kind, String priority) {
			this.id = id;
			this.agent = agent;
			this.kind = kind;
			this.priority = priority;
		}

		boolean terminal() {
			return state.equals("done") || state.equals("error");
		}

		JsonObject toJson() {
			JsonObject o = new JsonObject();
			o.addProperty("id", id);
			o.addProperty("agent", agent);
			o.addProperty("kind", kind);
			o.addProperty("priority", priority);
			o.addProperty("state", state);
			o.addProperty("message", message);
			if (!log.isEmpty()) {
				o.addProperty("log", log);
			}
			o.addProperty("cached", cached);
			if (state.equals("queued")) {
				o.addProperty("position", position);
			}
			o.addProperty("queuedAt", Instant.ofEpochMilli(queuedAt).toString());
			if (startedAt > 0) {
				o.addProperty("startedAt", Instant.ofEpochMilli(startedAt).toString());
			}
			if (finishedAt > 0) {
				o.addProperty("finishedAt", Instant.ofEpochMilli(finishedAt).toString());
			}
			JsonArray fs = new JsonArray();
			files.forEach(fs::add);
			o.add("files", fs);
			if (report != null) {
				o.addProperty("report", report);
			}
			return o;
		}
	}

	void write(Status s) {
		try {
			Path dir = Files.createDirectories(outDir(s.id));
			atomicWrite(dir.resolve("status.json"), GSON.toJson(s.toJson()));
		} catch (IOException e) {
			System.err.println("could not write status of " + s.id + ": " + e);
		}
	}

	void heartbeat(JsonObject o) {
		try {
			atomicWrite(root.resolve("daemon.json"), GSON.toJson(o));
		} catch (IOException e) {
			System.err.println("could not write heartbeat: " + e);
		}
	}

	/** Deletes outbox folders (and cache entries) whose last change is older than {@code maxAgeMs}; returns how many. */
	int clean(long maxAgeMs, java.util.Set<String> keep) {
		int n = 0;
		long limit = System.currentTimeMillis() - maxAgeMs;
		try (Stream<Path> s = Files.list(outbox)) {
			for (Path dir : (Iterable<Path>) s::iterator) {
				if (!Files.isDirectory(dir) || keep.contains(dir.getFileName().toString())) {
					continue;
				}
				FileTime t = newest(dir);
				if (t != null && t.toMillis() < limit) {
					deleteTree(dir);
					n++;
				}
			}
		} catch (IOException e) {
			System.err.println("outbox cleanup: " + e);
		}
		try (Stream<Path> s = Files.list(cache)) {
			for (Path f : (Iterable<Path>) s::iterator) {
				String id = Files.readString(f).trim();
				if (!Files.isRegularFile(outDir(id).resolve("status.json"))) {
					Files.deleteIfExists(f);
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("cache cleanup: " + e);
		}
		return n;
	}

	private static FileTime newest(Path dir) throws IOException {
		FileTime best = null;
		try (Stream<Path> s = Files.walk(dir)) {
			for (Path p : (Iterable<Path>) s::iterator) {
				FileTime t = Files.getLastModifiedTime(p);
				if (best == null || t.compareTo(best) > 0) {
					best = t;
				}
			}
		}
		return best;
	}

	static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> s = Files.walk(dir)) {
			List<Path> all = new ArrayList<>();
			s.forEach(all::add);
			for (int i = all.size() - 1; i >= 0; i--) {
				Files.deleteIfExists(all.get(i));
			}
		}
	}
}
