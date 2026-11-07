package dev.t3craft.survival;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.survival.SurvivalSettings.DebugGate;
import dev.t3craft.survival.SurvivalSettings.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurvivalConfigTest {
	static JsonObject obj(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@Test
	void absentSectionMeansDefaults() {
		SurvivalConfig.Result r = SurvivalConfig.parse(obj("{\"schema\":1,\"mode\":\"direct\"}"));
		assertEquals(SurvivalSettings.DEFAULTS, r.settings());
		assertTrue(r.warnings().isEmpty());
		assertEquals(SurvivalSettings.DEFAULTS, SurvivalConfig.parse(null).settings());
	}

	@Test
	void readsEveryKeyCaseInsensitively() {
		SurvivalConfig.Result r = SurvivalConfig.parse(obj("""
				{"survival": {"terminalForKey": "Survival", "builderMaterials": "never",
				              "debugBuilds": "ANY", "firstContactAdvancement": false}}"""));
		assertEquals(new SurvivalSettings(Requirement.SURVIVAL, Requirement.NEVER, DebugGate.ANY, false), r.settings());
		assertTrue(r.warnings().isEmpty());
	}

	@Test
	void invalidValuesWarnByKeyOnlyAndKeepOthers() {
		SurvivalConfig.Result r = SurvivalConfig.parse(obj("""
				{"survival": {"terminalForKey": "sometimes-secret-value", "builderMaterials": 3,
				              "debugBuilds": "any", "firstContactAdvancement": "yes"}}"""));
		assertEquals(Requirement.NEVER, r.settings().terminalForKey());
		assertEquals(Requirement.SURVIVAL, r.settings().builderMaterials());
		assertEquals(DebugGate.ANY, r.settings().debugBuilds());
		assertTrue(r.settings().firstContactAdvancement());
		assertEquals(List.of("survival.terminalForKey", "survival.builderMaterials", "survival.firstContactAdvancement"), r.warnings());
		assertFalse(String.join(" ", r.warnings()).contains("secret"));
	}

	@Test
	void nonObjectSectionWarns() {
		SurvivalConfig.Result r = SurvivalConfig.parse(obj("{\"survival\": true}"));
		assertEquals(SurvivalSettings.DEFAULTS, r.settings());
		assertEquals(List.of("survival"), r.warnings());
	}

	@Test
	void loadsFromFileAndToleratesMissingOrBroken(@TempDir Path dir) throws IOException {
		assertEquals(SurvivalSettings.DEFAULTS, SurvivalConfig.load(dir.resolve("absent.json")).settings());
		Path broken = Files.writeString(dir.resolve("broken.json"), "{not json");
		SurvivalConfig.Result b = SurvivalConfig.load(broken);
		assertEquals(SurvivalSettings.DEFAULTS, b.settings());
		assertEquals(1, b.warnings().size());
		Path ok = Files.writeString(dir.resolve("t3craft.json"), "{\"survival\":{\"terminalForKey\":\"always\"}}");
		assertEquals(Requirement.ALWAYS, SurvivalConfig.load(ok).settings().terminalForKey());
	}
}
