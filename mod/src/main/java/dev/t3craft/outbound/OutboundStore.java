package dev.t3craft.outbound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.t3.Json;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Disk side of the outbound events, under {@code <config>/t3craft/} (the same folder as
 * {@code environments.json}):
 * <ul>
 *   <li>{@code outbound.json}: {@link OutboundConfig}, visible settings, <b>no secrets</b>.</li>
 *   <li>{@code credentials/outbound/<destination id>.json}: {@code {"url": "..."}}, the webhook URL.
 *   A secret like the environment tokens in {@code credentials/<env>.json}: owner-only permissions where
 *   the OS supports it, never in {@code outbound.json}, never logged, never shown by a screen.</li>
 * </ul>
 * {@link #load} caches by file time and size, so the emitter can call it for every event and still see an
 * edit made by the settings screen (or by hand) without a restart. A broken file reads as "off".
 */
public final class OutboundStore {
	public static final int MAX_URL_LENGTH = 2048;

	private final Path dir;
	private long cachedMtime = Long.MIN_VALUE;
	private long cachedSize = -1;
	private OutboundConfig cached = OutboundConfig.OFF;

	/** {@code t3craftConfigDir} is {@code config/t3craft/}. */
	public OutboundStore(Path t3craftConfigDir) {
		this.dir = t3craftConfigDir;
	}

	public Path file() {
		return dir.resolve("outbound.json");
	}

	Path secretFile(String destinationId) {
		if (!Destination.ID.matcher(destinationId).matches()) {
			throw new IllegalArgumentException("invalid destination id");
		}
		return dir.resolve("credentials").resolve("outbound").resolve(destinationId + ".json");
	}

	/** The current settings; {@link OutboundConfig#OFF} when the file is missing or unreadable. */
	public synchronized OutboundConfig load() {
		Path f = file();
		try {
			if (!Files.exists(f)) {
				cachedMtime = Long.MIN_VALUE;
				cachedSize = -1;
				cached = OutboundConfig.OFF;
				return cached;
			}
			long mtime = Files.getLastModifiedTime(f).toMillis();
			long size = Files.size(f);
			if (mtime == cachedMtime && size == cachedSize) {
				return cached;
			}
			OutboundConfig cfg = OutboundConfig.fromJson(Files.readString(f, StandardCharsets.UTF_8));
			cachedMtime = mtime;
			cachedSize = size;
			cached = cfg;
			return cfg;
		} catch (IOException | RuntimeException e) {
			// Unreadable or malformed: fail closed.
			cachedMtime = Long.MIN_VALUE;
			cachedSize = -1;
			cached = OutboundConfig.OFF;
			return cached;
		}
	}

	public synchronized void save(OutboundConfig config) throws IOException {
		writeAtomically(file(), config.toJson(), false);
		Path f = file();
		cachedMtime = Files.getLastModifiedTime(f).toMillis();
		cachedSize = Files.size(f);
		cached = config;
	}

	/**
	 * Why {@code url} cannot be a webhook URL, or null when it is fine. The text never repeats the URL.
	 * Plain {@code http} is allowed (a Home Assistant on the LAN has no certificate) but the data then
	 * travels unencrypted; the docs say so.
	 */
	public static @Nullable String validate(@Nullable String url) {
		if (url == null || url.isBlank()) {
			return "The webhook URL is empty";
		}
		String s = url.strip();
		if (s.length() > MAX_URL_LENGTH) {
			return "The webhook URL is too long";
		}
		URI u;
		try {
			u = new URI(s);
		} catch (URISyntaxException e) {
			return "That is not a valid URL";
		}
		String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("https") && !scheme.equals("http")) {
			return "The webhook URL must start with https:// (or http://)";
		}
		if (u.getHost() == null || u.getHost().isBlank()) {
			return "The webhook URL has no host";
		}
		return null;
	}

	/** Stores the URL of a destination as a credential. Throws {@link IllegalArgumentException} when invalid. */
	public synchronized void setUrl(String destinationId, String url) throws IOException {
		String why = validate(url);
		if (why != null) {
			throw new IllegalArgumentException(why);
		}
		JsonObject o = new JsonObject();
		o.addProperty("url", url.strip());
		writeAtomically(secretFile(destinationId), o.toString(), true);
	}

	/** The destination's webhook URL, or null when none is stored or it is unusable. */
	public @Nullable URI url(String destinationId) {
		try {
			Path f = secretFile(destinationId);
			if (!Files.exists(f)) {
				return null;
			}
			JsonElement el = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			if (!el.isJsonObject()) {
				return null;
			}
			String url = Json.str(el.getAsJsonObject(), "url");
			if (validate(url) != null) {
				return null;
			}
			return new URI(url.strip());
		} catch (IOException | URISyntaxException | RuntimeException e) {
			return null;
		}
	}

	public boolean hasUrl(String destinationId) {
		return url(destinationId) != null;
	}

	/** Deletes the stored URL; returns whether there was one. */
	public synchronized boolean deleteUrl(String destinationId) throws IOException {
		return Files.deleteIfExists(secretFile(destinationId));
	}

	private static void writeAtomically(Path target, String content, boolean secret) throws IOException {
		Files.createDirectories(target.getParent());
		Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			ownerOnly(tmp, secret);
			Files.writeString(tmp, content, StandardCharsets.UTF_8);
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			ownerOnly(target, secret);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private static void ownerOnly(Path p, boolean secret) {
		if (!secret) {
			return;
		}
		try {
			Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------"));
		} catch (UnsupportedOperationException | IOException ignored) {
			// Windows: the file lives in the user's own profile (inherited ACL).
		}
	}
}
