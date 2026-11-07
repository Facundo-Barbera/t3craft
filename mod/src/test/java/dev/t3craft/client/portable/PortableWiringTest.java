package dev.t3craft.client.portable;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.client.npc.NpcInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * What can be checked without starting Minecraft: the item's data/asset files agree with each other
 * (recipe → item id, model, texture, names), the key has a name, the adapter maps the existing NPC
 * model correctly, and the portable code cannot write to T3.
 */
class PortableWiringTest {
	static final Path RES = Path.of("src/main/resources");

	static JsonObject json(String rel) throws IOException {
		return JsonParser.parseString(Files.readString(RES.resolve(rel))).getAsJsonObject();
	}

	@Test
	void recipeIsCheapAndProducesTheTerminal() throws IOException {
		JsonObject r = json("data/t3craft/recipe/t3_terminal.json");
		assertEquals("minecraft:crafting_shapeless", r.get("type").getAsString());
		assertEquals("t3craft:t3_terminal", r.getAsJsonObject("result").get("id").getAsString());
		assertTrue(r.getAsJsonArray("ingredients").size() <= 3, "cheap: at most 3 common items");
		JsonObject adv = json("data/t3craft/advancement/recipes/tools/t3_terminal.json");
		assertEquals("t3craft:t3_terminal", adv.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
	}

	@Test
	void itemHasModelTextureAndNames() throws IOException {
		assertEquals("t3craft:item/t3_terminal", json("assets/t3craft/items/t3_terminal.json").getAsJsonObject("model").get("model").getAsString());
		assertEquals("t3craft:item/t3_terminal", json("assets/t3craft/models/item/t3_terminal.json").getAsJsonObject("textures").get("layer0").getAsString());
		assertTrue(Files.size(RES.resolve("assets/t3craft/textures/item/t3_terminal.png")) > 0);
		for (String lang : List.of("en_us", "es_es")) {
			JsonObject l = json("assets/t3craft/lang/" + lang + ".json");
			assertTrue(l.has("item.t3craft.t3_terminal"), lang);
			assertTrue(l.has(PortableClient.KEY_NAME), lang);
			assertTrue(l.has("key.category.t3craft.t3craft"), lang);
		}
	}

	@Test
	void adapterMapsTheExistingNpcModel() {
		NpcInfo n = new NpcInfo("vps:abc", "Fix login", "! Fix login", "my-vps", "my-vps · web", 0xFFAA00, "Approval needed: command",
				0, false, "needs_attention", "vps:p1", "web", "vps/needs_attention", "attention");
		PortableThread t = PortableAdapter.row(n);
		assertEquals("vps", t.env());
		assertEquals("abc", t.threadId());
		assertEquals("web", t.project());
		assertEquals("my-vps", t.envLabel());
		assertTrue(t.needsAttention());
		assertEquals("Approval needed: command", t.activity());

		PortableThread odd = PortableAdapter.row(new NpcInfo("legacy", "T", "T", "", "", 0, null, 9, false, "idle", "", "", "", null));
		assertNull(odd.threadId(), "no thread id → the Open button stays disabled; nothing is guessed");
	}

	@Test
	void portableCodeIsReadOnly() throws IOException {
		Pattern write = Pattern.compile("dispatchCommand|launchThread|orchestration:operate|message\\.dispatch|runtime-request\\.respond|chat\\.send|sendText");
		List<Path> files;
		try (Stream<Path> s = Stream.concat(
				Files.walk(Path.of("src/client/java/dev/t3craft/client/portable")),
				Files.walk(Path.of("src/main/java/dev/t3craft/portable")))) {
			files = s.filter(p -> p.toString().endsWith(".java")).toList();
		}
		assertFalse(files.isEmpty());
		for (Path p : files) {
			assertFalse(write.matcher(Files.readString(p)).find(), p + " must not reference write/operate APIs");
		}
	}
}
