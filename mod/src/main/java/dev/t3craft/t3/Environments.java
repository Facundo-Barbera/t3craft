package dev.t3craft.t3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The T3 environments this Minecraft instance is paired with, under {@code <config>/t3craft/}:
 * <ul>
 *   <li>{@code environments.json}: {@code [{id, label, url}]}, no secrets.</li>
 *   <li>{@code credentials/<id>.json}: {@code {accessToken, scopes, expiresAt}}; owner-only
 *   permissions where the OS supports it. Lives in the player's instance profile, never in a repo.</li>
 * </ul>
 */
public final class Environments {
	public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public record Environment(String id, String label, URI url) {
	}

	private final Path dir;

	public Environments(Path t3craftConfigDir) {
		this.dir = t3craftConfigDir;
	}

	public Path dir() {
		return dir;
	}

	Path envFile() {
		return dir.resolve("environments.json");
	}

	Path credentialFile(String id) {
		return dir.resolve("credentials").resolve(id + ".json");
	}

	/** Lower-case slug from a label ("my-vps" → "my-vps", "Mini Acmesite" → "mini-acmesite"). */
	public static String slug(String label) {
		String s = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (s.isEmpty()) {
			s = "env";
		}
		return s.length() > 32 ? s.substring(0, 32).replaceAll("-+$", "") : s;
	}

	public List<Environment> list() {
		List<Environment> out = new ArrayList<>();
		try {
			if (!Files.exists(envFile())) {
				return out;
			}
			JsonElement el = JsonParser.parseString(Files.readString(envFile(), StandardCharsets.UTF_8));
			if (!el.isJsonArray()) {
				return out;
			}
			for (JsonElement e : el.getAsJsonArray()) {
				if (!e.isJsonObject()) {
					continue;
				}
				JsonObject o = e.getAsJsonObject();
				String id = Json.str(o, "id");
				String url = Json.str(o, "url");
				if (id == null || !ID.matcher(id).matches() || url == null) {
					continue;
				}
				try {
					out.add(new Environment(id, java.util.Objects.requireNonNullElse(Json.str(o, "label"), id), Pairing.base(URI.create(url))));
				} catch (IllegalArgumentException | Pairing.PairingException ignored) {
					// skip invalid entry
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// unreadable file: behave as empty
		}
		return out;
	}

	/** Adds or replaces an environment and its credential. */
	public synchronized void save(Environment env, Pairing.Session session) throws IOException {
		List<Environment> all = new ArrayList<>(list());
		all.removeIf(e -> e.id().equals(env.id()));
		all.add(env);
		JsonArray arr = new JsonArray();
		for (Environment e : all) {
			JsonObject o = new JsonObject();
			o.addProperty("id", e.id());
			o.addProperty("label", e.label());
			o.addProperty("url", e.url().toString());
			arr.add(o);
		}
		writeAtomically(envFile(), GSON.toJson(arr), false);
		JsonObject cred = new JsonObject();
		cred.addProperty("accessToken", session.accessToken());
		JsonArray scopes = new JsonArray();
		session.scopes().forEach(scopes::add);
		cred.add("scopes", scopes);
		cred.addProperty("expiresAt", java.time.Instant.ofEpochMilli(session.expiresAtMs()).toString());
		writeAtomically(credentialFile(env.id()), GSON.toJson(cred), true);
	}

	/** Removes the environment and deletes its local credential (revoke the session on the host too). */
	public synchronized boolean remove(String id) throws IOException {
		List<Environment> all = new ArrayList<>(list());
		boolean removed = all.removeIf(e -> e.id().equals(id));
		JsonArray arr = new JsonArray();
		for (Environment e : all) {
			JsonObject o = new JsonObject();
			o.addProperty("id", e.id());
			o.addProperty("label", e.label());
			o.addProperty("url", e.url().toString());
			arr.add(o);
		}
		if (removed) {
			writeAtomically(envFile(), GSON.toJson(arr), false);
		}
		return Files.deleteIfExists(credentialFile(id)) || removed;
	}

	/** The stored read-only session, or null when missing/invalid/not read-only. */
	public Pairing.@Nullable Session credential(String id) {
		try {
			Path f = credentialFile(id);
			if (!Files.exists(f)) {
				return null;
			}
			JsonElement el = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			if (!el.isJsonObject()) {
				return null;
			}
			JsonObject o = el.getAsJsonObject();
			String token = Json.str(o, "accessToken");
			JsonArray sc = Json.arr(o, "scopes");
			java.util.Set<String> scopes = new java.util.TreeSet<>();
			if (sc != null) {
				sc.forEach(s -> scopes.add(s.getAsString()));
			}
			if (token == null || token.isBlank() || !scopes.equals(java.util.Set.of(Pairing.READ_SCOPE))) {
				return null;
			}
			Long exp = Json.time(o, "expiresAt");
			return new Pairing.Session(token, java.util.Set.copyOf(scopes), exp == null ? 0 : exp);
		} catch (IOException | RuntimeException e) {
			return null;
		}
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
