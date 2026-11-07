package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The operate pairing always goes out with an explicit, minimal scope list. */
class OperatePairingTest {
	static Map<String, String> form(String body) {
		Map<String, String> m = new LinkedHashMap<>();
		for (String kv : body.split("&")) {
			int e = kv.indexOf('=');
			m.put(URLDecoder.decode(kv.substring(0, e), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(e + 1), StandardCharsets.UTF_8));
		}
		return m;
	}

	@Test
	void operateTokenRequestAsksExactlyReadAndOperate() {
		Map<String, String> f = form(Pairing.tokenRequestBody("cred", "T3Craft operate", "linux", Pairing.OPERATE_SCOPES));
		assertEquals("orchestration:operate orchestration:read", f.get("scope"));
		assertEquals(Set.of("orchestration:read", "orchestration:operate"), Set.of(f.get("scope").split(" ")));
		assertEquals(Pairing.GRANT_TYPE, f.get("grant_type"));
	}

	@Test
	void readTokenRequestStillAsksOnlyRead() {
		assertEquals("orchestration:read", form(Pairing.tokenRequestBody("cred", "T3Craft", "linux")).get("scope"));
	}

	@Test
	void tokenRequestNeverGoesOutWithoutExplicitScopes() {
		assertThrows(IllegalArgumentException.class, () -> Pairing.tokenRequestBody("c", "l", "os", Set.of()));
		assertThrows(IllegalArgumentException.class, () -> Pairing.tokenRequestBody("c", "l", "os", null));
		assertThrows(IllegalArgumentException.class, () -> Pairing.tokenRequestBody("c", "l", "os", Set.of("orchestration:operate")));
		assertThrows(IllegalArgumentException.class,
				() -> Pairing.tokenRequestBody("c", "l", "os", Set.of("orchestration:read", "orchestration:operate", "filesystem:write")));
	}

	@Test
	void hostCommandAlwaysCarriesExplicitScopes() {
		String cmd = Pairing.hostCommand(Pairing.OPERATE_SCOPES, "T3Craft operate", URI.create("https://vps.ts.net"));
		assertTrue(cmd.startsWith("t3 auth pairing create "), cmd);
		assertTrue(cmd.contains("--scope orchestration:read"), cmd);
		assertTrue(cmd.contains("--scope orchestration:operate"), cmd);
		assertEquals(2, cmd.split("--scope ", -1).length - 1, cmd);
		assertFalse(cmd.contains("filesystem") || cmd.contains("terminal") || cmd.contains("settings"), cmd);
		assertTrue(cmd.contains("--base-url https://vps.ts.net"));
		assertTrue(Pairing.hostCommand(Pairing.READ_SCOPES, null, null).contains("--scope orchestration:read"));
		assertThrows(IllegalArgumentException.class, () -> Pairing.hostCommand(Set.of(), null, null));
	}

	@Test
	void operateResponseMustGrantExactlyReadAndOperate() throws Exception {
		String ok = "{\"access_token\":\"op\",\"token_type\":\"Bearer\",\"expires_in\":60,\"scope\":\"orchestration:read orchestration:operate\"}";
		var s = Pairing.parseTokenResponse(ok, 1000, Pairing.OPERATE_SCOPES);
		assertEquals(Pairing.OPERATE_SCOPES, s.scopes());
		assertEquals(61_000, s.expiresAtMs());

		String onlyRead = "{\"access_token\":\"op\",\"token_type\":\"Bearer\",\"scope\":\"orchestration:read\"}";
		assertThrows(Pairing.PairingException.class, () -> Pairing.parseTokenResponse(onlyRead, 0, Pairing.OPERATE_SCOPES));
		String more = "{\"access_token\":\"op\",\"token_type\":\"Bearer\",\"scope\":\"orchestration:read orchestration:operate filesystem:write\"}";
		var ex = assertThrows(Pairing.PairingException.class, () -> Pairing.parseTokenResponse(more, 0, Pairing.OPERATE_SCOPES));
		assertFalse(ex.getMessage().contains("op\""));
		// the read pairing still refuses an operate grant
		assertThrows(Pairing.PairingException.class, () -> Pairing.parseTokenResponse(ok, 0));
	}

	@Test
	void invalidScopeErrorSuggestsTheScopedCommand() {
		String msg = Pairing.describeTokenError(400, "{\"error\":\"invalid_scope\"}", Pairing.OPERATE_SCOPES);
		assertTrue(msg.contains("--scope orchestration:operate") && msg.contains("--scope orchestration:read"), msg);
	}

	@Test
	void exchangeAgainstFakeSendsOperateScope() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			fake.tokenResponse = "{\"access_token\":\"op\",\"token_type\":\"Bearer\",\"expires_in\":60,\"scope\":\"orchestration:operate orchestration:read\"}";
			var target = Pairing.parse(fake.base() + "/pair#token=one-time", null);
			var s = new PairingClient(HttpClient.newHttpClient()).exchange(target, "T3Craft operate", "linux", Pairing.OPERATE_SCOPES)
					.get(10, TimeUnit.SECONDS);
			assertEquals(Pairing.OPERATE_SCOPES, s.scopes());
			assertTrue(fake.lastTokenForm.contains("scope=orchestration:operate orchestration:read"), fake.lastTokenForm);

			var bad = new PairingClient(HttpClient.newHttpClient()).exchange(target, "x", "linux", Set.of());
			var ex = assertThrows(ExecutionException.class, () -> bad.get(5, TimeUnit.SECONDS));
			assertInstanceOf(IllegalArgumentException.class, ex.getCause());
		}
	}
}
