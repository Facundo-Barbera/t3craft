package dev.t3craft.t3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Direct pairing with a T3 environment, read-only. Mirrors T3's own remote clients
 * (client-runtime authorization/remote.ts {@code bootstrapRemoteBearerSession}):
 * <ol>
 *   <li>The host issues a one-time pairing credential:
 *   {@code t3 auth pairing create --scope orchestration:read} (link {@code https://host/pair#token=…}).</li>
 *   <li>The mod POSTs {@code /oauth/token} (OAuth token exchange, form-urlencoded) asking for
 *   {@code scope=orchestration:read} only. T3 grants requested ∩ credential scopes.</li>
 *   <li>The response must grant exactly {@code orchestration:read}; anything else is rejected
 *   and nothing is stored. Bearer (no DPoP): 30-day session, no refresh token.</li>
 * </ol>
 */
public final class Pairing {
	public static final String READ_SCOPE = "orchestration:read";
	public static final String OPERATE_SCOPE = "orchestration:operate";
	/** The read-only pairing: exactly {@code orchestration:read}. */
	public static final Set<String> READ_SCOPES = Set.of(READ_SCOPE);
	/**
	 * The optional, separate operate pairing: exactly {@code orchestration:read} +
	 * {@code orchestration:operate}. Never more (no filesystem/terminal/settings scopes).
	 */
	public static final Set<String> OPERATE_SCOPES = Set.of(READ_SCOPE, OPERATE_SCOPE);
	public static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
	public static final String SUBJECT_TOKEN_TYPE = "urn:t3:params:oauth:token-type:environment-bootstrap";
	public static final String REQUESTED_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

	private Pairing() {
	}

	public static final class PairingException extends Exception {
		public PairingException(String message) {
			super(message);
		}
	}

	/** Where to pair and with what credential. {@code credential} is a secret: never log it. */
	public record Target(URI baseUrl, String credential) {
		@Override
		public String toString() {
			return "Target[" + baseUrl + ", credential=<redacted>]";
		}
	}

	/**
	 * Accepts a full link ({@code https://host[:port]/pair#token=XYZ}, also {@code ?token=}) or a bare
	 * code plus a separate host URL. The base URL keeps scheme, host and port only.
	 */
	public static Target parse(String input, @Nullable String hostUrl) throws PairingException {
		String in = input == null ? "" : input.trim();
		if (in.isEmpty()) {
			throw new PairingException("Paste the pairing link or code.");
		}
		if (in.startsWith("http://") || in.startsWith("https://")) {
			URI uri;
			try {
				uri = URI.create(in);
			} catch (IllegalArgumentException e) {
				throw new PairingException("That link is not a valid URL.");
			}
			String token = param(uri.getRawFragment(), "token");
			if (token == null) {
				token = param(uri.getRawQuery(), "token");
			}
			if (token == null || token.isBlank()) {
				throw new PairingException("The link has no #token=… part. Copy the whole pairing link.");
			}
			return new Target(base(uri), token.trim());
		}
		if (in.contains(" ") || in.contains("\n")) {
			throw new PairingException("A pairing code has no spaces.");
		}
		if (hostUrl == null || hostUrl.isBlank()) {
			throw new PairingException("With a bare code, also enter the T3 URL (e.g. https://host.ts.net).");
		}
		try {
			return new Target(base(URI.create(hostUrl.trim())), in);
		} catch (IllegalArgumentException e) {
			throw new PairingException("The T3 URL is not valid.");
		}
	}

	static URI base(URI uri) throws PairingException {
		String scheme = uri.getScheme();
		if (!"https".equals(scheme) && !"http".equals(scheme)) {
			throw new PairingException("The T3 URL must start with https:// or http://");
		}
		if (uri.getHost() == null || uri.getRawUserInfo() != null) {
			throw new PairingException("The T3 URL must be a plain host (no user info).");
		}
		return URI.create(scheme + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort()));
	}

	private static @Nullable String param(@Nullable String raw, String key) {
		if (raw == null) {
			return null;
		}
		for (String part : raw.split("&")) {
			int eq = part.indexOf('=');
			if (eq > 0 && part.substring(0, eq).equals(key)) {
				return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
			}
		}
		return null;
	}

	public static URI tokenEndpoint(URI baseUrl) {
		return baseUrl.resolve("/oauth/token");
	}

	/** Form body for {@code POST /oauth/token} (read-only pairing). Contains the secret credential. */
	public static String tokenRequestBody(String credential, String clientLabel, String clientOs) {
		return tokenRequestBody(credential, clientLabel, clientOs, READ_SCOPES);
	}

	/**
	 * Form body for {@code POST /oauth/token} asking for exactly {@code scopes}, which must be
	 * {@link #READ_SCOPES} or {@link #OPERATE_SCOPES}: the request never goes out without an
	 * explicit scope list. Contains the secret credential.
	 */
	public static String tokenRequestBody(String credential, String clientLabel, String clientOs, Set<String> scopes) {
		String scope = scopeParam(scopes);
		Map<String, String> f = new LinkedHashMap<>();
		f.put("grant_type", GRANT_TYPE);
		f.put("subject_token", credential);
		f.put("subject_token_type", SUBJECT_TOKEN_TYPE);
		f.put("requested_token_type", REQUESTED_TOKEN_TYPE);
		f.put("scope", scope);
		f.put("client_label", clientLabel);
		f.put("client_device_type", "desktop");
		if (clientOs != null && !clientOs.isBlank()) {
			f.put("client_os", clientOs);
		}
		return f.entrySet().stream()
				.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
	}

	/** Space-separated, sorted scope list; only the two allowed sets are accepted. */
	static String scopeParam(Set<String> scopes) {
		if (scopes == null || !(scopes.equals(READ_SCOPES) || scopes.equals(OPERATE_SCOPES))) {
			throw new IllegalArgumentException("T3Craft only requests " + READ_SCOPES + " or " + OPERATE_SCOPES + ", not " + scopes);
		}
		return String.join(" ", new TreeSet<>(scopes));
	}

	/**
	 * The command the player runs on the T3 host to issue a pairing credential for {@code scopes}.
	 * Always carries one {@code --scope} per scope: {@code t3 auth pairing create} without
	 * {@code --scope} would grant every standard scope (filesystem:write, terminal:operate…).
	 */
	public static String hostCommand(Set<String> scopes, @Nullable String label, @Nullable URI baseUrl) {
		scopeParam(scopes); // validates the set
		StringBuilder b = new StringBuilder("t3 auth pairing create");
		for (String s : new TreeSet<>(scopes)) {
			b.append(" --scope ").append(s);
		}
		b.append(" --ttl 5m");
		if (label != null && !label.isBlank()) {
			b.append(" --label \"").append(label.replace("\"", "")).append('"');
		}
		if (baseUrl != null) {
			b.append(" --base-url ").append(baseUrl);
		}
		return b.toString();
	}

	/** A read-only session. {@code accessToken} is a secret: never log it. */
	public record Session(String accessToken, Set<String> scopes, long expiresAtMs) {
		@Override
		public String toString() {
			return "Session[scopes=" + scopes + ", expiresAtMs=" + expiresAtMs + ", accessToken=<redacted>]";
		}
	}

	/** Validates a {@code /oauth/token} 200 response: Bearer, exactly {@code orchestration:read}. */
	public static Session parseTokenResponse(String body, long nowMs) throws PairingException {
		return parseTokenResponse(body, nowMs, READ_SCOPES);
	}

	/** Validates a {@code /oauth/token} 200 response: Bearer, granting exactly {@code expected}. */
	public static Session parseTokenResponse(String body, long nowMs, Set<String> expected) throws PairingException {
		scopeParam(expected);
		JsonObject o;
		try {
			JsonElement el = JsonParser.parseString(body);
			o = el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			o = null;
		}
		if (o == null) {
			throw new PairingException("T3 sent an unexpected token response.");
		}
		String token = Json.str(o, "access_token");
		String type = Json.str(o, "token_type");
		String scope = Json.str(o, "scope");
		if (token == null || token.isBlank()) {
			throw new PairingException("T3 sent no access token.");
		}
		if (!"Bearer".equals(type)) {
			throw new PairingException("T3 issued a " + type + " token; T3Craft only supports Bearer sessions.");
		}
		Set<String> scopes = scope == null ? Set.of() : new TreeSet<>(Arrays.asList(scope.trim().split("\\s+")));
		if (!scopes.equals(expected)) {
			throw new PairingException("Refusing the session: T3Craft only accepts " + String.join(" + ", new TreeSet<>(expected)) + ", T3 granted " + scopes + ".");
		}
		long expiresIn = Json.num(o, "expires_in", 0);
		return new Session(token, Set.copyOf(scopes), nowMs + Math.max(0, expiresIn) * 1000L);
	}

	/** Maps an error response of {@code /oauth/token} to a message for the player (no secrets). */
	public static String describeTokenError(int status, String body) {
		return describeTokenError(status, body, READ_SCOPES);
	}

	/** Maps an error response of {@code /oauth/token} for a pairing that asked for {@code scopes}. */
	public static String describeTokenError(int status, String body, Set<String> scopes) {
		String code = null;
		try {
			JsonElement el = JsonParser.parseString(body);
			if (el.isJsonObject()) {
				code = Json.str(el.getAsJsonObject(), "error");
				if (code == null) {
					code = Json.str(el.getAsJsonObject(), "code");
				}
			}
		} catch (RuntimeException ignored) {
			// not JSON
		}
		if ("invalid_scope".equals(code)) {
			return "That pairing credential does not include " + String.join(" + ", new TreeSet<>(scopes)) + ". Issue one with: "
					+ hostCommand(scopes, null, null);
		}
		if (status == 400 || status == 401 || status == 403) {
			return "T3 rejected the pairing credential (" + status + (code == null ? "" : " " + code)
					+ "). It is one-time and expires in minutes: issue a new one.";
		}
		return "Pairing failed: HTTP " + status + (code == null ? "" : " " + code) + ".";
	}
}
