package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PairingTest {
	@Test
	void parsesFragmentLink() throws Exception {
		Pairing.Target t = Pairing.parse("https://my-vps.example.ts.net/pair#token=abc%2Bdef", null);
		assertEquals(URI.create("https://my-vps.example.ts.net"), t.baseUrl());
		assertEquals("abc+def", t.credential());
		assertFalse(t.toString().contains("abc"), "toString must not leak the credential");
	}

	@Test
	void parsesLinkWithPortAndQueryToken() throws Exception {
		Pairing.Target t = Pairing.parse("http://mini.example.ts.net:3773/pair?token=xyz", null);
		assertEquals(URI.create("http://mini.example.ts.net:3773"), t.baseUrl());
		assertEquals("xyz", t.credential());
	}

	@Test
	void bareCodeNeedsHost() throws Exception {
		assertThrows(Pairing.PairingException.class, () -> Pairing.parse("CODE123", null));
		Pairing.Target t = Pairing.parse(" CODE123 ", "https://host.ts.net/whatever?x=1");
		assertEquals(URI.create("https://host.ts.net"), t.baseUrl());
		assertEquals("CODE123", t.credential());
	}

	@Test
	void rejectsBadInput() {
		assertThrows(Pairing.PairingException.class, () -> Pairing.parse("", null));
		assertThrows(Pairing.PairingException.class, () -> Pairing.parse("https://host/pair", null));
		assertThrows(Pairing.PairingException.class, () -> Pairing.parse("ftp://host/pair#token=a", null));
		assertThrows(Pairing.PairingException.class, () -> Pairing.parse("https://u:p@host/pair#token=a", null));
	}

	@Test
	void tokenRequestAsksReadOnly() {
		String body = Pairing.tokenRequestBody("se cret", "T3Craft (Minecraft)", "windows");
		assertTrue(body.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange"));
		assertTrue(body.contains("subject_token_type=urn%3At3%3Aparams%3Aoauth%3Atoken-type%3Aenvironment-bootstrap"));
		assertTrue(body.contains("&scope=orchestration%3Aread&"));
		assertFalse(body.contains("operate"));
		assertTrue(body.contains("subject_token=se+cret"));
		assertEquals(URI.create("https://h.ts.net/oauth/token"), Pairing.tokenEndpoint(URI.create("https://h.ts.net")));
	}

	@Test
	void acceptsOnlyExactlyReadScope() throws Exception {
		Pairing.Session s = Pairing.parseTokenResponse("""
				{"access_token":"tok","issued_token_type":"urn:ietf:params:oauth:token-type:access_token",
				 "token_type":"Bearer","expires_in":2592000,"scope":"orchestration:read"}""", 1000);
		assertEquals(Set.of("orchestration:read"), s.scopes());
		assertEquals(1000 + 2_592_000_000L, s.expiresAtMs());
		assertFalse(s.toString().contains("tok"));

		var more = assertThrows(Pairing.PairingException.class, () -> Pairing.parseTokenResponse(
				"{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":1,\"scope\":\"orchestration:read orchestration:operate\"}", 0));
		assertTrue(more.getMessage().contains("only accepts orchestration:read"));
		assertThrows(Pairing.PairingException.class, () -> Pairing.parseTokenResponse(
				"{\"access_token\":\"t\",\"token_type\":\"DPoP\",\"expires_in\":1,\"scope\":\"orchestration:read\"}", 0));
		assertThrows(Pairing.PairingException.class, () -> Pairing.parseTokenResponse("not json", 0));
	}

	@Test
	void describesErrors() {
		assertTrue(Pairing.describeTokenError(400, "{\"error\":\"invalid_scope\"}").contains("--scope orchestration:read"));
		assertTrue(Pairing.describeTokenError(401, "{}").contains("one-time"));
	}

	@Test
	void storesAndLoadsCredentials(@TempDir Path dir) throws Exception {
		Environments envs = new Environments(dir.resolve("t3craft"));
		assertTrue(envs.list().isEmpty());
		var env = new Environments.Environment("vps", "my-vps", URI.create("https://vps.ts.net"));
		envs.save(env, new Pairing.Session("secret-token", Set.of("orchestration:read"), 5000));
		assertEquals(1, envs.list().size());
		assertEquals("secret-token", envs.credential("vps").accessToken());
		assertFalse(Files.readString(envs.envFile()).contains("secret-token"), "environments.json holds no secrets");
		try {
			assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(envs.credentialFile("vps"))));
		} catch (UnsupportedOperationException ignored) {
			// non-POSIX filesystem
		}
		assertTrue(envs.remove("vps"));
		assertNull(envs.credential("vps"));
		assertTrue(envs.list().isEmpty());
	}

	@Test
	void ignoresStoredCredentialWithForeignScopes(@TempDir Path dir) throws Exception {
		Environments envs = new Environments(dir);
		Files.createDirectories(dir.resolve("credentials"));
		Files.writeString(dir.resolve("credentials/vps.json"),
				"{\"accessToken\":\"t\",\"scopes\":[\"orchestration:read\",\"orchestration:operate\",\"filesystem:write\"]}");
		assertNull(envs.credential("vps"));
		Files.writeString(dir.resolve("credentials/vps.json"), "{\"accessToken\":\"t\",\"scopes\":[\"orchestration:operate\"]}");
		assertNull(envs.credential("vps"));
		// the combined read + operate session of a default pairing is a valid read-zone credential
		Files.writeString(dir.resolve("credentials/vps.json"), "{\"accessToken\":\"t\",\"scopes\":[\"orchestration:read\",\"orchestration:operate\"]}");
		assertEquals(Pairing.OPERATE_SCOPES, envs.credential("vps").scopes());
	}

	@Test
	void slugs() {
		assertEquals("my-vps", Environments.slug("my-vps"));
		assertEquals("mini-acmesite", Environments.slug("  Mini Acmesite! "));
		assertEquals("env", Environments.slug("***"));
	}
}
