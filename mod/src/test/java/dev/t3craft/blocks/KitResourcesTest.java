package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.hq.HqKit;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Every kit block ships its data: lang keys in en_us, es_es and es_mx, blockstate, the models it
 * names, the textures those models name (all t3craft's own), block item and self-drop loot table.
 */
class KitResourcesTest {
	private static JsonObject json(String path) throws Exception {
		try (InputStream in = KitResourcesTest.class.getResourceAsStream(path)) {
			assertNotNull(in, path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static void exists(String path) throws Exception {
		try (InputStream in = KitResourcesTest.class.getResourceAsStream(path)) {
			assertNotNull(in, path);
		}
	}

	@Test
	void langKeysInEveryShippedLanguage() throws Exception {
		for (String lang : new String[] {"en_us", "es_es", "es_mx"}) {
			JsonObject o = json("/assets/t3craft/lang/" + lang + ".json");
			for (String id : KitBlocks.IDS) {
				String key = "block.t3craft." + id;
				assertTrue(o.has(key) && !o.get(key).getAsString().isBlank(), lang + " misses " + key);
			}
		}
		assertEquals("Escritorio de pie", json("/assets/t3craft/lang/es_es.json").get("block.t3craft.standing_desk").getAsString());
	}

	@Test
	void modelsTexturesItemsAndLoot() throws Exception {
		for (String id : KitBlocks.IDS) {
			JsonObject states = json("/assets/t3craft/blockstates/" + id + ".json");
			for (Map.Entry<String, JsonElement> v : states.getAsJsonObject("variants").entrySet()) {
				String model = v.getValue().getAsJsonObject().get("model").getAsString();
				assertTrue(model.startsWith("t3craft:block/"), model);
				JsonObject m = json("/assets/t3craft/models/block/" + model.substring("t3craft:block/".length()) + ".json");
				for (Map.Entry<String, JsonElement> t : m.getAsJsonObject("textures").entrySet()) {
					String tex = t.getValue().getAsString();
					assertTrue(tex.startsWith("t3craft:block/kit_"), id + " uses its own textures: " + tex);
					exists("/assets/t3craft/textures/block/" + tex.substring("t3craft:block/".length()) + ".png");
				}
			}
			JsonObject item = json("/assets/t3craft/items/" + id + ".json");
			assertTrue(item.getAsJsonObject("model").get("model").getAsString().startsWith("t3craft:block/"));
			JsonObject loot = json("/data/t3craft/loot_table/blocks/" + id + ".json");
			assertEquals("t3craft:" + id, loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
					.getAsJsonObject().get("name").getAsString(), "drops itself");
		}
	}

	@Test
	void everyRoleIsMapped() {
		for (HqKit.Role r : HqKit.Role.values()) {
			assertTrue(KitBlocks.IDS.contains(KitBlocks.ROLE_BLOCKS.get(r)), r.id());
		}
	}
}
