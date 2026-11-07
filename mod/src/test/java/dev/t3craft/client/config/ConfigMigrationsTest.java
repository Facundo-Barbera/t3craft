package dev.t3craft.client.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ConfigMigrationsTest {
	static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void v0ModConfigDefaultsBecomeV1AndKeepEveryKey() {
		JsonObject v0 = json("""
				{"mode":"direct","visibility":{"recentWindowHours":24,"maxPerEnvironment":40},"includeSubagents":false,
				 "bridgeUrl":"ws://127.0.0.1:7878/","bridgeKey":null,"someoneElse":{"x":1}}""");
		ConfigMigrations.Migrated m = ConfigMigrations.migrate(v0);
		assertEquals(0, m.fromVersion());
		assertTrue(m.changed());
		assertEquals(1, m.json().get("schemaVersion").getAsInt());
		assertEquals("direct", m.json().get("mode").getAsString());
		assertTrue(m.json().has("notifications"));
		assertTrue(m.json().has("environments"));
		assertEquals(1, m.json().getAsJsonObject("someoneElse").get("x").getAsInt());
		assertFalse(v0.has("schemaVersion"), "original untouched");
	}

	@Test
	void legacyBridgeInstallWithoutModeStaysBridge() {
		assertEquals("bridge", ConfigMigrations.migrate(json("{\"bridgeUrl\":\"ws://10.0.0.1:7878/\"}")).json().get("mode").getAsString());
		assertEquals("direct", ConfigMigrations.migrate(json("{}")).json().get("mode").getAsString());
	}

	@Test
	void nestedIncludeSubagentsMovesToTopLevel() {
		JsonObject o = ConfigMigrations.migrate(json("{\"visibility\":{\"includeSubagents\":true}}")).json();
		assertTrue(o.get("includeSubagents").getAsBoolean());
		assertFalse(o.getAsJsonObject("visibility").has("includeSubagents"));
	}

	@Test
	void currentIsUnchangedAndFutureIsFlagged() {
		assertFalse(ConfigMigrations.migrate(json("{\"schemaVersion\":1}")).changed());
		ConfigMigrations.Migrated f = ConfigMigrations.migrate(json("{\"schemaVersion\":99,\"mode\":\"bridge\"}"));
		assertTrue(f.fromFuture());
		assertEquals(99, f.json().get("schemaVersion").getAsInt());
	}
}
