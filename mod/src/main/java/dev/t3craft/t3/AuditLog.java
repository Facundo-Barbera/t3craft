package dev.t3craft.t3;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Local, append-only audit of every operate attempt: {@code config/t3craft/audit.log}, one JSON
 * object per line:
 * <pre>{"time":"2026-11-07T12:00:00Z","env":"vps","thread":"t-1","action":"thread_stop","destructive":true,"result":"ok","origin":"ui","detail":"done"}</pre>
 * No tokens and no content (never the payload, never a message text): {@code detail} is the
 * result message, passed through {@link T3Connection#sanitize}, single-line and clipped.
 * Simple size rotation: past {@code maxBytes} the file becomes {@code audit.log.1} (one backup).
 * {@link #append} never throws.
 */
public final class AuditLog {
	public static final long DEFAULT_MAX_BYTES = 1L << 20;
	private static final int MAX_DETAIL = 200;

	public record Entry(long timeMs, String env, @Nullable String threadId, String action, boolean destructive,
			String result, String origin, String detail) {
	}

	private final Path file;
	private final long maxBytes;

	public AuditLog(Path file, long maxBytes) {
		this.file = file;
		this.maxBytes = Math.max(256, maxBytes);
	}

	/** {@code config/t3craft/audit.log} with the default 1 MiB rotation. */
	public static AuditLog in(Path t3craftConfigDir) {
		return new AuditLog(t3craftConfigDir.resolve("audit.log"), DEFAULT_MAX_BYTES);
	}

	public Path file() {
		return file;
	}

	public Path rotated() {
		return file.resolveSibling(file.getFileName() + ".1");
	}

	/** One JSON line (without the trailing newline). */
	public static String line(Entry e) {
		JsonObject o = new JsonObject();
		o.addProperty("time", Instant.ofEpochMilli(e.timeMs()).toString());
		o.addProperty("env", clean(e.env(), 64));
		if (e.threadId() != null) {
			o.addProperty("thread", clean(e.threadId(), 128));
		}
		o.addProperty("action", clean(e.action(), 64));
		o.addProperty("destructive", e.destructive());
		o.addProperty("result", clean(e.result(), 32));
		o.addProperty("origin", clean(e.origin(), 32));
		o.addProperty("detail", clean(e.detail(), MAX_DETAIL));
		return o.toString();
	}

	static String clean(@Nullable String s, int max) {
		String out = T3Connection.sanitize(s).replaceAll("[\\r\\n\\t\\p{Cntrl}]+", " ").trim();
		return out.length() > max ? out.substring(0, max) + "…" : out;
	}

	public synchronized void append(Entry e) {
		try {
			byte[] bytes = (line(e) + "\n").getBytes(StandardCharsets.UTF_8);
			Files.createDirectories(file.getParent());
			if (Files.exists(file) && Files.size(file) + bytes.length > maxBytes) {
				Files.move(file, rotated(), StandardCopyOption.REPLACE_EXISTING);
			}
			Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
		} catch (IOException | RuntimeException ignored) {
			// auditing must never break the game; the action result is still reported to the player
		}
	}
}
