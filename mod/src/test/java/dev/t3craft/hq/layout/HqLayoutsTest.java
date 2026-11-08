package dev.t3craft.hq.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class HqLayoutsTest {
	@Test
	void orderAndIds() {
		assertEquals(List.of("classic", "campus", "ring", "vertical", "ensanche", "village", "hybrid-ringcampus"),
				HqLayouts.all().stream().map(HqLayoutInfo::id).toList());
		assertEquals("classic", HqLayouts.DEFAULT_ID);
		for (HqLayoutInfo i : HqLayouts.all()) {
			assertEquals("t3craft.hq.layout." + i.id() + ".name", i.nameKey());
			assertEquals("t3craft.hq.layout." + i.id() + ".desc", i.descKey());
			assertEquals(i.id().equals("campus"), i.large(), i.id());
		}
	}

	@Test
	void findAndResolve() {
		assertEquals("ring", HqLayouts.find(" Ring ").orElseThrow().id());
		assertTrue(HqLayouts.find("blocks").isEmpty());
		assertTrue(HqLayouts.find(null).isEmpty());
		assertEquals("classic", HqLayouts.resolve(null).id());
		assertEquals("classic", HqLayouts.resolve("nope").id());
		assertEquals("ensanche", HqLayouts.resolve("ensanche").id());
	}

	@Test
	void config() {
		assertEquals("classic", HqLayouts.fromConfig((JsonObject) null).id());
		assertEquals("classic", HqLayouts.fromConfig(JsonParser.parseString("{}").getAsJsonObject()).id());
		assertEquals("classic", HqLayouts.fromConfig(JsonParser.parseString("{\"hqLayout\":3}").getAsJsonObject()).id());
		assertEquals("classic", HqLayouts.fromConfig(JsonParser.parseString("{\"hqLayout\":\"x\"}").getAsJsonObject()).id());
		assertEquals("village", HqLayouts.fromConfig(JsonParser.parseString("{\"hqLayout\":\"village\"}").getAsJsonObject()).id());
	}

	@Test
	void langKeysInEveryLanguage() throws Exception {
		for (String lang : List.of("en_us", "es_es", "es_mx")) {
			try (var in = HqLayoutsTest.class.getResourceAsStream("/assets/t3craft/lang/" + lang + ".json")) {
				JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
				for (HqLayoutInfo i : HqLayouts.all()) {
					assertTrue(o.has(i.nameKey()), lang + " " + i.nameKey());
					assertFalse(o.get(i.descKey()).getAsString().contains("\n"), lang + " " + i.descKey());
				}
				if (lang.startsWith("es")) {
					assertEquals("Ensanche (manzanas)", o.get("t3craft.hq.layout.ensanche.name").getAsString());
				}
			}
		}
	}
}
