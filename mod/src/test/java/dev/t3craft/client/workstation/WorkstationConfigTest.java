package dev.t3craft.client.workstation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkstationConfigTest {
	@Test
	void defaultsOnMissingOrWrongTypes() {
		assertTrue(WorkstationConfig.parse(new JsonObject()).enabled());
		assertTrue(WorkstationConfig.parse(JsonParser.parseString("{\"workstations\": 3}").getAsJsonObject()).enabled());
		assertTrue(WorkstationConfig.parse(JsonParser.parseString("{\"workstations\": {\"enabled\": \"no\"}}").getAsJsonObject()).enabled());
		assertFalse(WorkstationConfig.parse(JsonParser.parseString("{\"workstations\": {\"enabled\": false}}").getAsJsonObject()).enabled());
	}

	@Test
	void saveMergesAndKeepsOtherKeys(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("t3craft.json");
		Files.writeString(f, "{\"schemaVersion\":1,\"mode\":\"direct\",\"workstations\":{\"enabled\":true,\"future\":7}}", StandardCharsets.UTF_8);
		new WorkstationConfig(false).save(f);
		JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
		assertEquals("direct", o.get("mode").getAsString());
		assertEquals(1, o.get("schemaVersion").getAsInt());
		assertFalse(o.getAsJsonObject("workstations").get("enabled").getAsBoolean());
		assertEquals(7, o.getAsJsonObject("workstations").get("future").getAsInt());
		assertFalse(WorkstationConfig.load(f).enabled());
	}

	@Test
	void missingFileLoadsDefaultsAndSaveCreatesIt(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("sub/t3craft.json");
		assertTrue(WorkstationConfig.load(f).enabled());
		new WorkstationConfig(false).save(f);
		assertFalse(WorkstationConfig.load(f).enabled());
	}

	@Test
	void neverOverwritesBrokenJson(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("t3craft.json");
		Files.writeString(f, "{ broken", StandardCharsets.UTF_8);
		assertTrue(WorkstationConfig.load(f).enabled());
		assertThrows(java.io.IOException.class, () -> new WorkstationConfig(false).save(f));
		assertEquals("{ broken", Files.readString(f));
	}
}
