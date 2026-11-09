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
 *   <li>{@code environments.json}: {@code [{id, label, url, urls?, environmentId?}]}, no secrets;
 *   {@code urls} are extra routes to the same environment (LAN, Tailscale, tunnel hostname).</li>
 *   <li>{@code credentials/<id>.json}: {@code {accessToken, scopes, expiresAt}}; owner-only
 *   permissions where the OS supports it. Lives in the player's instance profile, never in a repo.
 *   This is the <i>read</i> zone's token: {@code orchestration:read}, or the combined
 *   {@code orchestration:read + orchestration:operate} session of a default pairing.</li>
 * </ul>
 * Control (operate) is enabled only while {@link OperateCredentials} holds a token for the
 * environment ({@code credentials/operate/<id>.json}); {@link #pair} writes both files for a
 * combined session. See {@code docs/features/operate.md} "Almacenamiento".
 */
public final class Environments {
	public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * A paired environment. {@code url} is the primary address; {@code fallbacks} are other
	 * addresses of the same environment (LAN, Tailscale, a T3 Connect tunnel hostname), tried in
	 * order after it ({@link #routes()}). {@code environmentId} is the id the public descriptor
	 * reported when paired (null if unknown): a route that answers with another id is skipped.
	 * None of this is secret.
	 */
	public record Environment(String id, String label, URI url, List<URI> fallbacks, @Nullable String environmentId) {
		public Environment {
			List<URI> clean = new ArrayList<>();
			for (URI u : fallbacks) {
				if (!u.equals(url) && !clean.contains(u)) {
					clean.add(u);
				}
			}
			fallbacks = List.copyOf(clean);
		}

		public Environment(String id, String label, URI url) {
			this(id, label, url, List.of(), null);
		}

		/** Primary address first, then the fallbacks. */
		public List<URI> routes() {
			List<URI> all = new ArrayList<>();
			all.add(url);
			all.addAll(fallbacks);
			return List.copyOf(all);
		}

		public Environment withLabel(String newLabel) {
			return new Environment(id, newLabel, url, fallbacks, environmentId);
		}

		public Environment withEnvironmentId(@Nullable String newEnvironmentId) {
			return new Environment(id, label, url, fallbacks, newEnvironmentId);
		}

		/** Adds {@code route} as a fallback (no-op when it is already a route). */
		public Environment withRoute(URI route) {
			List<URI> next = new ArrayList<>(fallbacks);
			next.add(route);
			return new Environment(id, label, url, next, environmentId);
		}
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

	/** Lower-case slug from a label ("my-vps" → "my-vps", "Mini Laptop" → "mini-laptop"). */
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
					List<URI> fallbacks = new ArrayList<>();
					JsonArray extra = Json.arr(o, "urls");
					if (extra != null) {
						for (JsonElement u : extra) {
							try {
								fallbacks.add(Pairing.parseUrl(u.getAsString()));
							} catch (Pairing.PairingException | RuntimeException ignored) {
								// skip an invalid extra route, keep the environment
							}
						}
					}
					out.add(new Environment(id, java.util.Objects.requireNonNullElse(Json.str(o, "label"), id),
							Pairing.base(URI.create(url)), fallbacks, Json.str(o, "environmentId")));
				} catch (IllegalArgumentException | Pairing.PairingException ignored) {
					// skip invalid entry
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// unreadable file: behave as empty
		}
		return out;
	}

	/**
	 * Stores the result of a pairing: the environment plus its read-zone credential and, for a
	 * combined read + operate session, the same session as the operate token (control enabled).
	 * A read-only pairing deletes any previous operate token of the environment: re-pairing
	 * read-only means read-only. Returns whether control is enabled afterwards.
	 */
	public synchronized boolean pair(Environment env, Pairing.Session session) throws IOException {
		if (!Pairing.READ_SCOPES.equals(session.scopes()) && !Pairing.OPERATE_SCOPES.equals(session.scopes())) {
			throw new IllegalArgumentException("not a pairing session: " + new java.util.TreeSet<>(session.scopes()));
		}
		save(env, session);
		OperateCredentials operate = new OperateCredentials(dir);
		if (Pairing.grantsOperate(session.scopes())) {
			operate.save(env.id(), session);
			return true;
		}
		operate.revoke(env.id());
		return false;
	}

	/**
	 * Adds or replaces an environment and its read-zone credential. Never touches the operate
	 * token (rename re-saves the same session through here).
	 */
	public synchronized void save(Environment env, Pairing.Session session) throws IOException {
		List<Environment> all = new ArrayList<>(list());
		all.removeIf(e -> e.id().equals(env.id()));
		all.add(env);
		writeAtomically(envFile(), GSON.toJson(toJson(all)), false);
		JsonObject cred = new JsonObject();
		cred.addProperty("accessToken", session.accessToken());
		JsonArray scopes = new JsonArray();
		session.scopes().forEach(scopes::add);
		cred.add("scopes", scopes);
		cred.addProperty("expiresAt", java.time.Instant.ofEpochMilli(session.expiresAtMs()).toString());
		writeAtomically(credentialFile(env.id()), GSON.toJson(cred), true);
	}

	/**
	 * Removes the environment and deletes its local credentials, the read-only one and the
	 * optional operate one ({@link OperateCredentials}). Revoke the sessions on the host too.
	 */
	public synchronized boolean remove(String id) throws IOException {
		new OperateCredentials(dir).revoke(id);
		List<Environment> all = new ArrayList<>(list());
		boolean removed = all.removeIf(e -> e.id().equals(id));
		if (removed) {
			writeAtomically(envFile(), GSON.toJson(toJson(all)), false);
		}
		return Files.deleteIfExists(credentialFile(id)) || removed;
	}

	/**
	 * {@code [{id, label, url, urls?, environmentId?}]}: {@code urls} (the extra routes) and
	 * {@code environmentId} are written only when present, so older files stay byte-compatible.
	 */
	private static JsonArray toJson(List<Environment> all) {
		JsonArray arr = new JsonArray();
		for (Environment e : all) {
			JsonObject o = new JsonObject();
			o.addProperty("id", e.id());
			o.addProperty("label", e.label());
			o.addProperty("url", e.url().toString());
			if (!e.fallbacks().isEmpty()) {
				JsonArray urls = new JsonArray();
				e.fallbacks().forEach(u -> urls.add(u.toString()));
				o.add("urls", urls);
			}
			if (e.environmentId() != null) {
				o.addProperty("environmentId", e.environmentId());
			}
			arr.add(o);
		}
		return arr;
	}

	/**
	 * The stored read-zone session, or null when missing/invalid. Accepts exactly
	 * {@code orchestration:read} (read-only pairing, also every credential stored before
	 * 2026-11-07) or {@code orchestration:read + orchestration:operate} (default combined pairing);
	 * any other scope set (filesystem:write…) is ignored. The read connection only reads with it.
	 */
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
			if (token == null || token.isBlank() || !(scopes.equals(Pairing.READ_SCOPES) || scopes.equals(Pairing.OPERATE_SCOPES))) {
				return null;
			}
			Long exp = Json.time(o, "expiresAt");
			return new Pairing.Session(token, java.util.Set.copyOf(scopes), exp == null ? 0 : exp);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	static void writeAtomically(Path target, String content, boolean secret) throws IOException {
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
