package dev.t3craft.t3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The operate sessions ({@link Pairing#OPERATE_SCOPES}): {@code <config>/t3craft/credentials/operate/<env>.json}
 * ({@code {accessToken, scopes, expiresAt}}, owner-only permissions where the OS allows). Never in
 * {@code t3craft.json} nor in {@code environments.json}. <b>This file is the switch</b>: control is
 * enabled for an environment exactly while it holds a valid token here ({@link OperateGate}).
 * <ul>
 *   <li>Default pairing (read + operate in one): {@link Environments#pair} writes the same
 *   session here and in {@code credentials/<env>.json} ({@link #sharesReadSession}).</li>
 *   <li>"Enable control" on a read-only environment, or anything stored before 2026-11-07: a
 *   separate operate session here, the read-only one stays untouched (additive).</li>
 * </ul>
 *
 * <p>{@link #revoke} only deletes the local file. Revoking the session on the T3 host is a manual
 * step ({@code t3 auth session list} / {@code t3 auth session revoke <id>}, or Sharing &amp; access):
 * T3Craft never sends a write to T3 to revoke.
 */
public final class OperateCredentials {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Shown wherever the player revokes control: the host-side step is manual. */
	public static final String HOST_REVOKE_HINT = "also revoke the operate session on the T3 host: t3 auth session list / t3 auth session revoke <id>";

	/**
	 * Shown when control is revoked on a combined pairing: the read zone keeps the same session,
	 * which still carries operate on the host.
	 */
	public static final String SHARED_REVOKE_HINT = "This environment was paired with read + control in one session, which still reads "
			+ "and still carries operate on the T3 host: revoke it there (t3 auth session list / t3 auth session revoke <id>) "
			+ "and re-pair with /t3craft pair --read-only for a read-only session";

	private final Path dir;

	/** {@code t3craftConfigDir} is {@code config/t3craft/}, the same dir as {@link Environments}. */
	public OperateCredentials(Path t3craftConfigDir) {
		this.dir = t3craftConfigDir;
	}

	Path file(String envId) {
		return dir.resolve("credentials").resolve("operate").resolve(envId + ".json");
	}

	private static void checkId(String envId) {
		if (envId == null || !Environments.ID.matcher(envId).matches()) {
			throw new IllegalArgumentException("invalid environment id");
		}
	}

	/** Stores an operate session; refuses anything that is not exactly read + operate. */
	public synchronized void save(String envId, Pairing.Session session) throws IOException {
		checkId(envId);
		if (!Pairing.OPERATE_SCOPES.equals(session.scopes())) {
			throw new IllegalArgumentException("not an operate session: " + new TreeSet<>(session.scopes()));
		}
		JsonObject o = new JsonObject();
		o.addProperty("accessToken", session.accessToken());
		JsonArray scopes = new JsonArray();
		new TreeSet<>(session.scopes()).forEach(scopes::add);
		o.add("scopes", scopes);
		o.addProperty("expiresAt", Instant.ofEpochMilli(session.expiresAtMs()).toString());
		Environments.writeAtomically(file(envId), GSON.toJson(o), true);
	}

	/**
	 * The stored operate session, or null when missing, unreadable, expired at {@code nowMs}, or
	 * not exactly {@link Pairing#OPERATE_SCOPES}.
	 */
	public Pairing.@Nullable Session get(String envId, long nowMs) {
		Pairing.Session s = stored(envId);
		if (s == null || (s.expiresAtMs() > 0 && s.expiresAtMs() <= nowMs)) {
			return null;
		}
		return s;
	}

	/** Like {@link #get} but also returns an expired session (to tell the player it expired). */
	public Pairing.@Nullable Session stored(String envId) {
		try {
			checkId(envId);
			Path f = file(envId);
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
			Set<String> scopes = new TreeSet<>();
			if (sc != null) {
				sc.forEach(x -> scopes.add(x.getAsString()));
			}
			if (token == null || token.isBlank() || !scopes.equals(Pairing.OPERATE_SCOPES)) {
				return null;
			}
			Long exp = Json.time(o, "expiresAt");
			return new Pairing.Session(token, Set.copyOf(scopes), exp == null ? 0 : exp);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * Whether the operate token is the same session as the environment's read-zone credential
	 * (default combined pairing). After {@link #revoke} that session still carries operate on the
	 * T3 host, though T3Craft no longer writes with it: revoke it there, or re-pair read-only.
	 */
	public boolean sharesReadSession(String envId) {
		Pairing.Session op = stored(envId);
		Pairing.Session read = new Environments(dir).credential(envId);
		return op != null && read != null && op.accessToken().equals(read.accessToken());
	}

	public boolean has(String envId, long nowMs) {
		return get(envId, nowMs) != null;
	}

	/**
	 * Deletes the local operate token: the environment goes back to read-only and keeps its read
	 * pairing (with a combined session the read zone keeps reading with it). Returns whether there
	 * was one. Never contacts T3.
	 */
	public synchronized boolean revoke(String envId) throws IOException {
		try {
			checkId(envId);
		} catch (IllegalArgumentException e) {
			return false;
		}
		return Files.deleteIfExists(file(envId));
	}
}
