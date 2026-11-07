package dev.t3craft.client.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** The additive {@code portable.heldScreen} key (portable T3 Terminal's live held screen). */
class PortableConfigKeyTest {
	@Test
	void defaultIsOnAndAFileWithoutTheSectionReadsAsDefault() {
		assertTrue(T3CraftSettings.defaults().portable().heldScreen());
		ConfigCodec.Decoded d = ConfigCodecTest.decode("{\"schemaVersion\":1,\"mode\":\"direct\"}");
		assertTrue(d.settings().portable().heldScreen());
		assertTrue(d.warnings().isEmpty(), d.warnings().toString());
	}

	@Test
	void offRoundTripsAndKeepsOtherPortableKeys() {
		JsonObject base = JsonParser.parseString("{\"portable\":{\"future\":7}}").getAsJsonObject();
		T3CraftSettings off = T3CraftSettings.defaults().withPortable(new T3CraftSettings.Portable(false));
		JsonObject o = ConfigCodec.encode(off, base);
		assertFalse(o.getAsJsonObject("portable").get("heldScreen").getAsBoolean());
		assertEquals(7, o.getAsJsonObject("portable").get("future").getAsInt(), "unknown keys survive a save");
		assertEquals(off, ConfigCodec.decode(o).settings());
	}

	@Test
	void invalidValueFallsBackToDefaultWithWarning() {
		ConfigCodec.Decoded d = ConfigCodecTest.decode("{\"schemaVersion\":1,\"portable\":{\"heldScreen\":\"no\"}}");
		assertTrue(d.settings().portable().heldScreen());
		assertFalse(d.warnings().isEmpty());
		ConfigCodec.Decoded bad = ConfigCodecTest.decode("{\"schemaVersion\":1,\"portable\":3}");
		assertTrue(bad.settings().portable().heldScreen());
		assertFalse(bad.warnings().isEmpty());
	}

	@Test
	void withersKeepThePortableSection() {
		T3CraftSettings off = T3CraftSettings.defaults().withPortable(new T3CraftSettings.Portable(false));
		assertFalse(off.withVisibility(3, 4, true).portable().heldScreen());
		assertFalse(off.withNotifications(T3CraftSettings.Notifications.defaults()).portable().heldScreen());
		assertFalse(off.withEnv("vps", T3CraftSettings.EnvPrefs.DEFAULT.withVisible(false)).portable().heldScreen());
		assertFalse(off.withoutEnv("vps").portable().heldScreen());
	}
}
