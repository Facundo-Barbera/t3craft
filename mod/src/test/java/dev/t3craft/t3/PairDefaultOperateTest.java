package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Default pairing = read + operate in one session (decision 2026-11-07), "--read-only" = read only,
 * extra scopes refused, existing credentials keep working, read-only → control upgrade, revoke.
 * Fakes only: never a real T3.
 */
class PairDefaultOperateTest {
	static final long NOW = 5_000_000;
	static final Environments.Environment VPS = new Environments.Environment("vps", "my-vps", URI.create("https://vps.ts.net"));

	static String grant(String scope) {
		return "{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":60,\"scope\":\"" + scope + "\"}";
	}

	static OperateGate gate(Path dir, OperateGateTest.FakeTransport t) {
		return new OperateGate(new OperateCredentials(dir), env -> t, AuditLog.in(dir), () -> NOW);
	}

	// ---- scopes asked for -------------------------------------------------------------------

	@Test
	void defaultPairingAsksReadAndOperateTogether() {
		assertEquals(Set.of("orchestration:read", "orchestration:operate"), Pairing.DEFAULT_SCOPES);
		assertEquals(Pairing.DEFAULT_SCOPES, Pairing.pairingScopes(false));
		Map<String, String> f = OperatePairingTest.form(Pairing.tokenRequestBody("cred", "T3Craft", "linux", Pairing.pairingScopes(false)));
		assertEquals("orchestration:operate orchestration:read", f.get("scope"));
	}

	@Test
	void readOnlyPairingAsksOnlyRead() {
		assertEquals(Pairing.READ_SCOPES, Pairing.pairingScopes(true));
		Map<String, String> f = OperatePairingTest.form(Pairing.tokenRequestBody("cred", "T3Craft", "linux", Pairing.pairingScopes(true)));
		assertEquals("orchestration:read", f.get("scope"));
		assertFalse(Pairing.pairingHostCommand(true, null).contains("operate"));
	}

	@Test
	void hostCommandIsNeverWithoutScope() {
		assertEquals("t3 auth pairing create --scope orchestration:read --scope orchestration:operate --ttl 5m",
				Pairing.pairingHostCommand(false, null));
		assertEquals("t3 auth pairing create --scope orchestration:read --ttl 5m", Pairing.pairingHostCommand(true, null));
		for (boolean ro : new boolean[] {false, true}) {
			String cmd = Pairing.pairingHostCommand(ro, URI.create("https://vps.ts.net"));
			assertTrue(cmd.contains(" --scope orchestration:read"), cmd);
			assertEquals(ro ? 1 : 2, cmd.split("--scope ", -1).length - 1, cmd);
			assertFalse(cmd.matches(".*(filesystem|terminal|settings|source-control).*"), cmd);
			assertTrue(cmd.endsWith("--base-url https://vps.ts.net"), cmd);
			String hint = Pairing.describeTokenError(400, "{\"error\":\"invalid_scope\"}", Pairing.pairingScopes(ro));
			assertTrue(hint.contains("t3 auth pairing create --scope orchestration:read"), hint);
		}
		// only the two allowed sets ever make it into a command or a request
		for (Set<String> bad : List.of(Set.<String>of(), Set.of("orchestration:operate"),
				Set.of("orchestration:read", "orchestration:operate", "terminal:operate"))) {
			assertThrows(IllegalArgumentException.class, () -> Pairing.hostCommand(bad, null, null));
			assertThrows(IllegalArgumentException.class, () -> Pairing.tokenRequestBody("c", "l", "os", bad));
		}
	}

	// ---- what T3 grants -----------------------------------------------------------------------

	@Test
	void acceptsReadOrReadPlusOperate() throws Exception {
		assertEquals(Pairing.OPERATE_SCOPES, Pairing.parseTokenResponse(grant("orchestration:read orchestration:operate"), NOW, Pairing.DEFAULT_SCOPES).scopes());
		assertEquals(Pairing.READ_SCOPES, Pairing.parseTokenResponse(grant("orchestration:read"), NOW, Pairing.DEFAULT_SCOPES).scopes());
		assertEquals(Pairing.READ_SCOPES, Pairing.parseTokenResponse(grant("orchestration:read"), NOW, Pairing.READ_SCOPES).scopes());
	}

	@Test
	void refusesAnyExtraScopeWithAClearMessage() {
		for (String extra : List.of("filesystem:write", "terminal:operate", "settings:write")) {
			for (Set<String> req : List.of(Pairing.DEFAULT_SCOPES, Pairing.READ_SCOPES)) {
				var ex = assertThrows(Pairing.PairingException.class,
						() -> Pairing.parseTokenResponse(grant("orchestration:read orchestration:operate " + extra), NOW, req));
				assertTrue(ex.getMessage().contains(extra), ex.getMessage());
				assertTrue(ex.getMessage().contains("never uses"), ex.getMessage());
				assertTrue(ex.getMessage().contains("--scope orchestration:read"), ex.getMessage());
				assertFalse(ex.getMessage().contains("tok"), "no token in messages");
			}
		}
	}

	@Test
	void refusesOperateOnReadOnlyRequestAndGrantsWithoutRead() {
		var ex = assertThrows(Pairing.PairingException.class,
				() -> Pairing.parseTokenResponse(grant("orchestration:read orchestration:operate"), NOW, Pairing.READ_SCOPES));
		assertTrue(ex.getMessage().contains("read-only"), ex.getMessage());
		assertThrows(Pairing.PairingException.class, () -> Pairing.parseTokenResponse(grant("orchestration:operate"), NOW, Pairing.DEFAULT_SCOPES));
		assertThrows(Pairing.PairingException.class, () -> Pairing.parseTokenResponse(grant(""), NOW, Pairing.DEFAULT_SCOPES));
	}

	@Test
	void defaultExchangeAgainstFakeSendsBothScopes() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			fake.tokenResponse = grant("orchestration:operate orchestration:read");
			var target = Pairing.parse(fake.base() + "/pair#token=one-time", null);
			var s = new PairingClient(HttpClient.newHttpClient()).exchange(target, "T3Craft", "linux", Pairing.pairingScopes(false))
					.get(10, TimeUnit.SECONDS);
			assertEquals(Pairing.OPERATE_SCOPES, s.scopes());
			assertTrue(fake.lastTokenForm.contains("scope=orchestration:operate orchestration:read"), fake.lastTokenForm);

			fake.tokenResponse = grant("orchestration:read");
			var ro = new PairingClient(HttpClient.newHttpClient()).exchange(target, "T3Craft", "linux", Pairing.pairingScopes(true))
					.get(10, TimeUnit.SECONDS);
			assertEquals(Pairing.READ_SCOPES, ro.scopes());
			assertTrue(fake.lastTokenForm.contains("scope=orchestration:read&"), fake.lastTokenForm);
		}
	}

	// ---- storage -------------------------------------------------------------------------------

	@Test
	void combinedPairingStoresOneSessionForBothZones(@TempDir Path dir) throws Exception {
		Environments envs = new Environments(dir);
		assertTrue(envs.pair(VPS, new Pairing.Session("combined", Pairing.OPERATE_SCOPES, NOW + 60_000)));
		assertEquals("combined", envs.credential("vps").accessToken());
		OperateCredentials op = new OperateCredentials(dir);
		assertEquals("combined", op.get("vps", NOW).accessToken());
		assertTrue(op.sharesReadSession("vps"));
		assertEquals(OperateGate.Availability.ENABLED, gate(dir, new OperateGateTest.FakeTransport()).availability("vps"));
		assertFalse(Files.readString(envs.envFile()).contains("combined"));
		assertThrows(IllegalArgumentException.class,
				() -> envs.pair(VPS, new Pairing.Session("x", Set.of("orchestration:read", "filesystem:write"), 0)));
	}

	@Test
	void readOnlyPairingIsReadOnlyAndDropsOldControl(@TempDir Path dir) throws Exception {
		Environments envs = new Environments(dir);
		envs.pair(VPS, new Pairing.Session("combined", Pairing.OPERATE_SCOPES, NOW + 60_000));
		assertFalse(envs.pair(VPS, new Pairing.Session("read", Pairing.READ_SCOPES, NOW + 60_000)));
		assertEquals("read", envs.credential("vps").accessToken());
		assertNull(new OperateCredentials(dir).stored("vps"));
		assertEquals(OperateGate.Availability.READ_ONLY, gate(dir, new OperateGateTest.FakeTransport()).availability("vps"));
	}

	@Test
	void existingCredentialsKeepWorking(@TempDir Path dir) throws Exception {
		// Files as written before 2026-11-07: read-only credential + optional separate operate token.
		Files.createDirectories(dir.resolve("credentials/operate"));
		Files.writeString(dir.resolve("environments.json"), "[{\"id\":\"vps\",\"label\":\"my-vps\",\"url\":\"https://vps.ts.net\"},"
				+ "{\"id\":\"mini\",\"label\":\"mini\",\"url\":\"https://mini.ts.net\"}]");
		Files.writeString(dir.resolve("credentials/vps.json"),
				"{\"accessToken\":\"old-read\",\"scopes\":[\"orchestration:read\"],\"expiresAt\":\"2099-01-01T00:00:00Z\"}");
		Files.writeString(dir.resolve("credentials/operate/vps.json"),
				"{\"accessToken\":\"old-op\",\"scopes\":[\"orchestration:operate\",\"orchestration:read\"],\"expiresAt\":\"2099-01-01T00:00:00Z\"}");
		Files.writeString(dir.resolve("credentials/mini.json"),
				"{\"accessToken\":\"mini-read\",\"scopes\":[\"orchestration:read\"],\"expiresAt\":\"2099-01-01T00:00:00Z\"}");
		Environments envs = new Environments(dir);
		OperateCredentials op = new OperateCredentials(dir);
		assertEquals("old-read", envs.credential("vps").accessToken());
		assertEquals("old-op", op.get("vps", NOW).accessToken());
		assertFalse(op.sharesReadSession("vps"));
		assertEquals("mini-read", envs.credential("mini").accessToken());
		OperateGate g = gate(dir, new OperateGateTest.FakeTransport());
		assertEquals(OperateGate.Availability.ENABLED, g.availability("vps"));
		assertEquals(OperateGate.Availability.READ_ONLY, g.availability("mini"));
		// rename re-saves the read credential and never touches the operate token
		envs.save(new Environments.Environment("vps", "renamed", URI.create("https://vps.ts.net")), envs.credential("vps"));
		assertEquals("old-op", op.get("vps", NOW).accessToken());
		assertEquals("old-read", envs.credential("vps").accessToken());
	}

	@Test
	void upgradeReadOnlyToControlKeepsTheReadPairing(@TempDir Path dir) throws Exception {
		Environments envs = new Environments(dir);
		envs.pair(VPS, new Pairing.Session("read", Pairing.READ_SCOPES, NOW + 60_000));
		OperateGate g = gate(dir, new OperateGateTest.FakeTransport());
		assertFalse(g.canOperate("vps"));

		// "Enable control" with a credential issued read-only: refused, nothing changes.
		Pairing.Session readOnlyGrant = Pairing.parseTokenResponse(grant("orchestration:read"), NOW, Pairing.OPERATE_SCOPES);
		assertThrows(Pairing.PairingException.class, () -> Pairing.requireOperate(readOnlyGrant));
		assertFalse(g.canOperate("vps"));

		// With a proper read + operate credential: what OperateClient.enable stores.
		Pairing.Session up = Pairing.parseTokenResponse(grant("orchestration:read orchestration:operate"), NOW, Pairing.OPERATE_SCOPES);
		Pairing.requireOperate(up);
		new OperateCredentials(dir).save("vps", up);
		assertTrue(g.canOperate("vps"));
		assertEquals("read", envs.credential("vps").accessToken(), "the read pairing is kept");
		assertFalse(new OperateCredentials(dir).sharesReadSession("vps"));
	}

	@Test
	void revokeLeavesTheEnvironmentPairedReadOnly(@TempDir Path dir) throws Exception {
		Environments envs = new Environments(dir);
		envs.pair(VPS, new Pairing.Session("combined", Pairing.OPERATE_SCOPES, NOW + 60_000));
		OperateGateTest.FakeTransport t = new OperateGateTest.FakeTransport();
		OperateGate g = gate(dir, t);
		OperateCredentials op = new OperateCredentials(dir);
		assertTrue(op.sharesReadSession("vps"));

		assertTrue(op.revoke("vps"));
		assertFalse(g.canOperate("vps"));
		assertEquals(OperateGate.Availability.READ_ONLY, g.availability("vps"));
		assertEquals(1, envs.list().size(), "still paired");
		assertEquals("combined", envs.credential("vps").accessToken(), "the read zone keeps reading");
		assertFalse(op.sharesReadSession("vps"));
		assertFalse(op.revoke("vps"));

		// writes are refused (and audited) after the revoke, destructive confirmations still apply before
		OperateResult r = g.submit(OperateGateTest.exampleReply("vps", "hi"), OperateGate.Origin.COMMAND, false).get();
		assertEquals(OperateResult.Outcome.READ_ONLY, r.outcome());
		assertTrue(t.calls.isEmpty());
		assertTrue(Files.readString(dir.resolve("audit.log")).contains("read_only"));
		assertTrue(OperateCredentials.SHARED_REVOKE_HINT.contains("--read-only"));

		// unpair removes both files
		envs.pair(VPS, new Pairing.Session("combined2", Pairing.OPERATE_SCOPES, NOW + 60_000));
		assertTrue(envs.remove("vps"));
		assertNull(envs.credential("vps"));
		assertNull(op.stored("vps"));
	}

	@Test
	void destructiveActionsStillNeedConfirmationWithCombinedPairing(@TempDir Path dir) throws Exception {
		new Environments(dir).pair(VPS, new Pairing.Session("combined", Pairing.OPERATE_SCOPES, NOW + 60_000));
		OperateGateTest.FakeTransport t = new OperateGateTest.FakeTransport();
		OperateGate g = gate(dir, t);
		OperateResult r = g.submit(OperateGateTest.exampleSettle(g, "vps", "t-1"), OperateGate.Origin.UI, false).get();
		assertEquals(OperateResult.Outcome.NEEDS_CONFIRMATION, r.outcome());
		assertTrue(t.calls.isEmpty());
		assertEquals(OperateResult.Outcome.OK, g.submit(OperateGateTest.exampleSettle(g, "vps", "t-1"), OperateGate.Origin.UI, true).get().outcome());
		assertEquals(1, t.calls.size());
	}
}
