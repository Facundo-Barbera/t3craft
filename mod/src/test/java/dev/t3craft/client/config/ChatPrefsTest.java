package dev.t3craft.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** feat/chat's additive {@code chat} section of t3craft.json. */
class ChatPrefsTest {
	@Test
	void absentSectionReadsAsDefaultWithoutWarnings() {
		JsonObject o = JsonParser.parseString("{\"schemaVersion\":1,\"mode\":\"direct\"}").getAsJsonObject();
		ConfigCodec.Decoded d = ConfigCodec.decode(ConfigMigrations.migrate(o).json());
		assertEquals(T3CraftSettings.ChatPrefs.DEFAULT, d.settings().chat());
		assertTrue(d.warnings().isEmpty());
	}

	@Test
	void roundTripKeepsOtherKeysAndDropsInvalidIds() {
		JsonObject o = JsonParser.parseString(
				"{\"schemaVersion\":1,\"chat\":{\"hiddenEnvironments\":[\"vps\",\"Bad Id!\",\"home\"],\"future\":true},\"other\":1}")
				.getAsJsonObject();
		ConfigCodec.Decoded d = ConfigCodec.decode(ConfigMigrations.migrate(o).json());
		assertEquals(Set.of("vps", "home"), d.settings().chat().hiddenEnvironments());
		assertEquals(1, d.warnings().size());
		T3CraftSettings changed = d.settings().withChat(d.settings().chat().withHiddenEnvironments(Set.of("pc")));
		JsonObject out = ConfigCodec.encode(changed, o);
		assertEquals("[\"pc\"]", out.getAsJsonObject("chat").get("hiddenEnvironments").toString());
		assertTrue(out.getAsJsonObject("chat").get("future").getAsBoolean(), "unknown chat keys survive");
		assertEquals(1, out.get("other").getAsInt());
		assertEquals(Set.of("pc"), ConfigCodec.decode(out).settings().chat().hiddenEnvironments());
	}

	@Test
	void otherWithersKeepChat() {
		T3CraftSettings s = T3CraftSettings.defaults().withChat(new T3CraftSettings.ChatPrefs(Set.of("a")));
		assertEquals(Set.of("a"), s.withVisibility(2, 3, true).withPortable(s.portable().withHeldScreen(false)).withNotifications(s.notifications()).withEnv("x", T3CraftSettings.EnvPrefs.DEFAULT)
				.withoutEnv("x").chat().hiddenEnvironments());
	}
}
