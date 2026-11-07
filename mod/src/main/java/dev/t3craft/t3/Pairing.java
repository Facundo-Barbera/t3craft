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

	/** Form body for {@code POST /oauth/token}. Contains the secret credential. */
	public static String tokenRequestBody(String credential, String clientLabel, String clientOs) {
		Map<String, String> f = new LinkedHashMap<>();
		f.put("grant_type", GRANT_TYPE);
		f.put("subject_token", credential);
		f.put("subject_token_type", SUBJECT_TOKEN_TYPE);
		f.put("requested_token_type", REQUESTED_TOKEN_TYPE);
		f.put("scope", READ_SCOPE);
		f.put("client_label", clientLabel);
		f.put("client_device_type", "desktop");
		if (clientOs != null && !clientOs.isBlank()) {
			f.put("client_os", clientOs);
		}
		return f.entrySet().stream()
				.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
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
		if (!scopes.equals(Set.of(READ_SCOPE))) {
			throw new PairingException("Refusing the session: T3Craft only accepts " + READ_SCOPE + ", T3 granted " + scopes + ".");
		}
		long expiresIn = Json.num(o, "expires_in", 0);
		return new Session(token, Set.copyOf(scopes), nowMs + Math.max(0, expiresIn) * 1000L);
	}

	/** Maps an error response of {@code /oauth/token} to a message for the player (no secrets). */
	public static String describeTokenError(int status, String body) {
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
			return "That pairing credential does not include " + READ_SCOPE + ". Issue one with --scope " + READ_SCOPE + ".";
		}
		if (status == 400 || status == 401 || status == 403) {
			return "T3 rejected the pairing credential (" + status + (code == null ? "" : " " + code)
					+ "). It is one-time and expires in minutes: issue a new one.";
		}
		return "Pairing failed: HTTP " + status + (code == null ? "" : " " + code) + ".";
	}
}
