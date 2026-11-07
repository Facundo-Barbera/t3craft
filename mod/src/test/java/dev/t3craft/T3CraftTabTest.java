package dev.t3craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The single T3Craft creative tab: per-module contributions and its title in every shipped language. */
class T3CraftTabTest {
	private static final List<String> BLOCKS = List.of("monitor", "status_lamp", "board_lamp");

	/** Mirrors the wiring: the terminal always contributes, blocks only when the module is on. */
	private static List<String> tab(boolean blocksOn) {
		TabContents<String> t = new TabContents<>();
		t.add(List.of("t3_terminal"));
		if (blocksOn) {
			t.add(BLOCKS);
		}
		return t.entries();
	}

	@Test
	void blocksOnAddsTheModuleItemsAfterTheTerminal() {
		assertEquals(List.of("t3_terminal", "monitor", "status_lamp", "board_lamp"), tab(true));
	}

	@Test
	void blocksOffLeavesOnlyTheTerminal() {
		assertEquals(List.of("t3_terminal"), tab(false));
	}

	@Test
	void lazyContributionsAreReadAtBuildTimeAndDeduplicated() {
		TabContents<String> t = new TabContents<>();
		List<String> late = new ArrayList<>();
		t.add(() -> late);
		t.add(List.of("a"));
		late.add("a");
		late.add("b");
		assertEquals(List.of("a", "b"), t.entries());
	}

	@Test
	void titleKeyExistsInEveryLanguage() throws Exception {
		assertEquals("itemGroup.t3craft", T3CraftTab.TITLE_KEY);
		for (String l : List.of("en_us", "es_es", "es_mx")) {
			try (var in = T3CraftTabTest.class.getResourceAsStream("/assets/t3craft/lang/" + l + ".json")) {
				assertNotNull(in, l);
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				assertTrue(o.has(T3CraftTab.TITLE_KEY), l + " misses " + T3CraftTab.TITLE_KEY);
			}
		}
	}

	@Test
	void esMxHasEveryEsEsKey() throws Exception {
		JsonObject es;
		JsonObject mx;
		try (var a = T3CraftTabTest.class.getResourceAsStream("/assets/t3craft/lang/es_es.json");
				var b = T3CraftTabTest.class.getResourceAsStream("/assets/t3craft/lang/es_mx.json")) {
			es = JsonParser.parseReader(new InputStreamReader(a, StandardCharsets.UTF_8)).getAsJsonObject();
			mx = JsonParser.parseReader(new InputStreamReader(b, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		assertEquals(es.keySet(), mx.keySet());
	}
}
