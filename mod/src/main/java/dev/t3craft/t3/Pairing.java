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
 * Direct pairing with a T3 environment. Mirrors T3's own remote clients
 * (client-runtime authorization/remote.ts {@code bootstrapRemoteBearerSession}):
 * <ol>
 *   <li>The host issues a one-time pairing credential with explicit scopes. By default
 *   ({@link #DEFAULT_SCOPES}) read + operate in one pairing:
 *   {@code t3 auth pairing create --scope orchestration:operate --scope orchestration:read};
 *   with "read-only" only {@code --scope orchestration:read}. Never without {@code --scope}
 *   (that grants every scope). Link: {@code https://host/pair#token=…}.</li>
 *   <li>The mod POSTs {@code /oauth/token} (OAuth token exchange, form-urlencoded) asking for
 *   exactly those scopes. T3 grants requested ∩ credential scopes.</li>
 *   <li>The response must grant {@code orchestration:read}, optionally plus
 *   {@code orchestration:operate} if it was asked for, and nothing else: any other scope
 *   (filesystem:write, terminal:operate…) is rejected and nothing is stored. Bearer (no DPoP):
 *   30-day session, no refresh token.</li>
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
	/**
	 * What {@code /t3craft pair} and the pairing screen ask for unless "read-only" is chosen:
	 * read + operate together, in a single pairing (decision of 2026-11-07).
	 */
	public static final Set<String> DEFAULT_SCOPES = OPERATE_SCOPES;
	public static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
	public static final String SUBJECT_TOKEN_TYPE = "urn:t3:params:oauth:token-type:environment-bootstrap";
	public static final String REQUESTED_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

	private Pairing() {
	}

	public static class PairingException extends Exception {
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
			throw new PairingException("With a bare code, also enter the T3 URL (e.g. https://host.ts.net) or the T3 Connect tunnel hostname.");
		}
		return new Target(parseUrl(hostUrl), in);
	}

	/**
	 * Like {@link #parse} but a non-blank {@code hostUrl} wins over the host of a full link. For the
	 * pairing screen: {@code t3 auth pairing create} prints a link for the address the host knows
	 * (often {@code localhost}), while the player reaches it through a tunnel hostname or Tailscale
	 * name typed in the URL box. The credential goes only to the host the player typed.
	 */
	public static Target parseOverridingHost(String input, @Nullable String hostUrl) throws PairingException {
		Target t = parse(input, hostUrl);
		if (hostUrl == null || hostUrl.isBlank()) {
			return t;
		}
		return new Target(parseUrl(hostUrl), t.credential());
	}

	/**
	 * Turns what the player typed into an environment base URL (scheme, host, port; no path):
	 * {@code https://host/mcp} (what "Copy MCP URL" gives), {@code https://host/pair#token=…} and a bare
	 * hostname such as a T3 Connect tunnel ({@code name.example.com}, taken as {@code https://}; type
	 * {@code http://} for a plain LAN address). The host is lower-cased. Never contains a secret: a
	 * fragment or query is dropped.
	 */
	public static URI parseUrl(@Nullable String input) throws PairingException {
		String in = input == null ? "" : input.trim();
		if (in.isEmpty()) {
			throw new PairingException("Enter the T3 URL or the T3 Connect tunnel hostname.");
		}
		if (in.chars().anyMatch(Character::isWhitespace)) {
			throw new PairingException("The T3 URL has spaces.");
		}
		if (!in.contains("://")) {
			in = "https://" + in;
		}
		try {
			return base(URI.create(in));
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
		return URI.create(scheme + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + (uri.getPort() == -1 ? "" : ":" + uri.getPort()));
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

	/** The scopes a pairing asks for: {@link #DEFAULT_SCOPES} (read + operate), or only read. */
	public static Set<String> pairingScopes(boolean readOnly) {
		return readOnly ? READ_SCOPES : DEFAULT_SCOPES;
	}

	/**
	 * The host command the pairing screen shows: {@link #hostCommand} for
	 * {@link #pairingScopes(boolean)}, so always with explicit {@code --scope}s.
	 */
	public static String pairingHostCommand(boolean readOnly, @Nullable URI baseUrl) {
		return hostCommand(pairingScopes(readOnly), null, baseUrl);
	}

	/** Whether {@code scopes} carries {@code orchestration:operate} (control). */
	public static boolean grantsOperate(Set<String> scopes) {
		return scopes != null && scopes.contains(OPERATE_SCOPE);
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
		// read first, then operate: "--scope orchestration:read --scope orchestration:operate".
		b.append(" --scope ").append(READ_SCOPE);
		if (scopes.contains(OPERATE_SCOPE)) {
			b.append(" --scope ").append(OPERATE_SCOPE);
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

	/**
	 * A pairing session: {@link #READ_SCOPES} or {@link #OPERATE_SCOPES}. {@code accessToken} is a
	 * secret: never log it.
	 */
	public record Session(String accessToken, Set<String> scopes, long expiresAtMs) {
		@Override
		public String toString() {
			return "Session[scopes=" + scopes + ", expiresAtMs=" + expiresAtMs + ", accessToken=<redacted>]";
		}
	}

	/** Validates a {@code /oauth/token} 200 response of a read-only pairing: Bearer, exactly {@code orchestration:read}. */
	public static Session parseTokenResponse(String body, long nowMs) throws PairingException {
		return parseTokenResponse(body, nowMs, READ_SCOPES);
	}

	/**
	 * Validates a {@code /oauth/token} 200 response of a pairing that asked for {@code requested}
	 * ({@link #READ_SCOPES} or {@link #OPERATE_SCOPES}). Bearer only. The grant must be
	 * {@code orchestration:read} or {@code orchestration:read + orchestration:operate}, within
	 * what was asked:
	 * <ul>
	 *   <li>any scope other than those two (filesystem:write, terminal:operate…) is refused;</li>
	 *   <li>operate granted to a read-only request is refused;</li>
	 *   <li>read only, when read + operate was asked, is accepted (T3 grants requested ∩
	 *   credential: the host issued a read-only credential). Callers that need control check
	 *   {@link #requireOperate}.</li>
	 * </ul>
	 */
	public static Session parseTokenResponse(String body, long nowMs, Set<String> requested) throws PairingException {
		scopeParam(requested);
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
		Set<String> scopes = scope == null || scope.isBlank() ? Set.of() : new TreeSet<>(Arrays.asList(scope.trim().split("\\s+")));
		checkGranted(scopes, requested);
		long expiresIn = Json.num(o, "expires_in", 0);
		return new Session(token, Set.copyOf(scopes), nowMs + Math.max(0, expiresIn) * 1000L);
	}

	/** The rules of {@link #parseTokenResponse} on the granted scope set. Messages carry no secret. */
	static void checkGranted(Set<String> granted, Set<String> requested) throws PairingException {
		Set<String> extra = new TreeSet<>(granted);
		extra.removeAll(OPERATE_SCOPES);
		if (!extra.isEmpty()) {
			throw new PairingException("Refusing the session: T3 granted " + String.join(", ", extra)
					+ ", which T3Craft never uses (it only accepts orchestration:read, optionally + orchestration:operate)."
					+ " The credential was probably issued without --scope. Revoke that session on the host and issue one with: "
					+ hostCommand(requested, null, null));
		}
		if (!granted.contains(READ_SCOPE)) {
			throw new PairingException("Refusing the session: T3 did not grant orchestration:read (granted " + new TreeSet<>(granted) + ").");
		}
		if (!requested.containsAll(granted)) {
			throw new PairingException("Refusing the session: a read-only pairing was asked for, but T3 granted "
					+ String.join(" + ", new TreeSet<>(granted)) + ". T3Craft only accepts orchestration:read here.");
		}
	}

	/**
	 * For "Enable control": the session must carry operate. A read-only grant means the host
	 * issued the credential without {@code --scope orchestration:operate}.
	 */
	public static void requireOperate(Session session) throws PairingException {
		if (!grantsOperate(session.scopes())) {
			throw new PairingException("That pairing credential does not include orchestration:operate. Issue one with: "
					+ hostCommand(OPERATE_SCOPES, null, null));
		}
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
