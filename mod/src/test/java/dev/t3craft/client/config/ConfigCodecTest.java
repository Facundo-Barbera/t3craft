package dev.t3craft.client.config;

import static dev.t3craft.client.config.ConfigMigrationsTest.json;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.t3craft.client.config.T3CraftSettings.EnvPrefs;
import org.junit.jupiter.api.Test;

class ConfigCodecTest {
	static ConfigCodec.Decoded decode(String s) {
		return ConfigCodec.decode(ConfigMigrations.migrate(json(s)).json());
	}

	@Test
	void defaultsRoundTrip() {
		T3CraftSettings d = T3CraftSettings.defaults();
		ConfigCodec.Decoded back = ConfigCodec.decode(ConfigCodec.encode(d, new JsonObject()));
		assertEquals(d, back.settings());
		assertTrue(back.warnings().isEmpty(), back.warnings().toString());
	}

	@Test
	void fullRoundTripWithEnvironments() {
		T3CraftSettings s = T3CraftSettings.defaults()
				.withVisibility(12.5, 10, true)
				.withNotifications(new T3CraftSettings.Notifications(true, false, true, true, false, true, true, false))
				.withEnv("my-vps", new EnvPrefs("VPS", 0x55FFFF, false, true, false, true, 6.0, 5));
		assertEquals(s, ConfigCodec.decode(ConfigCodec.encode(s, new JsonObject())).settings());
	}

	@Test
	void encodeKeepsModConfigKeysWhereModConfigReadsThem() {
		JsonObject o = ConfigCodec.encode(T3CraftSettings.defaults().withVisibility(48, 7, true), new JsonObject());
		assertEquals(48, o.getAsJsonObject("visibility").get("recentWindowHours").getAsDouble());
		assertEquals(7, o.getAsJsonObject("visibility").get("maxPerEnvironment").getAsInt());
		assertTrue(o.get("includeSubagents").getAsBoolean());
		assertEquals("direct", o.get("mode").getAsString());
		assertTrue(o.get("bridgeKey").isJsonNull());
	}

	@Test
	void encodePreservesUnknownKeys() {
		JsonObject base = json("{\"hq\":{\"x\":3},\"visibility\":{\"future\":true},\"notifications\":{\"volume\":0.5}}");
		JsonObject o = ConfigCodec.encode(T3CraftSettings.defaults(), base);
		assertEquals(3, o.getAsJsonObject("hq").get("x").getAsInt());
		assertTrue(o.getAsJsonObject("visibility").get("future").getAsBoolean());
		assertEquals(0.5, o.getAsJsonObject("notifications").get("volume").getAsDouble());
	}

	@Test
	void invalidValuesFallBackWithWarningsThatNeverContainValues() {
		ConfigCodec.Decoded d = decode("""
				{"mode":"sideways","bridgeUrl":"http://user:hunter2@host/","bridgeKey":"s3cret-key",
				 "visibility":{"recentWindowHours":-5,"maxPerEnvironment":5000},"includeSubagents":"yes",
				 "notifications":{"chat":"nope"},
				 "environments":{"BAD ID":{},"ok-env":{"color":"red","filter":{"maxPerEnvironment":0}}}}""");
		T3CraftSettings s = d.settings();
		assertEquals("direct", s.mode());
		assertEquals(T3CraftSettings.DEFAULT_BRIDGE_URL, s.bridgeUrl());
		assertEquals(24, s.recentWindowHours());
		assertEquals(40, s.maxPerEnvironment());
		assertFalse(s.includeSubagents());
		assertTrue(s.notifications().chat());
		assertNull(s.env("ok-env").color());
		assertNull(s.env("ok-env").max());
		assertFalse(s.environments().containsKey("BAD ID"));
		assertTrue(d.warnings().size() >= 8, d.warnings().toString());
		for (String w : d.warnings()) {
			assertFalse(w.contains("hunter2") || w.contains("s3cret") || w.contains("sideways"), w);
		}
		assertEquals("s3cret-key", s.bridgeKey());
		assertFalse(s.toString().contains("s3cret"), "toString redacts the bridge key");
	}

	@Test
	void colours() {
		assertEquals(0x55FFFF, ConfigCodec.parseColor("#55ffff"));
		assertEquals(0x00FF00, ConfigCodec.parseColor("00FF00"));
		assertNull(ConfigCodec.parseColor("#12345"));
		assertEquals("#0000FF", ConfigCodec.formatColor(0xFF));
		assertEquals(0x123456, decode("{\"environments\":{\"e\":{\"color\":1193046}}}").settings().env("e").color());
	}

	@Test
	void labelsAreSanitized() {
		assertNull(ConfigCodec.sanitizeLabel("   "));
		assertEquals("a b", ConfigCodec.sanitizeLabel(" a\u0007 b "));
		assertEquals(32, ConfigCodec.sanitizeLabel("x".repeat(50)).length());
	}

	@Test
	void defaultEnvPrefsAreNotStored() {
		T3CraftSettings s = T3CraftSettings.defaults().withEnv("e", EnvPrefs.DEFAULT);
		assertTrue(s.environments().isEmpty());
		assertTrue(decode("{\"environments\":{\"e\":{\"visible\":true}}}").settings().environments().isEmpty());
	}
}
