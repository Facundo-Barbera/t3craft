package dev.t3craft.client.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {
	@TempDir
	Path dir;

	JsonObject read(Path p) throws Exception {
		return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	void missingFileWritesDefaults() throws Exception {
		Path f = dir.resolve("t3craft.json");
		ConfigStore.Loaded l = new ConfigStore(f).load();
		assertEquals(T3CraftSettings.defaults(), l.settings());
		assertEquals(1, read(f).get("schemaVersion").getAsInt());
	}

	@Test
	void migratesWithBackupAndSavesMergingUnknownKeys() throws Exception {
		Path f = dir.resolve("t3craft.json");
		Files.writeString(f, "{\"bridgeUrl\":\"ws://10.1.2.3:7878/\",\"bridgeKey\":\"k\",\"other\":42}");
		ConfigStore store = new ConfigStore(f);
		ConfigStore.Loaded l = store.load();
		assertEquals("bridge", l.settings().mode());
		assertTrue(Files.exists(dir.resolve("t3craft.json.v0.bak")));
		assertEquals(1, read(f).get("schemaVersion").getAsInt());
		assertTrue(store.save(l.settings().withVisibility(2, 3, false)));
		JsonObject o = read(f);
		assertEquals(42, o.get("other").getAsInt());
		assertEquals("k", o.get("bridgeKey").getAsString(), "bridge key preserved, not dropped");
		assertEquals(3, o.getAsJsonObject("visibility").get("maxPerEnvironment").getAsInt());
	}

	@Test
	void futureSchemaIsNeverOverwritten() throws Exception {
		Path f = dir.resolve("t3craft.json");
		String content = "{\"schemaVersion\":7,\"mode\":\"bridge\"}";
		Files.writeString(f, content);
		ConfigStore store = new ConfigStore(f);
		assertTrue(store.load().readOnly());
		assertFalse(store.save(T3CraftSettings.defaults()));
		assertEquals(content, Files.readString(f));
	}

	@Test
	void brokenFileIsKeptUntilSaveThenMovedAside() throws Exception {
		Path f = dir.resolve("t3craft.json");
		Files.writeString(f, "{ nope");
		ConfigStore store = new ConfigStore(f);
		ConfigStore.Loaded l = store.load();
		assertEquals(T3CraftSettings.defaults(), l.settings());
		assertFalse(l.warnings().isEmpty());
		assertEquals("{ nope", Files.readString(f));
		assertTrue(store.save(l.settings()));
		assertEquals("{ nope", Files.readString(dir.resolve("t3craft.json.broken.bak")));
		assertEquals(1, read(f).get("schemaVersion").getAsInt());
	}
}
