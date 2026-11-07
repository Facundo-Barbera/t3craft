package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OperateCredentialsTest {
	static final long NOW = 1_000_000;
	static final Pairing.Session READ = new Pairing.Session("read-token", Set.of(Pairing.READ_SCOPE), NOW + 60_000);
	static final Pairing.Session OP = new Pairing.Session("operate-token", Pairing.OPERATE_SCOPES, NOW + 60_000);

	@Test
	void storedApartFromReadCredentialAndConfig(@TempDir Path dir) throws Exception {
		Environments envs = new Environments(dir);
		envs.save(new Environments.Environment("vps", "my-vps", URI.create("https://vps.ts.net")), READ);
		OperateCredentials op = new OperateCredentials(dir);
		assertFalse(op.has("vps", NOW));
		op.save("vps", OP);

		Path f = dir.resolve("credentials/operate/vps.json");
		assertTrue(Files.exists(f));
		assertTrue(Files.readString(f).contains("operate-token"));
		assertFalse(Files.readString(dir.resolve("credentials/vps.json")).contains("operate-token"));
		assertFalse(Files.readString(dir.resolve("environments.json")).contains("token"));
		assertFalse(Files.exists(dir.resolve("t3craft.json")));

		assertEquals("read-token", envs.credential("vps").accessToken());
		assertEquals("operate-token", op.get("vps", NOW).accessToken());
		assertNull(op.get("other", NOW));
		try {
			assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(f)));
		} catch (UnsupportedOperationException ignored) {
			// non-POSIX
		}
	}

	@Test
	void refusesNonOperateSessions(@TempDir Path dir) {
		OperateCredentials op = new OperateCredentials(dir);
		assertThrows(IllegalArgumentException.class, () -> op.save("vps", READ));
		var wide = new Pairing.Session("x", Set.of(Pairing.READ_SCOPE, Pairing.OPERATE_SCOPE, "filesystem:write"), 0);
		assertThrows(IllegalArgumentException.class, () -> op.save("vps", wide));
		assertThrows(IllegalArgumentException.class, () -> op.save("../evil", OP));
	}

	@Test
	void ignoresTamperedFile(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("credentials/operate/vps.json");
		Files.createDirectories(f.getParent());
		Files.writeString(f, "{\"accessToken\":\"x\",\"scopes\":[\"orchestration:read\"]}");
		assertNull(new OperateCredentials(dir).get("vps", NOW));
		Files.writeString(f, "not json");
		assertNull(new OperateCredentials(dir).get("vps", NOW));
	}

	@Test
	void expiredIsNotUsableButVisible(@TempDir Path dir) throws Exception {
		OperateCredentials op = new OperateCredentials(dir);
		op.save("vps", new Pairing.Session("t", Pairing.OPERATE_SCOPES, NOW - 1));
		assertNull(op.get("vps", NOW));
		assertNotNull(op.stored("vps"));
	}

	@Test
	void revokeDeletesOnlyTheOperateToken(@TempDir Path dir) throws Exception {
		Environments envs = new Environments(dir);
		envs.save(new Environments.Environment("vps", "my-vps", URI.create("https://vps.ts.net")), READ);
		OperateCredentials op = new OperateCredentials(dir);
		op.save("vps", OP);
		assertTrue(op.revoke("vps"));
		assertFalse(op.revoke("vps"));
		assertNull(op.get("vps", NOW));
		assertNotNull(envs.credential("vps"), "read-only pairing survives revoking control");
		assertFalse(op.revoke("../x"));
	}

	@Test
	void unpairAlsoDeletesTheOperateToken(@TempDir Path dir) throws Exception {
		Environments envs = new Environments(dir);
		envs.save(new Environments.Environment("vps", "my-vps", URI.create("https://vps.ts.net")), READ);
		OperateCredentials op = new OperateCredentials(dir);
		op.save("vps", OP);
		assertTrue(envs.remove("vps"));
		assertFalse(Files.exists(dir.resolve("credentials/operate/vps.json")));
	}
}
