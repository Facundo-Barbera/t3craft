package dev.t3craft.survival;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The data files for survival agree with each other and with the code: the T3 Terminal recipe only
 * uses early-game items, its recipe-book unlock fires on any of them, "First contact" has the
 * criterion the code awards, and every policy reason has a translation.
 */
class SurvivalDataTest {
	static final Path RES = Path.of("src/main/resources");

	/**
	 * How far into a survival run an item is reachable. 0 = punch a tree, 1 = stone tools + furnace,
	 * 2 = iron pickaxe (redstone ore). Anything above 2 is not early game.
	 */
	static final Map<String, Integer> TIER = Map.ofEntries(
			Map.entry("minecraft:stick", 0), Map.entry("minecraft:oak_planks", 0), Map.entry("#minecraft:planks", 0),
			Map.entry("minecraft:cobblestone", 1), Map.entry("minecraft:glass", 1), Map.entry("minecraft:glass_pane", 1),
			Map.entry("minecraft:copper_ingot", 1), Map.entry("minecraft:coal", 1), Map.entry("minecraft:torch", 1),
			Map.entry("minecraft:iron_ingot", 2), Map.entry("minecraft:redstone", 2), Map.entry("minecraft:lapis_lazuli", 2),
			Map.entry("minecraft:gold_ingot", 3), Map.entry("minecraft:diamond", 4), Map.entry("minecraft:amethyst_shard", 3),
			Map.entry("minecraft:ender_pearl", 4), Map.entry("minecraft:netherite_ingot", 6));
	static final int EARLY_GAME = 2;

	static JsonObject json(String rel) throws IOException {
		return JsonParser.parseString(Files.readString(RES.resolve(rel))).getAsJsonObject();
	}

	/** Ingredient ids of a shaped or shapeless crafting recipe (string or list-of-strings ingredients). */
	static List<String> ingredients(JsonObject recipe) {
		List<String> out = new java.util.ArrayList<>();
		String type = recipe.get("type").getAsString();
		Iterable<JsonElement> raw = switch (type) {
			case "minecraft:crafting_shapeless" -> recipe.getAsJsonArray("ingredients");
			case "minecraft:crafting_shaped" -> recipe.getAsJsonObject("key").entrySet().stream().map(Map.Entry::getValue).toList();
			default -> throw new AssertionError("unexpected recipe type " + type);
		};
		for (JsonElement e : raw) {
			if (e.isJsonArray()) {
				e.getAsJsonArray().forEach(x -> out.add(x.getAsString()));
			} else {
				out.add(e.getAsString());
			}
		}
		return out;
	}

	@Test
	void terminalRecipeIsEarlyGame() throws IOException {
		JsonObject r = json("data/t3craft/recipe/t3_terminal.json");
		assertEquals("t3craft:t3_terminal", r.getAsJsonObject("result").get("id").getAsString());
		List<String> ing = ingredients(r);
		assertFalse(ing.isEmpty());
		for (String id : ing) {
			Integer tier = TIER.get(id);
			assertNotNull(tier, id + ": unknown ingredient, add it to TIER with its progression tier");
			assertTrue(tier <= EARLY_GAME, id + " is not early game (tier " + tier + ")");
		}
		assertTrue(ing.size() <= 9);
	}

	@Test
	void recipeUnlocksWithAnyIngredient() throws IOException {
		List<String> ing = ingredients(json("data/t3craft/recipe/t3_terminal.json"));
		JsonObject adv = json("data/t3craft/advancement/recipes/tools/t3_terminal.json");
		Set<String> unlockItems = new HashSet<>();
		JsonObject criteria = adv.getAsJsonObject("criteria");
		for (Map.Entry<String, JsonElement> c : criteria.entrySet()) {
			JsonObject o = c.getValue().getAsJsonObject();
			if ("minecraft:inventory_changed".equals(o.get("trigger").getAsString())) {
				for (JsonElement p : o.getAsJsonObject("conditions").getAsJsonArray("items")) {
					unlockItems.add(p.getAsJsonObject().get("items").getAsString());
				}
			}
		}
		assertTrue(unlockItems.containsAll(ing), "picking up any ingredient shows the recipe: " + unlockItems);
		JsonArray req = adv.getAsJsonArray("requirements");
		assertEquals(1, req.size(), "one OR-group: any criterion unlocks");
		Set<String> group = new HashSet<>();
		req.get(0).getAsJsonArray().forEach(e -> group.add(e.getAsString()));
		assertEquals(criteria.keySet(), group);
		assertEquals("t3craft:t3_terminal", adv.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
	}

	@Test
	void firstContactIsOnlyGrantedByCode() throws IOException {
		JsonObject adv = json("data/t3craft/advancement/first_contact.json");
		JsonObject criteria = adv.getAsJsonObject("criteria");
		assertEquals(Set.of(FirstContact.CRITERION), criteria.keySet());
		assertEquals("minecraft:impossible", criteria.getAsJsonObject(FirstContact.CRITERION).get("trigger").getAsString());
		JsonObject display = adv.getAsJsonObject("display");
		assertEquals("t3craft:t3_terminal", display.getAsJsonObject("icon").get("id").getAsString());
		for (String lang : List.of("en_us", "es_es")) {
			JsonObject l = json("assets/t3craft/lang/" + lang + ".json");
			assertTrue(l.has(display.getAsJsonObject("title").get("translate").getAsString()), lang);
			assertTrue(l.has(display.getAsJsonObject("description").get("translate").getAsString()), lang);
		}
	}

	@Test
	void everyPolicyReasonIsTranslated() throws IOException {
		List<String> reasons = List.of("ok", "needs_terminal", "needs_singleplayer", "spectator", "adventure", "needs_cheats");
		for (String lang : List.of("en_us", "es_es")) {
			JsonObject l = json("assets/t3craft/lang/" + lang + ".json");
			for (String reason : reasons) {
				assertTrue(l.has(new Verdict(false, reason).langKey()), lang + ": " + reason);
			}
		}
	}
}
