package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditLogTest {
	static AuditLog.Entry entry(String detail) {
		return new AuditLog.Entry(0, "vps", "t-1", "thread_stop", true, "ok", "ui", detail);
	}

	@Test
	void oneJsonObjectPerLine() {
		String line = AuditLog.line(entry("done"));
		assertFalse(line.contains("\n"));
		JsonObject o = JsonParser.parseString(line).getAsJsonObject();
		assertEquals("1970-01-01T00:00:00Z", o.get("time").getAsString());
		assertEquals("vps", o.get("env").getAsString());
		assertEquals("t-1", o.get("thread").getAsString());
		assertEquals("thread_stop", o.get("action").getAsString());
		assertTrue(o.get("destructive").getAsBoolean());
		assertEquals("ok", o.get("result").getAsString());
		assertEquals("ui", o.get("origin").getAsString());
		assertEquals("done", o.get("detail").getAsString());
		assertFalse(JsonParser.parseString(AuditLog.line(new AuditLog.Entry(0, "vps", null, "thread_create", false, "ok", "ui", ""))).getAsJsonObject().has("thread"));
	}

	@Test
	void sanitizesSecretsAndControlCharacters() {
		String line = AuditLog.line(entry("failed: Authorization: Bearer abc.def wss://h/ws?wsTicket=TICK&x=1 https://h/pair#token=PAIRCODE {\"access_token\":\"AT\"}\nINJECTED"));
		assertFalse(line.contains("abc.def"), line);
		assertFalse(line.contains("TICK"), line);
		assertFalse(line.contains("PAIRCODE"), line);
		assertFalse(line.contains("\"AT\""), line);
		assertFalse(line.contains("\\n"), line);
		assertTrue(line.contains("<redacted>"));
		assertEquals(1, line.split("\n").length);
		String longDetail = AuditLog.line(entry("x".repeat(5000)));
		assertTrue(longDetail.length() < 600);
	}

	@Test
	void appendsAndRotatesBySize(@TempDir Path dir) throws Exception {
		AuditLog log = new AuditLog(dir.resolve("audit.log"), 1024);
		for (int i = 0; i < 30; i++) {
			log.append(entry("n" + i));
		}
		assertTrue(Files.exists(log.file()));
		assertTrue(Files.exists(log.rotated()));
		assertTrue(Files.size(log.file()) <= 1024);
		for (String l : Files.readAllLines(log.file())) {
			JsonParser.parseString(l).getAsJsonObject();
		}
		assertTrue(Files.readString(log.file()).contains("\"n29\""));
	}

	@Test
	void appendNeverThrows(@TempDir Path dir) throws Exception {
		Path blocker = dir.resolve("file");
		Files.writeString(blocker, "x");
		new AuditLog(blocker.resolve("audit.log"), 1024).append(entry("x")); // parent is a file
	}
}
